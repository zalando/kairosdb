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
package org.kairosdb.core.reporting;

import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.ServletModule;
import org.kairosdb.core.http.MonitorFilter;

import java.util.List;

public class MetricReportingModule extends ServletModule {
    @Override
    protected void configureServlets() {
        bind(MetricReportingConfiguration.class).in(Scopes.SINGLETON);
        bind(MetricReporterService.class).in(Scopes.SINGLETON);

        bind(RuntimeReporter.class).in(Scopes.SINGLETON);
        bind(CacheMetricsReporter.class).in(Scopes.SINGLETON);
        bind(QueryMeasurementsReporter.class).in(Scopes.SINGLETON);
        bind(CacheFilesMeasurementsReporter.class).in(Scopes.SINGLETON);

        bind(new TypeLiteral<List<KairosMetricReporter>>() {
        }).toProvider(KairosMetricReporterListProvider.class);
    }
}