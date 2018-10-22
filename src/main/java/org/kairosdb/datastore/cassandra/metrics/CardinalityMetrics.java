package org.kairosdb.datastore.cassandra.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import org.kairosdb.core.datastore.QueryMetric;
import org.kairosdb.datastore.cassandra.CassandraConfiguration;
import org.kairosdb.datastore.cassandra.CassandraDatastore;

public class CardinalityMetrics {

    private static final String MEASURES_PREFIX = "kairosdb.queries.";

    private final MetricRegistry metricRegistry;
    private final Histogram checkCardinality;
    private CassandraDatastore datastore;
    private CassandraConfiguration configuration;

    @Inject
    public CardinalityMetrics(MetricRegistry metricsRegistry, CassandraDatastore datastore, CassandraConfiguration configuration) {

        this.metricRegistry = metricsRegistry;
        this.datastore = datastore;
        this.configuration = configuration;
        checkCardinality = metricRegistry.histogram(MEASURES_PREFIX + "cardinality");
    }

    private boolean canQueryBeReported(final QueryMetric query) {
        return !query.getName().startsWith(MEASURES_PREFIX);
    }

    private void measureCardinality(final Histogram histogram, QueryMetric query) {
        histogram.update(datastore.getRowKeyIndexCardinality(query.getName()));
    }

    public void measureCardinalityMetric(QueryMetric query) {
        if (canQueryBeReported(query) && configuration.get_isUseCardinality()) {
            final Histogram histogram = metricRegistry.histogram(MEASURES_PREFIX + query.getName() + ".cardinality");
            measureCardinality(histogram, query);
            measureCardinality(checkCardinality, query);
        }
    }

    /*public void measureCardinalityMetric1(String name, int size) {
            final Histogram histogram = metricRegistry.histogram(MEASURES_PREFIX + name + ".cardinality");
            histogram.update(size);
            checkCardinality.update(size);
    }*/
}
