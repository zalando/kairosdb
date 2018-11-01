package org.kairosdb.datastore.cassandra.circuitbreaker;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class HystrixProperties {

    private static final String EXECUTION_TIMEOUT_MILLIS = "hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds";
    private static final String FALLBACK_MAX_CONCURRENT_REQUESTS = "hystrix.command.default.execution.isolation.semaphore.maxConcurrentRequests";
    private static final String REQUEST_VOLUME_THRESHOLD = "hystrix.command.default.circuitBreaker.requestVolumeThreshold";
    private static final String SLEEP_WINDOW_MILLIS = "hystrix.command.default.circuitBreaker.sleepWindowInMilliseconds";
    private static final String THREAD_POOL_MAX = "hystrix.threadpool.default.maximumSize";
    private static final String ALLOW_DIVERGE_FROM_CORE = "hystrix.threadpool.default.allowMaximumSizeToDivergeFromCoreSize";

    @Inject(optional = true)
    @Named(EXECUTION_TIMEOUT_MILLIS)
    private int m_ExecutionTimeoutMillis = 1000;

    @Inject(optional = true)
    @Named(FALLBACK_MAX_CONCURRENT_REQUESTS)
    private int m_FallbackMaxConcurrentRequests = 10;

    @Inject(optional = true)
    @Named(REQUEST_VOLUME_THRESHOLD)
    private int m_RequestVolumeThreshold = 20;

    @Inject(optional = true)
    @Named(SLEEP_WINDOW_MILLIS)
    private int m_SleedWindowMillis = 5000;

    @Inject(optional = true)
    @Named(THREAD_POOL_MAX)
    private int m_ThreadPoolMax = 10;

    @Inject(optional = true)
    @Named(ALLOW_DIVERGE_FROM_CORE)
    private boolean m_AllowDivergeFromCore = false;

    public HystrixProperties(){}

    public int getM_ExecutionTimeoutMillis() {
        return m_ExecutionTimeoutMillis;
    }

    public void setM_ExecutionTimeoutMillis(int m_ExecutionTimeoutMillis) {
        this.m_ExecutionTimeoutMillis = m_ExecutionTimeoutMillis;
    }

    public int getM_FallbackMaxConcurrentRequests() {
        return m_FallbackMaxConcurrentRequests;
    }

    public void setM_FallbackMaxConcurrentRequests(int m_FallbackMaxConcurrentRequests) {
        this.m_FallbackMaxConcurrentRequests = m_FallbackMaxConcurrentRequests;
    }

    public int getM_RequestVolumeThreshold() {
        return m_RequestVolumeThreshold;
    }

    public void setM_RequestVolumeThreshold(int m_RequestVolumeThreshold) {
        this.m_RequestVolumeThreshold = m_RequestVolumeThreshold;
    }

    public int getM_SleedWindowMillis() {
        return m_SleedWindowMillis;
    }

    public void setM_SleedWindowMillis(int m_SleedWindowMillis) {
        this.m_SleedWindowMillis = m_SleedWindowMillis;
    }

    public int getM_ThreadPoolMax() {
        return m_ThreadPoolMax;
    }

    public void setM_ThreadPoolMax(int m_ThreadPoolMax) {
        this.m_ThreadPoolMax = m_ThreadPoolMax;
    }

    public boolean isM_AllowDivergeFromCore() {
        return m_AllowDivergeFromCore;
    }

    public void setM_AllowDivergeFromCore(boolean m_AllowDivergeFromCore) {
        this.m_AllowDivergeFromCore = m_AllowDivergeFromCore;
    }
}
