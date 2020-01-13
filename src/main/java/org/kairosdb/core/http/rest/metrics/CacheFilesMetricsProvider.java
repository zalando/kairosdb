package org.kairosdb.core.http.rest.metrics;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.kairosdb.core.admin.InternalMetricsProvider;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.Collectors;

public class CacheFilesMetricsProvider implements InternalMetricsProvider {
    private static final String KAIROSDB_READ_CACHE_FILES_LIFESPAN = "kairosdb.read.cache.files.lifespan";
    private final MetricRegistry metricRegistry;
    private final Histogram fileLifespanHistogram;

    @Inject
    public CacheFilesMetricsProvider(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        fileLifespanHistogram = metricRegistry.histogram(KAIROSDB_READ_CACHE_FILES_LIFESPAN);
    }

    public void measureSpan(long fileCreateAt) {
        final long timeInMillis = System.currentTimeMillis() - fileCreateAt;
        final long timeInSeconds = timeInMillis / 1000;
        fileLifespanHistogram.update(timeInSeconds);
    }

    @Override
    public Map<String, Metric> getAll() {
        final Map<String, Metric> cacheMetrics = metricRegistry.getMetrics().entrySet().stream()
                .filter(e -> KAIROSDB_READ_CACHE_FILES_LIFESPAN.equals(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return ImmutableMap.copyOf(cacheMetrics);
    }

    @Override
    public Map<String, Metric> getForPrefix(@Nullable String prefix) {
        return getAll();
    }
}
