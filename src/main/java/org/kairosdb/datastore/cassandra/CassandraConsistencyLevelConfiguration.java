package org.kairosdb.datastore.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.google.inject.Inject;

public class CassandraConsistencyLevelConfiguration {

    private ConsistencyLevel readLevel;

    @Inject
    public CassandraConsistencyLevelConfiguration(CassandraConfiguration cassandraConfiguration) {
        this.readLevel = cassandraConfiguration.getDataReadLevel();
    }

    public ConsistencyLevel getReadLevel() {
        return readLevel;
    }

    public void setReadLevel(ConsistencyLevel readLevel) {
        this.readLevel = readLevel;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CassandraConsistencyLevelConfiguration{");
        sb.append("readLevel=").append(readLevel.toString());
        sb.append('}');
        return sb.toString();
    }
}
