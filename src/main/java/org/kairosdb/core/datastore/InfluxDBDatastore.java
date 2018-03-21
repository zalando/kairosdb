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
import org.influxdb.InfluxDB;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.kairosdb.core.DataPoint;
import org.kairosdb.core.KairosDataPointFactory;
import org.kairosdb.core.aggregator.Aggregator;
import org.kairosdb.core.aggregator.LimitAggregator;
import org.kairosdb.core.exception.DatastoreException;
import org.kairosdb.core.groupby.GroupBy;
import org.kairosdb.core.groupby.GroupByResult;
import org.kairosdb.core.groupby.Grouper;
import org.kairosdb.core.groupby.TagGroupBy;
import org.kairosdb.core.groupby.TagGroupByResult;
import org.kairosdb.core.groupby.TypeGroupByResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jooq.SQLDialect.DEFAULT;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.val;

public class InfluxDBDatastore implements Datastore {
    public static final Logger logger = LoggerFactory.getLogger(InfluxDBDatastore.class);

    private final KairosDataPointFactory m_dataPointFactory;
    private final InfluxDB influxDB;
    private final String dbName;

    @Inject
    private InfluxDBDatastore(final KairosDataPointFactory dataPointFactory,
                              final InfluxDB influxDB,
                              final String dbName) {
        this.m_dataPointFactory = dataPointFactory;
        this.influxDB = influxDB;
        this.dbName = dbName;
    }

    @Override
    public void close() throws InterruptedException, DatastoreException {
        influxDB.close();
    }

    @Override
    public void putDataPoint(final String metricName, final ImmutableSortedMap<String, String> tags, final DataPoint dataPoint, final int ttl) throws DatastoreException {

        Point.Builder pointBuilder = Point.measurement(metricName)
                .time(dataPoint.getTimestamp(), TimeUnit.MILLISECONDS);

        if (dataPoint.isDouble()) {
            pointBuilder.addField("data", dataPoint.getDoubleValue());
        } else if (dataPoint.isLong()) {
            pointBuilder.addField("data", dataPoint.getLongValue());
        } else {
            throw new DatastoreException("unsupported type of data point: " + dataPoint.getDataStoreDataType());
        }

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            pointBuilder.tag(entry.getKey(), entry.getValue());
        }

        influxDB.write(pointBuilder.build());
    }


    @Override
    public void queryDatabase(final DatastoreMetricQuery query, final QueryCallback queryCallback) throws DatastoreException {
        final DSLContext create = DSL.using(DEFAULT);
        long startTime = query.getStartTime();
        long endTime = query.getEndTime();
        SelectConditionStep queryBuilder = create.select(field("data"))
                .from(query.getName())
                .where(val("time").ge(Long.toString(startTime)).and(val("time").le(Long.toString(endTime))));
        for (Map.Entry<String, Collection<String>> entry : query.getTags().asMap().entrySet()) {
            Condition cond = null;
            String tagName = entry.getKey();
            for (String tagValue : entry.getValue()) {
                if (cond == null) {
                    cond = val(tagName).eq(tagValue);
                    continue;
                }
                cond = cond.or(val(tagName).eq(tagValue));
            }
            if (cond != null) {
                queryBuilder = queryBuilder.and(cond);
            }
        }

        final String sql = queryBuilder
                .limit(query.getLimit())
                .getSQL();

        logger.warn("executing query: {}", sql);

        QueryResult response = this.influxDB.query(new Query(sql, dbName));
        if (response.hasError()) {
            throw new DatastoreException(response.getError());
        }

        for (QueryResult.Result result : response.getResults()) {
            if (result.hasError()) {
                logger.error("one of the results returned an error: {}", result.getError());
                continue;
            }

            for (QueryResult.Series series : result.getSeries()) {
                // Series is a datapointrow
                try {
                    // TODO: replace "double" with proper type
                    queryCallback.startDataPointSet("double", series.getTags());
                } catch (IOException e) {
                    //!!! do nothing
                }

                // TODO: add datapoints here
                // Question: what series.getValues() mean?

                try {
                    queryCallback.endDataPoints();
                } catch (IOException e) {
                    //!!! do nothing
                }

                logger.warn("!!!series received: {}", series.toString());
            }
        }
    }

    @Override
    public Iterable<String> getMetricNames() throws DatastoreException {
        return null;
    }

    @Override
    public Iterable<String> getTagNames() throws DatastoreException {
        return null;
    }

    @Override
    public Iterable<String> getTagValues() throws DatastoreException {
        return null;
    }


    @Override
    public void deleteDataPoints(final DatastoreMetricQuery deleteQuery) throws DatastoreException {
        throw new DatastoreException("deleteDataPoints is not supported");
    }

    @Override
    public TagSet queryMetricTags(final DatastoreMetricQuery query) throws DatastoreException {
        return null;
    }

    public DatastoreQuery createQuery(QueryMetric metric) throws DatastoreException {
        checkNotNull(metric);

        DatastoreQuery dq;

        dq = new InfluxDBDatastore.DatastoreQueryImpl(this, metric);

        return (dq);
    }

    private class DatastoreQueryImpl implements DatastoreQuery {
        private Datastore m_datastore;
        private QueryMetric m_metric;
        private List<DataPointGroup> m_results;
        private int m_dataPointCount;
        private int m_rowCount;

        public DatastoreQueryImpl(Datastore datastore, QueryMetric metric) {
            m_metric = metric;
            m_datastore = datastore;
        }

        public int getSampleSize() {
            return m_dataPointCount;
        }

        public int getRowCount() {
            return m_rowCount;
        }

        @Override
        public List<DataPointGroup> execute() throws DatastoreException {
            DumbQueryCallback callback = new DumbQueryCallback(m_metric.getName());

            List<DataPointRow> returnedRows = null;

            try {
                m_datastore.queryDatabase(m_metric, callback);
                returnedRows = callback.getRows();
                m_rowCount = returnedRows.size();
            } catch (Exception e) {
                throw new DatastoreException(e);
            }

            //Get data point count
            for (DataPointRow returnedRow : returnedRows) {
                m_dataPointCount += returnedRow.getDataPointCount();
            }

            List<DataPointGroup> queryResults = groupByTypeAndTag(m_metric.getName(),
                    returnedRows, getTagGroupBy(m_metric.getGroupBys()), m_metric.getOrder());

            // Now group for all other types of group bys.
            Grouper grouper = new Grouper(m_dataPointFactory);
            try {
                queryResults = grouper.group(removeTagGroupBy(m_metric.getGroupBys()), queryResults);
            } catch (IOException e) {
                throw new DatastoreException(e);
            }

            m_results = new LinkedList<>();
            for (DataPointGroup queryResult : queryResults) {
                String groupType = DataPoint.GROUP_NUMBER;
                //todo May want to make group type a first class citizen in DataPointGroup
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

            return (m_results);
        }

        private List<DataPointGroup> groupByTypeAndTag(String metricName,
                                                       List<DataPointRow> rows, TagGroupBy tagGroupBy, Order order) {
            List<DataPointGroup> ret = new ArrayList<>();

            if (rows.isEmpty()) {
                ret.add(new SortingDataPointGroup(metricName, order));
            } else {
                ListMultimap<String, DataPointGroup> typeGroups = ArrayListMultimap.create();

                //Go through each row grouping them by type
                for (DataPointRow row : rows) {
                    String groupType = m_dataPointFactory.getGroupType(row.getDatastoreType());

                    typeGroups.put(groupType, new DataPointGroupRowWrapper(row));
                }

                //Sort the types for predictable results
                TreeSet<String> sortedTypes = new TreeSet<String>(typeGroups.keySet());

                //Now go through each type group and group by tag if needed.
                for (String type : sortedTypes) {
                    if (tagGroupBy != null) {
                        ListMultimap<String, DataPointGroup> groups = ArrayListMultimap.create();
                        Map<String, TagGroupByResult> groupByResults = new HashMap<String, TagGroupByResult>();

                        for (DataPointGroup dataPointGroup : typeGroups.get(type)) {
                            //Todo: Add code to datastore implementations to filter by the group by tag

                            LinkedHashMap<String, String> matchingTags = getMatchingTags(dataPointGroup, tagGroupBy.getTagNames());
                            String tagsKey = getTagsKey(matchingTags);
                            groups.put(tagsKey, dataPointGroup);
                            groupByResults.put(tagsKey, new TagGroupByResult(tagGroupBy, matchingTags));
                        }

                        //Sort groups by tags
                        TreeSet<String> sortedGroups = new TreeSet<String>(groups.keySet());

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

        private String getTagsKey(LinkedHashMap<String, String> tags) {
            StringBuilder builder = new StringBuilder();
            for (String name : tags.keySet()) {
                builder.append(name).append(tags.get(name));
            }

            return builder.toString();
        }

        private LinkedHashMap<String, String> getMatchingTags(DataPointGroup datapointGroup, List<String> tagNames) {
            LinkedHashMap<String, String> matchingTags = new LinkedHashMap<String, String>();
            for (String tagName : tagNames) {
                Set<String> tagValues = datapointGroup.getTagValues(tagName);
                if (tagValues != null) {
                    String tagValue = tagValues.iterator().next();
                    matchingTags.put(tagName, tagValue != null ? tagValue : "");
                }
            }

            return matchingTags;
        }

        private TagGroupBy getTagGroupBy(List<GroupBy> groupBys) {
            for (GroupBy groupBy : groupBys) {
                if (groupBy instanceof TagGroupBy)
                    return (TagGroupBy) groupBy;
            }
            return null;
        }

        private List<GroupBy> removeTagGroupBy(List<GroupBy> groupBys) {
            List<GroupBy> modifiedGroupBys = new ArrayList<GroupBy>();
            for (GroupBy groupBy : groupBys) {
                if (!(groupBy instanceof TagGroupBy))
                    modifiedGroupBys.add(groupBy);
            }
            return modifiedGroupBys;
        }

        @Override
        public void close() {
            // TODO: NOOP
        }
    }

}
