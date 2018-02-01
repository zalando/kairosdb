package org.kairosdb.core.reporting;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.kairosdb.core.DataPoint;
import org.kairosdb.core.DataPointSet;
import org.kairosdb.core.datapoints.LongDataPointFactory;
import org.kairosdb.core.datapoints.LongDataPointFactoryImpl;
import org.kairosdb.datastore.cassandra.CassandraDatastore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class CheckCardinalityReporter implements KairosMetricReporter {

    private static final Logger logger = LoggerFactory.getLogger(CheckCardinalityReporter.class);
    private static final String CARDINALITY_PREFIX = "cardinality.";

    @Inject
    private CassandraDatastore datastore;

    private LongDataPointFactory longDataPointFactory = new LongDataPointFactoryImpl();

    public CheckCardinalityReporter(final CassandraDatastore datastore) {
        checkNotNull(datastore, "datastore can't be null");

        this.datastore = datastore;
    }

    @Override
    public List<DataPointSet> getMetrics(final long now) {
        final ImmutableList.Builder<DataPointSet> builder = ImmutableList.builder();
        Iterable<String> metricNames = datastore.getMetricNames();
        for (String metricName : metricNames) {
            if (metricName.startsWith(CARDINALITY_PREFIX)) {
                continue;
            }

            try {
                long cardinality = datastore.getWindowCardinality(metricName);
                final DataPointSet dataPointSet = createDataPointSet(now, metricName, cardinality);
                builder.add(dataPointSet);
            } catch (Throwable e) {
                logger.error("failed reporting cardinality for metric '{}': ({}){}", metricName,
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
        return builder.build();
    }

    private DataPointSet createDataPointSet(final long now, final String metricName, final long cardinality) {
        final DataPointSet dataPointSet = new DataPointSet(CARDINALITY_PREFIX + metricName);

        final DataPoint dataPoint = longDataPointFactory.createDataPoint(now, cardinality);
        dataPointSet.addDataPoint(dataPoint);

        return dataPointSet;
    }
}
