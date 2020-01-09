package org.kairosdb.core.datastore;

import java.util.UUID;

public class DatastoreMetricQueryMetadata {
    private final String queryType;
    private final String indexUsed;
    private final boolean loggable;
    private final int readCount;

    public DatastoreMetricQueryMetadata(String queryType, int readCount, String indexUsed, boolean loggable) {
        this.queryType = queryType;
        this.readCount = readCount;
        this.indexUsed = indexUsed;
        this.loggable = loggable;
    }

    public String getQueryType() {
        return queryType;
    }

    public boolean getLoggable() {
        return loggable;
    }

    public String getIndexUsed() {
        return indexUsed;
    }

    public int getReadCount() {
        return readCount;
    }
}
