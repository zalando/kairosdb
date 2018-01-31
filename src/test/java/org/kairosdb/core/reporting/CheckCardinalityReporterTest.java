package org.kairosdb.core.reporting;

import org.junit.Before;
import org.junit.Test;
import org.kairosdb.core.DataPointSet;
import org.kairosdb.datastore.cassandra.CassandraDatastore;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CheckCardinalityReporterTest {
    private CheckCardinalityReporter reporter;
    private CassandraDatastore datastore;

    @Before
    public void setUp() {
        datastore = mock(CassandraDatastore.class);
        reporter = new CheckCardinalityReporter(datastore);
    }

    @Test
    public void testReporterBuildsDatapointSet() throws Exception {
        when(datastore.getWindowCardinality(anyString())).thenReturn(100500L);
        when(datastore.getMetricNames()).thenReturn(Arrays.asList("foo", "bar"));

        final List<DataPointSet> points = reporter.getMetrics(0);

        assertEquals(2, points.size());
        assertTrue(points.stream().anyMatch(item -> "cardinality.foo".equals(item.getName())));
        assertTrue(points.stream().anyMatch(item -> "cardinality.bar".equals(item.getName())));
        assertTrue(points.stream().anyMatch(item -> 100500L == item.getDataPoints().get(0).getLongValue()));
    }

    @Test
    public void testReporterIgnoresDatastoreExceptions() throws Exception {
        when(datastore.getWindowCardinality(anyString())).thenReturn(100500L);
        when(datastore.getWindowCardinality("baz")).thenThrow(new Exception("bazbaz"));
        when(datastore.getMetricNames()).thenReturn(Arrays.asList("foo", "baz", "bar"));

        final List<DataPointSet> points = reporter.getMetrics(0);

        assertEquals(2, points.size());
        assertTrue(points.stream().anyMatch(item -> "cardinality.foo".equals(item.getName())));
        assertTrue(points.stream().anyMatch(item -> "cardinality.bar".equals(item.getName())));
        assertTrue(points.stream().anyMatch(item -> 100500L == item.getDataPoints().get(0).getLongValue()));
    }

    @Test
    public void testReporterIgnoresCardinalityChecks() throws Exception {
        when(datastore.getWindowCardinality(anyString())).thenReturn(100500L);
        when(datastore.getMetricNames()).thenReturn(Arrays.asList("foo", "cardinality.foo"));

        final List<DataPointSet> points = reporter.getMetrics(0);

        assertEquals(1, points.size());
        assertTrue(points.stream().anyMatch(item -> "cardinality.foo".equals(item.getName())));
        assertTrue(points.stream().anyMatch(item -> 100500L == item.getDataPoints().get(0).getLongValue()));
    }
}
