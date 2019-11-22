package org.kairosdb.core.http.rest.metrics;

import org.kairosdb.core.admin.InternalMetricsProvider;

public interface HealthCheckMetricProvider extends InternalMetricsProvider {
    void measureJVMDeadlockThread();

    void measureCassandraUnhealthyCheck();
}
