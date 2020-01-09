/*
 * Copyright 2013 Proofpoint Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.kairosdb.core.datastore;


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.kairosdb.core.DataPoint;
import org.kairosdb.core.DataPointListener;
import org.kairosdb.core.DataPointSet;
import org.kairosdb.core.KairosDataPointFactory;
import org.kairosdb.core.aggregator.Aggregator;
import org.kairosdb.core.aggregator.LimitAggregator;
import org.kairosdb.core.datapoints.LongDataPointFactory;
import org.kairosdb.core.datapoints.LongDataPointFactoryImpl;
import org.kairosdb.core.exception.DatastoreException;
import org.kairosdb.core.groupby.*;
import org.kairosdb.core.http.rest.metrics.CacheFilesMetricsProvider;
import org.kairosdb.core.reporting.KairosMetricReporter;
import org.kairosdb.datastore.cassandra.MaxRowKeysForQueryExceededException;
import org.kairosdb.util.MemoryMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class KairosDatastore implements KairosMetricReporter {
    private static final Logger logger = LoggerFactory.getLogger(KairosDatastore.class);
    private static final String QUERY_CACHE_DIR = "kairosdb.query_cache.cache_dir";

    private static final String READ_CACHE_HIT = "kairosdb.datastore.read.cache_hit";
    private static final String READ_CACHE_MISS = "kairosdb.datastore.read.cache_miss";

    private final Datastore m_datastore;
    private final QueryQueuingManager m_queuingManager;
    private final List<DataPointListener> m_dataPointListeners;
    private final KairosDataPointFactory m_dataPointFactory;

    private String m_baseCacheDir;
    private volatile String m_cacheDir;

    private final AtomicInteger m_readCacheHit = new AtomicInteger();
    private final AtomicInteger m_readCacheMiss = new AtomicInteger();

    @Inject
    private LongDataPointFactory m_longDataPointFactory = new LongDataPointFactoryImpl();

    @Inject
    private CacheFilesMetricsProvider cacheFilesMetricsProvider;

    @Inject
    @Named("HOSTNAME")
    private String hostName = "localhost";

    private Tracer tracer;

    @Inject
    public KairosDatastore(Datastore datastore, QueryQueuingManager queuingManager,
                           List<DataPointListener> dataPointListeners, KairosDataPointFactory dataPointFactory, Tracer tracer) {
        m_datastore = checkNotNull(datastore);
        m_dataPointListeners = checkNotNull(dataPointListeners);
        m_queuingManager = checkNotNull(queuingManager);
        m_dataPointFactory = dataPointFactory;

        m_baseCacheDir = System.getProperty("java.io.tmpdir") + "/kairos_cache/";

        setupCacheDirectory();

        this.tracer = tracer;
    }

    @Inject(optional = true)
    public void setBaseCacheDir(@Named(QUERY_CACHE_DIR) String cacheTempDir) {
        if (cacheTempDir != null && !cacheTempDir.equals("")) {
            m_baseCacheDir = cacheTempDir;
            setupCacheDirectory();
        }
    }

    private void setupCacheDirectory() {
        cleanDirectory(new File(m_baseCacheDir));
        newCacheDirectory();
        File cacheDirectory = new File(m_cacheDir);
        if (!cacheDirectory.mkdirs()) {
            logger.error(String.format("Could not create directory %s", m_cacheDir));
        }
        checkState(cacheDirectory.exists(), "Cache directory not created");
    }

    /**
     * Make sure the folder exists
     */
    private static void ensureFolder(String path) {
        File fPath = new File(path);
        if (!fPath.exists()) {
            if (!fPath.mkdirs()) {
                logger.error(String.format("Could not create directory %s", path));
            }
        }
    }

    public String getCacheDir() {
        ensureFolder(m_cacheDir);
        return m_cacheDir;
    }

    private void newCacheDirectory() {
        String newCacheDir = m_baseCacheDir + "/" + System.currentTimeMillis() + "/";
        ensureFolder(newCacheDir);
        m_cacheDir = newCacheDir;
    }

    private void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            logger.warn("There is no such path '{}'", directory.toAbsolutePath());
            return;
        }
        Files.list(directory).forEach(entry -> {
            try {
                if (Files.isDirectory(entry)) {
                    cleanDirectory(entry);
                } else {
                    Files.delete(entry);
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });

        Files.delete(directory);
    }

    private void cleanDirectory(File directory) {
        if (!directory.exists()) {
            logger.warn(String.format("There is no such path '%s'", directory.getAbsolutePath()));
            return;
        }
        File[] list = directory.listFiles();

        if (list != null && list.length > 0) {
            for (File aList : list) {
                if (aList.isDirectory())
                    cleanDirectory(aList);

                if (!aList.delete()) {
                    logger.error(String.format("Unable to delete file '%s'", aList.getAbsolutePath()));
                }
            }
        }

        if (!directory.delete()) {
            logger.error(String.format("Unable to delete directory '%s'", directory.getAbsolutePath()));
        }
    }

    public void cleanCacheDir(boolean wait) throws IOException {
        String oldCacheDir = m_cacheDir;
        newCacheDirectory();

        if (wait) {
            try {
                logger.warn("Sleep for 10 minutes, to make sure that all read requests using old cache dir are finished");
                Thread.sleep(10 * 60 * 1000);
            } catch (InterruptedException e) {
                logger.error("Sleep interrupted:", e);
            }
        }

        logger.debug("Executing job...");
        File dir = new File(oldCacheDir);
        logger.warn("Deleting cache files in " + dir.getAbsolutePath());

        cleanDirectory(dir.toPath());
    }

    /**
     * Close the datastore
     */
    public void close() throws InterruptedException, DatastoreException {
        m_datastore.close();
    }

    public void putDataPoint(String metricName,
                             ImmutableSortedMap<String, String> tags,
                             DataPoint dataPoint) throws DatastoreException {
        putDataPoint(metricName, tags, dataPoint, 0);
    }

    public void putDataPoint(String metricName,
                             ImmutableSortedMap<String, String> tags,
                             DataPoint dataPoint, int ttl) throws DatastoreException {
        //Add to datastore first.
        m_datastore.putDataPoint(metricName, tags, dataPoint, ttl);

        for (DataPointListener dataPointListener : m_dataPointListeners) {
            dataPointListener.dataPoint(metricName, tags, dataPoint);
        }
    }


    public Iterable<String> getMetricNames() throws DatastoreException {
        return m_datastore.getMetricNames();
    }

    public Iterable<String> getTagNames() throws DatastoreException {
        return m_datastore.getTagNames();
    }

    /**
     * Exports the data for a metric query without doing any aggregation or sorting
     *
     * @param metric metric
     */
    public void export(QueryMetric metric, QueryCallback callback) throws DatastoreException {
        checkNotNull(metric);

        m_datastore.queryDatabase(metric, callback);
    }


    public List<DataPointGroup> queryTags(QueryMetric metric) throws DatastoreException {
        TagSet tagSet = m_datastore.queryMetricTags(metric);

        return Collections.singletonList(new EmptyDataPointGroup(metric.getName(), tagSet));
    }

    public DatastoreQuery createQuery(QueryMetric metric) throws DatastoreException {
        checkNotNull(metric);

        DatastoreQuery dq;
        Span span = tracer.activeSpan();

        try {
            dq = new DatastoreQueryImpl(metric);

            if (span != null) {
                span.setTag("query_waiting_count", m_queuingManager.getQueryWaitingCount());
                span.setTag("available_threads", m_queuingManager.getAvailableThreads());
            }
        } catch (NoSuchAlgorithmException | InterruptedException e) {
            throw new DatastoreException(e);
        }

        return dq;
    }


    public void delete(QueryMetric metric) throws DatastoreException {
        checkNotNull(metric);

        try {
            m_datastore.deleteDataPoints(metric);
        } catch (Exception e) {
            throw new DatastoreException(e);
        }
    }

    private static List<GroupBy> removeTagGroupBy(List<GroupBy> groupBys) {
        List<GroupBy> modifiedGroupBys = new ArrayList<>();
        for (GroupBy groupBy : groupBys) {
            if (!(groupBy instanceof TagGroupBy))
                modifiedGroupBys.add(groupBy);
        }
        return modifiedGroupBys;
    }

    private static TagGroupBy getTagGroupBy(List<GroupBy> groupBys) {
        for (GroupBy groupBy : groupBys) {
            if (groupBy instanceof TagGroupBy)
                return (TagGroupBy) groupBy;
        }
        return null;
    }

    List<DataPointGroup> groupByTypeAndTag(String metricName,
                                           List<DataPointRow> rows, TagGroupBy tagGroupBy, Order order) {
        List<DataPointGroup> ret = new ArrayList<>();
        MemoryMonitor mm = new MemoryMonitor(20);

        if (rows.isEmpty()) {
            ret.add(new SortingDataPointGroup(metricName, order));
        } else {
            ListMultimap<String, DataPointGroup> typeGroups = ArrayListMultimap.create();

            //Go through each row grouping them by type
            for (DataPointRow row : rows) {
                String groupType = m_dataPointFactory.getGroupType(row.getDatastoreType());

                typeGroups.put(groupType, new DataPointGroupRowWrapper(row));
                mm.checkMemoryAndThrowException();
            }

            //Sort the types for predictable results
            TreeSet<String> sortedTypes = new TreeSet<>(typeGroups.keySet());

            //Now go through each type group and group by tag if needed.
            for (String type : sortedTypes) {
                if (tagGroupBy != null) {
                    ListMultimap<String, DataPointGroup> groups = ArrayListMultimap.create();
                    Map<String, TagGroupByResult> groupByResults = new HashMap<>();

                    for (DataPointGroup dataPointGroup : typeGroups.get(type)) {
                        // FIXME Add code to datastore implementations to filter by the group by tag

                        LinkedHashMap<String, String> matchingTags = getMatchingTags(dataPointGroup, tagGroupBy.getTagNames());
                        String tagsKey = getTagsKey(matchingTags);
                        groups.put(tagsKey, dataPointGroup);
                        groupByResults.put(tagsKey, new TagGroupByResult(tagGroupBy, matchingTags));
                        mm.checkMemoryAndThrowException();
                    }

                    //Sort groups by tags
                    TreeSet<String> sortedGroups = new TreeSet<>(groups.keySet());

                    for (String key : sortedGroups) {
                        SortingDataPointGroup sdpGroup = new SortingDataPointGroup(groups.get(key), groupByResults.get(key), order);
                        sdpGroup.addGroupByResult(new TypeGroupByResult(type));
                        ret.add(sdpGroup);
                    }
                } else {
                    ret.add(new SortingDataPointGroup(typeGroups.get(type), new TypeGroupByResult(type), order));
                }
            }
        }

        return ret;
    }


    /**
     * Create a unique identifier for this combination of tags to be used as the key of a hash map.
     */
    private static String getTagsKey(LinkedHashMap<String, String> tags) {
        StringBuilder builder = new StringBuilder();
        for (String name : tags.keySet()) {
            builder.append(name).append(tags.get(name));
        }

        return builder.toString();
    }

    /**
     * Tags are inserted in the order specified in tagNames which is the order
     * from the query.  We use a linked hashmap so that order is preserved and
     * the group by responses are sorted in the order specified in the query.
     */
    private static LinkedHashMap<String, String> getMatchingTags(DataPointGroup datapointGroup, List<String> tagNames) {
        LinkedHashMap<String, String> matchingTags = new LinkedHashMap<>();
        for (String tagName : tagNames) {
            Set<String> tagValues = datapointGroup.getTagValues(tagName);
            if (tagValues != null) {
                String tagValue = tagValues.iterator().next();
                matchingTags.put(tagName, tagValue != null ? tagValue : "");
            }
        }

        return matchingTags;
    }


    private static String calculateFilenameHash(QueryMetric metric) throws NoSuchAlgorithmException {
        String hashString = metric.getCacheString();
        if (hashString == null)
            hashString = String.valueOf(System.currentTimeMillis());

        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        byte[] digest = messageDigest.digest(hashString.getBytes(StandardCharsets.UTF_8));

        return new BigInteger(1, digest).toString(16);
    }

    @Override
    public List<DataPointSet> getMetrics(long now) {
        DataPointSet dpsHit = new DataPointSet(READ_CACHE_HIT);
        DataPointSet dpsMiss = new DataPointSet(READ_CACHE_MISS);

        int hits = m_readCacheHit.getAndSet(0);
        int misses = m_readCacheMiss.getAndSet(0);

        dpsHit.addDataPoint(m_longDataPointFactory.createDataPoint(now, hits));
        dpsMiss.addDataPoint(m_longDataPointFactory.createDataPoint(now, misses));

        List<DataPointSet> ret = Arrays.asList(dpsHit, dpsMiss);

        ret.forEach(dps -> dps.addTag("host", hostName));

        return ret;
    }


    private class DatastoreQueryImpl implements DatastoreQuery {
        private String m_cacheFilename;
        private QueryMetric m_metric;
        private List<DataPointGroup> m_results;
        private int m_dataPointCount;
        private int m_rowCount;

        DatastoreQueryImpl(QueryMetric metric) throws NoSuchAlgorithmException, InterruptedException {
            m_metric = metric;
            m_cacheFilename = calculateFilenameHash(metric);
            m_queuingManager.waitForTimeToRun(m_cacheFilename);
        }

        public int getSampleSize() {
            return m_dataPointCount;
        }

        @Override
        public List<DataPointGroup> execute() throws DatastoreException {
            Span span = tracer.buildSpan("query_database_datapoints_count").start();

            try (Scope scope = tracer.scopeManager().activate(span)) {
                CachedSearchResult cachedResults = null;

                List<DataPointRow> returnedRows = null;

                try {
                    String tempFile = m_cacheDir + m_cacheFilename;

                    if (m_metric.getCacheTime() > 0) {
                        cachedResults = CachedSearchResult.openCachedSearchResult(m_metric.getName(),
                                tempFile, m_metric.getCacheTime(), m_dataPointFactory);
                        if (cachedResults != null) {
                            returnedRows = cachedResults.getRows();
                            cachedResults.cacheCreatedAt().ifPresent(fileCreateAt -> {
                                logger.warn(
                                        "Cache file was created {} seconds ago",
                                        (System.currentTimeMillis() - fileCreateAt) / 1000
                                );
                                cacheFilesMetricsProvider.measureSpan(fileCreateAt);
                            });
                            span.setTag("cached", true);
                            m_readCacheHit.incrementAndGet();
                        }
                    }

                    if (cachedResults == null) {
                        cachedResults = CachedSearchResult.createCachedSearchResult(m_metric.getName(),
                                tempFile, m_dataPointFactory);
                        m_datastore.queryDatabase(m_metric, cachedResults);
                        returnedRows = cachedResults.getRows();
                        span.setTag("cached", false);
                        m_readCacheMiss.incrementAndGet();
                    }
                } catch (MaxRowKeysForQueryExceededException e) {
                    if (cachedResults != null) {
                        cachedResults.decrementClose();
                    }
                    throw e;
                } catch (Exception e) {
                    if (cachedResults != null) {
                        cachedResults.decrementClose();
                    }
                    throw new DatastoreException(e);
                }

                //Get data point count
                for (DataPointRow returnedRow : returnedRows) {
                    m_dataPointCount += returnedRow.getDataPointCount();
                }

                m_rowCount = returnedRows.size();

                span.setTag("datapoint_count", m_dataPointCount);
                span.setTag("row_count", m_rowCount);

                logQuery();

                List<DataPointGroup> queryResults = groupByTypeAndTag(m_metric.getName(),
                        returnedRows, getTagGroupBy(m_metric.getGroupBys()), m_metric.getOrder());

                // Now group for all other types of group bys.
                Grouper grouper = new Grouper(m_dataPointFactory);
                try {
                    queryResults = grouper.group(removeTagGroupBy(m_metric.getGroupBys()), queryResults);
                } catch (IOException e) {
                    throw new DatastoreException(e);
                }

                m_results = new ArrayList<>();
                for (DataPointGroup queryResult : queryResults) {
                    String groupType = DataPoint.GROUP_NUMBER;
                    // FIXME May want to make group type a first class citizen in DataPointGroup
                    for (GroupByResult groupByResult : queryResult.getGroupByResult()) {
                        if (groupByResult instanceof TypeGroupByResult) {
                            groupType = ((TypeGroupByResult) groupByResult).getType();
                        }
                    }

                    DataPointGroup aggregatedGroup = queryResult;

                    List<Aggregator> aggregators = m_metric.getAggregators();

                    if (m_metric.getLimit() != 0) {
                        aggregatedGroup = new LimitAggregator(m_metric.getLimit()).aggregate(aggregatedGroup);
                    }

                    //This will pipe the aggregators together.
                    for (Aggregator aggregator : aggregators) {
                        //Make sure the aggregator can handle this type of data.
                        if (aggregator.canAggregate(groupType))
                            aggregatedGroup = aggregator.aggregate(aggregatedGroup);
                    }

                    m_results.add(aggregatedGroup);
                }
            } catch (Exception e) {
                Tags.ERROR.set(span, Boolean.TRUE);
                span.log(e.getMessage());
                throw e;
            } finally {
                span.finish();
            }
            return m_results;
        }

        private void logQuery() {
            final DatastoreMetricQueryMetadata meta = m_metric.getMeta();
            if (meta == null || !meta.getLoggable()) {
                return;
            }

            final long endTime = Long.MAX_VALUE == m_metric.getEndTime() ? System.currentTimeMillis() : m_metric.getEndTime();
            final long duration = endTime - m_metric.getStartTime();
            final boolean isUntilNow = System.currentTimeMillis() - endTime <= 30_000;
            logger.info("query_finished: type={} metric={} query={} datapoint_count={} rows_read={} " +
                            "rows_filtered={} start_time={} end_time={} duration={} is_until_now={}",
                    meta.getQueryType(), m_metric.getName(), m_metric.getTags(), m_dataPointCount, meta.getReadCount(),
                    m_rowCount, m_metric.getStartTime(), endTime, duration, isUntilNow);
        }

        @Override
        public void close() {
            try {
                if (m_results != null) {
                    for (DataPointGroup result : m_results) {
                        result.close();
                    }
                }
            } finally {  //This must get done
                m_queuingManager.done(m_cacheFilename);
            }
        }
    }
}
