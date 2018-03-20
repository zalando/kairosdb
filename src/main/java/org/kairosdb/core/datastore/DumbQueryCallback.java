package org.kairosdb.core.datastore;

import org.kairosdb.core.DataPoint;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DumbQueryCallback implements QueryCallback {
    private List<DataPoint> dataPoints = new LinkedList<>();

    @Override
    public void addDataPoint(final DataPoint datapoint) throws IOException {
        dataPoints.add(datapoint);
    }

    @Override
    public void startDataPointSet(final String dataType, final Map<String, String> tags) throws IOException {
        // effectively noop
    }

    @Override
    public void endDataPoints() throws IOException {
        // effectively noop
    }
}
