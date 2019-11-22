package org.kairosdb.core.http.rest.metrics;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class DefaultHealthCheckMetricProvider implements HealthCheckMetricProvider {

    private static final String DEADLOCK_THERAD_MEASURES_PREFIX = "kairosdb.health.deadlock.thread_count";
    private static final String CASSANDRA_UNHEALTHY_MEASURES_PREFIX = "kairosdb.health.cassandra.unhealthy_count";

    private final MetricRegistry metricRegistry;
    private final Histogram deadlockHistogramCount;
    private final Histogram cassandraUnhealthyHistogramCount;

    @Inject
    public DefaultHealthCheckMetricProvider(@Nonnull final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        deadlockHistogramCount = metricRegistry.histogram(DEADLOCK_THERAD_MEASURES_PREFIX);
        cassandraUnhealthyHistogramCount = metricRegistry.histogram(CASSANDRA_UNHEALTHY_MEASURES_PREFIX);
    }

    @Override
    public void measureJVMDeadlockThread() {
        deadlockHistogramCount.update(1);
    }

    @Override
    public void measureCassandraUnhealthyCheck() {
        cassandraUnhealthyHistogramCount.update(1);
    }

    @Override
    public Map<String, Metric> getAll() {
        return null;
    }

    @Override
    public Map<String, Metric> getForPrefix(@Nullable String prefix) {
        return null;
    }
}
