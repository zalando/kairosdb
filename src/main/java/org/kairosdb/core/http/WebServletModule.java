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
package org.kairosdb.core.http;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import com.squarespace.jersey2.guice.JerseyGuiceModule;
import org.kairosdb.core.health.HealthCheckResource;
import org.kairosdb.core.health.HealthCheckService;
import org.kairosdb.core.health.HealthCheckServiceImpl;
import org.kairosdb.core.http.rest.MetricsResource;
import org.kairosdb.core.http.rest.metrics.DefaultQueryMeasurementProvider;
import org.kairosdb.core.http.rest.metrics.QueryMeasurementProvider;

import java.util.Properties;

public class WebServletModule extends AbstractModule
{
	public WebServletModule(Properties props)
	{
	}

	@Override
	protected void configure()
	{
	    install(new JerseyGuiceModule("__HK2_Generated_0"));
        install(new ServletModule() {
            @Override
            protected void configureServlets() {
                binder().requireExplicitBindings();
                bind(WebServer.class);
                bind(QueryMeasurementProvider.class).to(DefaultQueryMeasurementProvider.class).in(Singleton.class);
                bind(MetricsResource.class).in(Scopes.SINGLETON);
                bind(HealthCheckService.class).to(HealthCheckServiceImpl.class).in(javax.inject.Singleton.class);
                bind(HealthCheckResource.class).in(Scopes.SINGLETON);
            }
        });
	}
}