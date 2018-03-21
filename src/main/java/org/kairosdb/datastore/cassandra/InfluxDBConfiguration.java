package org.kairosdb.datastore.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.Map;

public class InfluxDBConfiguration {


    private static final String URL_PROPERTY = "kairosdb.datastore.influxdb.url";
    private static final String DBNAME_PROPERTY = "kairosdb.datastore.influxdb.dbname";
    private static final String USERNAME_PROPERTY = "kairosdb.datastore.influxdb.username";
    private static final String PASSWORD_PROPERTY = "kairosdb.datastore.influxdb.password";

    @Inject
    @Named(URL_PROPERTY)
    private String influxDBURL;

    @Inject
    @Named(DBNAME_PROPERTY)
    private String influxDBName;

    @Inject
    @Named(USERNAME_PROPERTY)
    private String username;

    @Inject
    @Named(PASSWORD_PROPERTY)
    private String password;


    public String getInfluxDBURL() {
        return influxDBURL;
    }

    public void setInfluxDBURL(final String influxDBURL) {
        this.influxDBURL = influxDBURL;
    }

    public String getInfluxDBName() {
        return influxDBName;
    }

    public void setInfluxDBName(final String influxDBName) {
        this.influxDBName = influxDBName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }
}
