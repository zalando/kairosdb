package org.kairosdb.datastore.cassandra;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.EC2AwareRoundRobinPolicy;
import com.datastax.driver.core.policies.EC2MultiRegionAddressTranslator;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 Created by bhawkins on 3/4/15.
 */
public class CassandraClientImpl implements CassandraClient {
	private static final Logger logger = LoggerFactory.getLogger(CassandraClientImpl.class);

	private final Cluster m_cluster;
	private String m_keyspace;

	private static final String CASSANDRA_READ_TIMEOUT = "kairosdb.datastore.cassandra.read.timeout";

	@javax.inject.Inject
	@Named(CASSANDRA_READ_TIMEOUT)
	// We set the default timeout as configured by the upstream driver.
	private int m_cassandraReadTimeout = 12000;

	@Inject
	public CassandraClientImpl(CassandraConfiguration config) {
		final Cluster.Builder builder = new Cluster.Builder().withSocketOptions(
				new SocketOptions().setReadTimeoutMillis(m_cassandraReadTimeout));

		if (config.getAddressTranslator().equals(CassandraConfiguration.ADDRESS_TRANSLATOR_TYPE.EC2)) {
			builder.withAddressTranslator(new EC2MultiRegionAddressTranslator());
			// This should work, seems the EC2AwareRoundRobinPolicy uses REMOTE for not being in the SAME az
			builder.withLoadBalancingPolicy(new TokenAwarePolicy(EC2AwareRoundRobinPolicy.CreateEC2AwareRoundRobinPolicy()));
		} else {
			builder.withLoadBalancingPolicy(new TokenAwarePolicy(DCAwareRoundRobinPolicy.builder().build()));
		}

		final QueryOptions queryOptions = new QueryOptions().setConsistencyLevel(config.getDataReadLevel());
		builder.withQueryOptions(queryOptions);

		for (String node : config.getHostList().split(",")) {
			builder.addContactPoint(node.trim());
		}

		final String user = config.getUser();
		final String password = config.getPassword();
		if (null != user && null != password && !"".equals(user) && !"".equals(password)) {
			builder.withCredentials(user, password);
		}

		m_cluster = builder.build();
		m_keyspace = config.getKeyspaceName();

		logConnectionPoolConfig(m_cluster.getConfiguration().getPoolingOptions());
	}

	@Override
	public Session getKeyspaceSession() {
		return m_cluster.connect(m_keyspace);
	}

	@Override
	public Session getSession() {
		return m_cluster.connect();
	}

	@Override
	public String getKeyspace() {
		return m_keyspace;
	}

	@Override
	public void close() {
		m_cluster.close();
	}

    private void logConnectionPoolConfig(PoolingOptions poolOpts) {
        logger.warn("Core connections per host (remote): " + poolOpts.getCoreConnectionsPerHost(HostDistance.REMOTE));
        logger.warn("Core connections per host (local): " + poolOpts.getCoreConnectionsPerHost(HostDistance.LOCAL));
        logger.warn("Max connections per host (remote): " + poolOpts.getMaxConnectionsPerHost(HostDistance.REMOTE));
        logger.warn("Max connections per host (local): " + poolOpts.getMaxConnectionsPerHost(HostDistance.LOCAL));
        logger.warn("Max requests per connection (remote): " + poolOpts.getMaxRequestsPerConnection(HostDistance.REMOTE));
        logger.warn("Max requests per connection (local): " + poolOpts.getMaxRequestsPerConnection(HostDistance.LOCAL));
        logger.warn("Max queue size: " + poolOpts.getMaxQueueSize());
        logger.warn("Pool timeout mills: " + poolOpts.getPoolTimeoutMillis());
        logger.warn("Idle timeout seconds: " + poolOpts.getIdleTimeoutSeconds());

        final ProtocolOptions protocolOpts = m_cluster.getConfiguration().getProtocolOptions();
        if (protocolOpts != null && protocolOpts.getProtocolVersion() != null) {
            logger.warn("Protocol version: " + protocolOpts.getProtocolVersion().toString());
        }
    }

	public void logConnectionStats(final Session session) {
            final Session.State state = session.getState();
			final Configuration configuration = session.getCluster().getConfiguration();
			final PoolingOptions poolingOptions = configuration.getPoolingOptions();
			final Collection<Host> connectedHosts = state.getConnectedHosts();
			for (Host host : connectedHosts) {
				final HostDistance distance = configuration.getPolicies().getLoadBalancingPolicy().distance(host);
                final int connections = state.getOpenConnections(host);
                final int inFlightQueries = state.getInFlightQueries(host);
				final int maxRequestsPerConnection = poolingOptions.getMaxRequestsPerConnection(distance);
                logger.debug("connection_stats: hosts_count={} host={} distance={} connections={}, current_load={}, max_load={}",
						connectedHosts.size(), host, distance.name(), connections, inFlightQueries, connections * maxRequestsPerConnection);
            }
    }
}
