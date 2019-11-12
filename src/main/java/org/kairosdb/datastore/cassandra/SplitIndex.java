package org.kairosdb.datastore.cassandra;

import java.util.Set;

public class SplitIndex {
    private String splitIndexField;
    private Set<String> splitIndexValues;

    public SplitIndex(String splitIndexField, Set<String> splitIndexValues) {
        this.splitIndexField = splitIndexField;
        this.splitIndexValues = splitIndexValues;
    }

    public String getSplitIndexField() {
        return splitIndexField;
    }

    public Set<String> getSplitIndexValues() {
        return splitIndexValues;
    }
}

