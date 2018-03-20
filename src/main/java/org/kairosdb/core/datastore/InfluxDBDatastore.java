/*
 * Copyright 2013 Proofpoint Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.kairosdb.core.datastore;


import com.google.common.collect.ImmutableSortedMap;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.kairosdb.core.DataPoint;
import org.kairosdb.core.exception.DatastoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.jooq.SQLDialect.DEFAULT;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.val;

public class InfluxDBDatastore implements Datastore {
    public static final Logger logger = LoggerFactory.getLogger(InfluxDBDatastore.class);
    private InfluxDB influxDB;
    private String dbName;

    @Override
    public void close() throws InterruptedException, DatastoreException {
        influxDB.close();
    }

    @Override
    public void putDataPoint(final String metricName, final ImmutableSortedMap<String, String> tags, final DataPoint dataPoint, final int ttl) throws DatastoreException {

        Point.Builder pointBuilder = Point.measurement(metricName)
                .time(dataPoint.getTimestamp(), TimeUnit.MILLISECONDS);

        if (dataPoint.isDouble()) {
            pointBuilder.addField("data", dataPoint.getDoubleValue());
        } else if (dataPoint.isLong()) {
            pointBuilder.addField("data", dataPoint.getLongValue());
        } else {
            throw new DatastoreException("unsupported type of data point: " + dataPoint.getDataStoreDataType());
        }

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            pointBuilder.tag(entry.getKey(), entry.getValue());
        }

        influxDB.write(pointBuilder.build());
    }


    @Override
    public void queryDatabase(final DatastoreMetricQuery query, final QueryCallback queryCallback) throws DatastoreException {
        final DSLContext create = DSL.using(DEFAULT);
        long startTime = query.getStartTime();
        long endTime = query.getEndTime();
        SelectConditionStep queryBuilder = create.select(field("data"))
                .from(query.getName())
                .where(val("time").ge(Long.toString(startTime)).and(val("time").le(Long.toString(endTime))));
        for (Map.Entry<String, Collection<String>> entry : query.getTags().asMap().entrySet()) {
            Condition cond = null;
            String tagName = entry.getKey();
            for (String tagValue : entry.getValue()) {
                if (cond == null) {
                    cond = val(tagName).eq(tagValue);
                    continue;
                }
                cond = cond.or(val(tagName).eq(tagValue));
            }
            if (cond != null) {
                queryBuilder = queryBuilder.and(cond);
            }
        }

        final String sql = queryBuilder
                .limit(query.getLimit())
                .getSQL();

        logger.warn("executing query: {}", sql);

        QueryResult response = this.influxDB.query(new Query(sql, dbName));
        if (response.hasError()) {
            throw new DatastoreException(response.getError());
        }

        for (QueryResult.Result result : response.getResults()) {
            if (result.hasError()) {
                logger.error("one of the results returned an error: {}", result.getError());
                continue;
            }

            for (QueryResult.Series series : result.getSeries()) {
                logger.warn("!!!series received: {}", series.toString());
            }
        }
    }

    @Override
    public Iterable<String> getMetricNames() throws DatastoreException {
        return null;
    }

    @Override
    public Iterable<String> getTagNames() throws DatastoreException {
        return null;
    }

    @Override
    public Iterable<String> getTagValues() throws DatastoreException {
        return null;
    }


    @Override
    public void deleteDataPoints(final DatastoreMetricQuery deleteQuery) throws DatastoreException {
        throw new DatastoreException("deleteDataPoints is not supported");
    }

    @Override
    public TagSet queryMetricTags(final DatastoreMetricQuery query) throws DatastoreException {
        return null;
    }
}
