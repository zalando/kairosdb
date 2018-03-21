package org.kairosdb.core.datastore;

import org.kairosdb.core.DataPoint;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DumbQueryCallback implements QueryCallback {
    private List<DataPointRow> dataPointRows = new LinkedList<>();
    private DataPointRowImpl currentDataPointRow;
    private final String metricName;

    public DumbQueryCallback(final String metricName) {
        this.metricName = metricName;
    }

    @Override
    public void addDataPoint(final DataPoint datapoint) throws IOException {
        this.currentDataPointRow.addDataPoint(datapoint);
    }

    @Override
    public void startDataPointSet(final String dataType, final Map<String, String> tags) throws IOException {
        this.currentDataPointRow = new DataPointRowImpl();
        this.currentDataPointRow.setName(metricName);

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            this.currentDataPointRow.addTag(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void endDataPoints() throws IOException {
        this.dataPointRows.add(this.currentDataPointRow);
        this.currentDataPointRow = null;
    }

    public List<DataPointRow> getRows() {
        return this.dataPointRows;
    }
}
