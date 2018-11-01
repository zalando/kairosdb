package org.kairosdb.datastore.cassandra.circuitbreaker;

import com.datastax.driver.core.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.netflix.hystrix.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CommandExecute  extends HystrixCommand<ResultSet>{

    public static final Logger logger = LoggerFactory.getLogger(CommandExecute.class);

    private final Session session;
    private final BoundStatement bs;

    @Inject
    private static HystrixProperties configuration;

    private static Setter setter;
    /**
     * Peak rps for datapoints_query is 137 rps across 6 kairosdb-read instances
     * Peak rps per instance = 23
     * p99 latency of ~1.57 seconds
     * Thread pool size: 137 * 1.57 = 36 = ~40
     * @param session Cassandra Session object which executes the query
     * @param bs Prepared CQL statement
     */
    public CommandExecute(Session session, BoundStatement bs){
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("CQLExecute"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CQLExecute"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionTimeoutInMilliseconds(1000)
                        .withFallbackIsolationSemaphoreMaxConcurrentRequests(10)
                        .withCircuitBreakerRequestVolumeThreshold(20)
                        .withCircuitBreakerForceOpen(true)
                        .withFallbackEnabled(false)
                        .withCircuitBreakerSleepWindowInMilliseconds(5000))
                .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
                        .withAllowMaximumSizeToDivergeFromCoreSize(true)
                        .withMaximumSize(10))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("CQLExecute-ThreadPool")));
        this.session = session;
        this.bs = bs;
    }

    /*@Inject
    private CommandExecute(Session session, BoundStatement bs, HystrixProperties configuration){
        this(session, bs, Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("CQLExecute"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CQLExecute"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionTimeoutInMilliseconds(configuration.getM_ExecutionTimeoutMillis())
                        .withFallbackIsolationSemaphoreMaxConcurrentRequests(configuration.getM_FallbackMaxConcurrentRequests())
                        .withCircuitBreakerRequestVolumeThreshold(configuration.getM_RequestVolumeThreshold())
                        .withCircuitBreakerSleepWindowInMilliseconds(configuration.getM_SleedWindowMillis()))
                .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
                        .withAllowMaximumSizeToDivergeFromCoreSize(configuration.isM_AllowDivergeFromCore())
                        .withMaximumSize(configuration.getM_ThreadPoolMax()))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("CQLExecute-ThreadPool")));
    }

    private CommandExecute(Session session, BoundStatement bs, Setter setter){
        super(setter);
        this.session = session;
        this.bs = bs;
    }*/

    @Override
    protected ResultSet run() {
        return session.execute(bs);
    }

    @Override
    protected ResultSet getFallback() {
        return new ResultSet() {
            @Override
            public ColumnDefinitions getColumnDefinitions() {
                return null;
            }

            @Override
            public boolean isExhausted() {
                return false;
            }

            @Override
            public Row one() {
                return null;
            }

            @Override
            public List<Row> all() {
                List<Row> list = new ArrayList<>();
                return list;
            }

            @Override
            public Iterator<Row> iterator() {
                return null;
            }

            @Override
            public int getAvailableWithoutFetching() {
                return 0;
            }

            @Override
            public boolean isFullyFetched() {
                return false;
            }

            @Override
            public ListenableFuture<ResultSet> fetchMoreResults() {
                return null;
            }

            @Override
            public ExecutionInfo getExecutionInfo() {
                return null;
            }

            @Override
            public List<ExecutionInfo> getAllExecutionInfo() {
                return null;
            }

            @Override
            public boolean wasApplied() {
                return false;
            }
        };
    }


}
