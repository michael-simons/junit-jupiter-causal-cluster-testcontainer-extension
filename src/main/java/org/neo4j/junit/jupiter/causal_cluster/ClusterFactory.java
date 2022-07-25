/*
 * Copyright (c) 2019-2021 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.junit.jupiter.causal_cluster;

import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.neo4j.junit.jupiter.causal_cluster.Neo4jServer.Type;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.Neo4jLabsPlugin;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;

/**
 * Creates new cluster instances.
 *
 * @author Michael J. Simons
 * @author Andrew Jefferson
 */
final class ClusterFactory {

	static final int BALANCED_PORT = 8888;
	private static final int DEFAULT_BOLT_PORT = 7687;
	private static final String balancedAddress = "neo4j.balanced";
	public static final int MINIMUM_NUMBER_OF_CORE_SERVERS_REQUIRED = 3;
	public static final int MINIMUM_NUMBER_OF_REPLICA_SERVERS_REQUIRED = 0;

	private final Configuration configuration;


	private SocatContainer boltProxy;

	ClusterFactory(Configuration configuration) {
		this.configuration = configuration;
	}

	Neo4jCluster createCluster() {

		final int numberOfCoreServers = configuration.getNumberOfCoreServers();
		final int numberOfReplicaServers = configuration.getNumberOfReadReplicas();

		// Have some sanity check on the number of servers
		if (numberOfCoreServers < MINIMUM_NUMBER_OF_CORE_SERVERS_REQUIRED) {
			throw new IllegalArgumentException("A cluster needs at least 3 core servers.");
		}

		if (numberOfReplicaServers < MINIMUM_NUMBER_OF_REPLICA_SERVERS_REQUIRED) {
			throw new IllegalArgumentException("A cluster cannot have a negative number of read replicas.");
		}

		// Prepare one shared network for those containers
		final Network onNetwork = Network.newNetwork();

		// Setup a naming strategy and the initial discovery members
		final String withInitialDiscoveryMembers = iterateCoreServers()
			.map(n -> String.format("%s:5000", n.getValue()))
			.collect(Collectors.joining(","));

		// Prepare proxy to enter the cluster
		boltProxy = new SocatContainer().withNetwork(onNetwork);
		boltProxy.withTarget(BALANCED_PORT, balancedAddress, DEFAULT_BOLT_PORT);
		iterateCoreServers().forEach(member -> boltProxy
			.withTarget(member.getKey(), member.getValue(), DEFAULT_BOLT_PORT));
		iterateReplicaServers().forEach(member -> boltProxy
			.withTarget(member.getKey(), member.getValue(), DEFAULT_BOLT_PORT));

		// Start the proxy so that the exposed ports are available and we can get the mapped ones
		boltProxy.start();

		// First configure the core server container
		final List<DefaultNeo4jServer> coreServers = iterateCoreServers()
			.map(createCoreServer(onNetwork, withInitialDiscoveryMembers))
			.collect(Collectors.toList());
		startInParallel(coreServers);

		// Then the replicas configure the core server container
		final List<DefaultNeo4jServer> readReplicaServers = iterateReplicaServers()
			.map(createReadReplica(onNetwork, withInitialDiscoveryMembers))
			.collect(Collectors.toList());
		startInParallel(readReplicaServers);

		return new DefaultNeo4jCluster(boltProxy, coreServers, readReplicaServers, onNetwork);
	}

	private void startInParallel(List<DefaultNeo4jServer> servers) {

		final CountDownLatch latch = new CountDownLatch(servers.size());
		servers.forEach(instance -> CompletableFuture.runAsync(() -> {
			instance.unwrap().start();
			latch.countDown();
		}));

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private Stream<Map.Entry<Integer, String>> iterateCoreServers() {
		final IntFunction<String> generateInstanceName = i -> String.format("neo4j%d", i);

		return IntStream.rangeClosed(1, this.configuration.getNumberOfCoreServers())
			.mapToObj(i -> new AbstractMap.SimpleEntry<>(DEFAULT_BOLT_PORT + i - 1, generateInstanceName.apply(i)));
	}

	private Stream<Map.Entry<Integer, String>> iterateReplicaServers() {
		final IntFunction<String> generateInstanceName = i -> String.format("replica%d", i);

		return IntStream.rangeClosed(1, this.configuration.getNumberOfReadReplicas())
			.mapToObj(
				i -> new AbstractMap.SimpleEntry<>(DEFAULT_BOLT_PORT + configuration.getNumberOfCoreServers() + i - 1,
					generateInstanceName.apply(i)));
	}

	private Function<Map.Entry<Integer, String>, DefaultNeo4jServer> createCoreServer(
		final Network network,
		final String initialDiscoveryMembers) {

		return (portAndAlias) -> {
			Neo4jContainer<?> container = configureContainerForCoreServer(portAndAlias, network,
				initialDiscoveryMembers);
			return new DefaultNeo4jServer(container, getNeo4jUri(portAndAlias.getKey()), Type.CORE_SERVER);
		};
	}

	private Function<Map.Entry<Integer, String>, DefaultNeo4jServer> createReadReplica(
		final Network network,
		final String initialDiscoveryMembers) {

		return (portAndAlias) -> {
			Neo4jContainer<?> container = configureContainerForReplicaServerOn(portAndAlias, network,
				initialDiscoveryMembers);
			return new DefaultNeo4jServer(container, getNeo4jUri(portAndAlias.getKey()), Neo4jServer.Type.REPLICA_SERVER);
		};
	}

	private Neo4jContainer<?> configureContainerForCoreServer(
		final Map.Entry<Integer, String> portAndAlias,
		final Network network,
		final String initialDiscoveryMembers
	) {

		String numberOfCoreServers = Integer.toString(configuration.getNumberOfCoreServers());

		Neo4jContainer<?> coreContainer = newContainerWithCommonConfig(portAndAlias, network)
			.withNeo4jConfig("dbms.mode", "CORE")
			.withNeo4jConfig("causal_clustering.initial_discovery_members", initialDiscoveryMembers)
			.withNeo4jConfig("causal_clustering.minimum_core_cluster_size_at_formation", numberOfCoreServers)
			.withNeo4jConfig("causal_clustering.minimum_core_cluster_size_at_runtime", numberOfCoreServers)
			.withStartupTimeout(configuration.getStartupTimeout());

		coreContainer = configurePlugins(coreContainer);

		return configuration.applyCoreModifier(coreContainer);
	}

	private Neo4jContainer<?> configureContainerForReplicaServerOn(
		Map.Entry<Integer, String> portAndAlias,
		final Network network,
		final String initialDiscoveryMembers
	) {
		Neo4jContainer<?> readReplicaContainer = newContainerWithCommonConfig(portAndAlias, network)
			.withNeo4jConfig("dbms.mode", "READ_REPLICA")
			.withNeo4jConfig("causal_clustering.initial_discovery_members", initialDiscoveryMembers)
			.withStartupTimeout(configuration.getStartupTimeout());

		// See https://neo4j.com/docs/operations-manual/4.1/clustering/deploy/#causal-clustering-add-read-replica
		// But I assume a bug in the docs, needs to be confirmed though.
		if (configuration.is41()) {
			readReplicaContainer = readReplicaContainer
				.withNeo4jConfig("causal_clustering.discovery_members", initialDiscoveryMembers);
		}

		readReplicaContainer = configurePlugins(readReplicaContainer);

		return configuration.applyReadReplicaModifier(readReplicaContainer);
	}

	private Neo4jContainer<?> configurePlugins(Neo4jContainer<?> readReplicaContainer) {
		if (!configuration.getPlugins().isEmpty()) {
			readReplicaContainer = readReplicaContainer.withLabsPlugins(configuration.getPlugins().toArray(new Neo4jLabsPlugin[0]));
		}
		return readReplicaContainer;
	}

	private Neo4jContainer<?> newContainerWithCommonConfig(Map.Entry<Integer, String> portAndAlias, Network network) {

		boolean is35 = configuration.is35();
		String alias = portAndAlias.getValue();

		String advertisedAddress = String.format("%s:%d",
			boltProxy.getContainerIpAddress(),
			boltProxy.getMappedPort(portAndAlias.getKey())
		);

		return new Neo4jContainer<>(configuration.getImageName())
			.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
			.withAdminPassword(configuration.getPassword())
			.withNetwork(network)
			.withNetworkAliases(alias, balancedAddress)
			.withCreateContainerCmdModifier(cmd -> cmd.withHostName(alias))
			.withNeo4jConfig("dbms.memory.pagecache.size", configuration.getPagecacheSize() + "M")
			.withNeo4jConfig("dbms.memory.heap.initial_size", configuration.getInitialHeapSize() + "M")
			.withNeo4jConfig(is35 ? "dbms.connectors.default_listen_address" : "dbms.default_listen_address", "0.0.0.0")
			.withNeo4jConfig(is35 ? "dbms.connectors.default_advertised_address" : "dbms.default_advertised_address",
				alias)
			.withNeo4jConfig("dbms.connector.bolt.advertised_address", advertisedAddress);
	}

	private URI getNeo4jUri(int boltPort) {
		return URI.create(String.format(
			"neo4j://%s:%d",
			boltProxy.getContainerIpAddress(),
			boltProxy.getMappedPort(boltPort)
		));
	}
}
