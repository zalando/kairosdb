package org.kairosdb.datastore.cassandra.circuitbreaker;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import org.kairosdb.datastore.cassandra.CassandraDatastore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HystrixModule extends AbstractModule{
    public static final Logger logger = LoggerFactory.getLogger(HystrixModule.class);

    @Override
    protected void configure(){
        logger.info("Configured HystrixModule");
        bind(HystrixProperties.class).in(Singleton.class);
    }

    @Singleton
    HystrixMetricsPublisher hystrixMetricsPublisher(MetricRegistry metricRegistry) {
        HystrixCodaHaleMetricsPublisher publisher = new HystrixCodaHaleMetricsPublisher(metricRegistry);
        HystrixPlugins.getInstance().registerMetricsPublisher(publisher);
        logger.info("Hystrix Metrics Publisher configured: {}", publisher.toString());
        return publisher;
    }
}
