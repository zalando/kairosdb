package org.kairosdb.datastore.cassandra.circuitbreaker;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;

public class Metrics extends HystrixMetricsPublisher {

    private MetricRegistry registry;

    @Inject
    public Metrics(MetricRegistry registry){
        this.registry = registry;
    }
}
