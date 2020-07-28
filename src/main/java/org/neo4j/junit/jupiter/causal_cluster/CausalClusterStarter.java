package org.neo4j.junit.jupiter.causal_cluster;

import static java.util.stream.Collectors.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;

/**
 * This takes care of all the containers started.
 *
 * @author Michael J. Simons
 */
final class CausalClusterStarter {

	private static final int DEFAULT_BOLT_PORT = 7687;

	private final Configuration configuration;

	private SocatContainer boltProxy;

	public CausalClusterStarter(Configuration configuration) {
		this.configuration = configuration;
	}

	CausalCluster start() {

		final int numberOfCoreMembers = configuration.getNumberOfCoreMembers();

		// Prepare one shared network for those containers
		final Network network = Network.newNetwork();

		// Setup a naming strategy and the initial discovery members
		final String initialDiscoveryMembers = configuration.iterateCoreMembers()
			.map(n -> String.format("%s:5000", n.getValue()))
			.collect(joining(","));

		// Prepare proxy to enter the cluster
		boltProxy = new SocatContainer().withNetwork(network);
		configuration.iterateCoreMembers()
			.forEach(
				member -> boltProxy
					.withTarget(DEFAULT_BOLT_PORT + member.getKey(), member.getValue(), DEFAULT_BOLT_PORT));

		// Start the proxy so that the exposed ports are available and we can get the mapped ones
		boltProxy.start();

		final boolean is35 = configuration.getNeo4jVersion().startsWith("3.5");
		// Build the cluster
		final List<Neo4jContainer> cluster = configuration.iterateCoreMembers()
			.map(member -> new Neo4jContainer<>(configuration.getImageName())
				.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
				.withAdminPassword(configuration.getPassword())
				.withNetwork(network)
				.withNetworkAliases(member.getValue())
				.withCreateContainerCmdModifier(cmd -> cmd.withHostName(member.getValue()))
				.withNeo4jConfig("dbms.mode", "CORE")
				.withNeo4jConfig("dbms.memory.pagecache.size", configuration.getPagecacheSize() + "M")
				.withNeo4jConfig("dbms.memory.heap.initial_size", configuration.getInitialHeapSize() + "M")
				.withNeo4jConfig(is35 ? "dbms.connectors.default_listen_address" : "dbms.default_listen_address",
					"0.0.0.0")
				.withNeo4jConfig(
					is35 ? "dbms.connectors.default_advertised_address" : "dbms.default_advertised_address",
					member.getValue())
				.withNeo4jConfig("dbms.connector.bolt.advertised_address", String
					.format("%s:%d", boltProxy.getContainerIpAddress(),
						boltProxy.getMappedPort(DEFAULT_BOLT_PORT + member.getKey())))
				.withNeo4jConfig("causal_clustering.initial_discovery_members", initialDiscoveryMembers)
				.withNeo4jConfig("causal_clustering.minimum_core_cluster_size_at_formation",
					Integer.toString(numberOfCoreMembers))
				.withNeo4jConfig("causal_clustering.minimum_core_cluster_size_at_runtime",
					Integer.toString(numberOfCoreMembers))
				.withStartupTimeout(configuration.getStartupTimeout()))
			.collect(toList());

		// Start all of them in parallel
		final CountDownLatch latch = new CountDownLatch(numberOfCoreMembers);
		cluster.forEach(instance -> CompletableFuture.runAsync(() -> {
			instance.start();
			latch.countDown();
		}));

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return new CausalCluster(boltProxy, DEFAULT_BOLT_PORT, cluster);
	}
}
