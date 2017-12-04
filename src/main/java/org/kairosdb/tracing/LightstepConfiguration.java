package org.kairosdb.tracing;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Created by abeverage on 12/4/17.
 */
public class LightstepConfiguration {

	private static final String ACCESS_TOKEN = "tracing.lightstep.access_token";
	private static final String COLLECTOR_HOST = "tracing.lightstep.collector_host";
	private static final String COLLECTOR_PORT = "tracing.lightstep.collector_port";
	private static final String COLLECTOR_PROTOCOL = "tracing.lightstep.collector_protocol";

	@Inject
	@Named(ACCESS_TOKEN)
	private String accessToken;

	@Inject
	@Named(COLLECTOR_HOST)
	private String collectorHost;

	@Inject(optional = true)
	@Named(COLLECTOR_PORT)
	private int collectorPort;

	@Inject(optional = true)
	@Named(COLLECTOR_PROTOCOL)
	private String collectorProtocol;

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getCollectorHost() {
		return collectorHost;
	}

	public void setCollectorHost(String collectorHost) {
		this.collectorHost = collectorHost;
	}

	public int getCollectorPort() {
		return collectorPort;
	}

	public void setCollectorPort(int collectorPort) {
		this.collectorPort = collectorPort;
	}

	public String getCollectorProtocol() {
		return collectorProtocol;
	}

	public void setCollectorProtocol(String collectorProtocol) {
		this.collectorProtocol = collectorProtocol;
	}
}
