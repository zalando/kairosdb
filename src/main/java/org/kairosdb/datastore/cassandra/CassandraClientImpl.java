package org.kairosdb.datastore.cassandra;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.EC2AwareRoundRobinPolicy;
import com.datastax.driver.core.policies.EC2MultiRegionAddressTranslator;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 Created by bhawkins on 3/4/15.
 */
public class CassandraClientImpl implements CassandraClient
{
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

		final PoolingOptions poolingOptions = new PoolingOptions();
		poolingOptions
				.setConnectionsPerHost(HostDistance.LOCAL, 10, 25)
				.setConnectionsPerHost(HostDistance.REMOTE, 5, 10);
		builder.withPoolingOptions(poolingOptions);

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
	}


	@Override
	public Session getKeyspaceSession()
	{
		return m_cluster.connect(m_keyspace);
	}

	@Override
	public Session getSession()
	{
		return m_cluster.connect();
	}

	@Override
	public String getKeyspace()
	{
		return m_keyspace;
	}

	@Override
	public void close()
	{
		m_cluster.close();
	}
}
