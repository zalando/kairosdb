package org.kairosdb.datastore.cassandra;

import com.google.common.collect.SetMultimap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SplitIndexDecider {
    public SplitIndex decideSplitIndex(List<String> indexTags, SetMultimap<String, String> filterTags) {
        String useSplitField = null;
        Set<String> useSplitSet = new HashSet<>();

        if (useMetricAsSplitField(indexTags, filterTags)) {
            useSplitField = "metric";
            useSplitSet = filterTags.get("key").stream().map((key) -> extractMetricsFromKey(key)).collect(Collectors.toSet());
        } else {
            for (String split : indexTags) {
                if (filterTags.containsKey(split)) {
                    Set<String> currentSet = filterTags.get(split);
                    final boolean currentSetIsSmaller = currentSet.size() < useSplitSet.size();
                    final boolean currentSetIsNotEmpty = currentSet.size() > 0 && useSplitSet.isEmpty();
                    final boolean currentSetHasNoWildcards = currentSet.stream().noneMatch(x -> x.contains("*") || x.contains("?"));
                    if ((currentSetIsSmaller || currentSetIsNotEmpty) && currentSetHasNoWildcards) {
                        useSplitSet = currentSet;
                        useSplitField = split;
                    }
                }
            }
        }
        return new SplitIndex(useSplitField, useSplitSet);
    }

    /* Rule when to use metric as split index key
    - Only use metric if any value of key tag has wild cards
    - If all other provided split index tags in the query do not have wildcards - Do not use metric as split index
    - If metric does not have wildcards */
    private boolean useMetricAsSplitField(List<String> indexTags, SetMultimap<String, String> filterTags) {
        if (filterTags.containsKey("key")) {
            Set<String> keyValues = filterTags.get("key");
            boolean keyHasWildcards = keyValues.stream().anyMatch(x -> x.contains("*") || x.contains("?"));
            if (keyHasWildcards && doAllSplitIndexTagsHaveWildCards(indexTags, filterTags)) {
                return true;
            }
        }
        return false;
    }

    private boolean doAllSplitIndexTagsHaveWildCards(List<String> indexTags, SetMultimap<String, String> filterTags) {
        for (String splitTag : indexTags) {
            if (filterTags.containsKey(splitTag)) {
                Set<String> tagValues = filterTags.get(splitTag);
                if (tagValues.stream().noneMatch(x -> x.contains("*") || x.contains("?")) == true) {
                    return false;
                }
            }
        }
        return true;
    }

    private String extractMetricsFromKey(String key) {
        String[] segments = key.split(".");
        String metric = segments[segments.length - 1];
        return metric.contains("*") || metric.contains("?") ? "" : metric;
    }
}
