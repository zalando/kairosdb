package org.kairosdb.core.http.rest;

import com.google.common.collect.SetMultimap;
import org.kairosdb.core.datastore.QueryMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class QueryAutocompleter {
    private static final Logger logger = LoggerFactory.getLogger(QueryAutocompleter.class);

    public void complete(QueryMetric query) {
        completeMetricTag(query);
    }

    private void completeMetricTag(QueryMetric query) {
        final SetMultimap<String, String> tags = query.getTags();
        final Set<String> keys = tags.get("key");
        final Set<String> metrics = new HashSet<>();
        for (final String key : keys) {
            try {
                final String metric = extractMetricName(key);
                if (isWildcard(metric)) {
                    return;
                }
                metrics.add(metric);
            } catch (Exception e) {
                logger.warn("Problem while parsing key: " + key, e);
                return;
            }
        }
        tags.putAll("metric", metrics);
    }

    private String extractMetricName(final String key) {
        if (null == key || "".equals(key)) return null;
        final String[] keyParts = key.split("\\.");
        final String metricName = keyParts[keyParts.length - 1];
        return "".equals(metricName) ? keyParts[keyParts.length - 2] : metricName;
    }


    private boolean isWildcard(final String metric) {
        return metric.contains("*") || metric.contains("?");
    }

}
