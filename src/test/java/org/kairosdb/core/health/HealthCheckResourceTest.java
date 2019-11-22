package org.kairosdb.core.health;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import io.opentracing.util.GlobalTracer;
import org.junit.Before;
import org.junit.Test;
import org.kairosdb.core.datastore.Datastore;
import org.kairosdb.core.exception.DatastoreException;
import org.kairosdb.core.http.rest.metrics.DefaultHealthCheckMetricProvider;
import org.kairosdb.core.http.rest.metrics.DefaultQueryMeasurementProvider;
import org.kairosdb.core.http.rest.metrics.HealthCheckMetricProvider;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthCheckResourceTest
{
	private HealthCheckResource resourceService;
	private Datastore datastore;

	@Before
	public void setup() throws DatastoreException
	{
		datastore = mock(Datastore.class);
		when(datastore.getMetricNames()).thenReturn(Collections.<String>emptyList());

		HealthCheckService healthCheckService = new TestHealthCheckService();
		final MetricRegistry registry = new MetricRegistry();
		final HealthCheckMetricProvider provider = new DefaultHealthCheckMetricProvider(registry);
		resourceService = new HealthCheckResource(healthCheckService, provider);
	}

	@Test(expected = NullPointerException.class)
	public void testConstructorNullHealthCheckServiceInvalid()
	{
		new HealthCheckResource(null, null);
	}

	@Test
	public void testCheckAllHealthy()
	{
		Response response = resourceService.check();

		assertThat(response.getStatus(), equalTo(Response.Status.NO_CONTENT.getStatusCode()));
	}

	@Test
	public void testCheckUnHealthy() throws DatastoreException
	{
		when(datastore.getMetricNames()).thenThrow(new DatastoreException("Error"));
		Response response = resourceService.check();

		assertThat(response.getStatus(), equalTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
	}

	@Test
	public void testStatusAllHealthy()
	{
		Response response = resourceService.status();

		assertThat(response.getStatus(), equalTo(Response.Status.OK.getStatusCode()));
	}

	@Test
	public void testStatusUnHealthy() throws DatastoreException
	{
		when(datastore.getMetricNames()).thenThrow(new DatastoreException("Error"));
		Response response = resourceService.status();

		assertThat(response.getStatus(), equalTo(Response.Status.OK.getStatusCode()));
	}

	private class TestHealthCheckService implements HealthCheckService
	{
		@Override
		public List<HealthStatus> getChecks()
		{
			List<HealthStatus> list = new ArrayList<HealthStatus>();
			list.add(new TestHealthStatus());
			list.add(new DatastoreQueryHealthCheck(datastore));

			return list;
		}
	}

	private class TestHealthStatus implements HealthStatus
	{
		@Override
		public String getName()
		{
			return getClass().getSimpleName();
		}

		@Override
		public HealthCheck.Result execute()
		{
			return HealthCheck.Result.healthy();
		}
	}
}