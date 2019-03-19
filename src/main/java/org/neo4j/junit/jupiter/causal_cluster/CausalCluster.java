/*
 * Copyright (c) 2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.junit.jupiter.causal_cluster;

import static java.util.stream.Collectors.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

/**
 * This takes care of all the containers started.
 *
 * @author Michael J. Simons
 */
class CausalCluster implements CloseableResource {

	private static final int DEFAULT_BOLT_PORT = 7687;

	static CausalCluster start(Configuration configuration) {

		final int numberOfCoreMembers = configuration.getNumberOfCoreMembers();

		// Currently needed as a whole new waiting strategy due to a bug in test containers
		WaitStrategy waitForBolt = new LogMessageWaitStrategy()
			.withRegEx(String.format(".*Bolt enabled on 0\\.0\\.0\\.0:%d\\.\n", DEFAULT_BOLT_PORT))
			.withStartupTimeout(configuration.getStartupTimeout());

		// Prepare one shared network for those containers
		Network network = Network.newNetwork();

		// Setup a naming strategy and the initial discovery members

		final String initialDiscoveryMembers = configuration.iterateCoreMembers()
			.map(n -> String.format("%s:5000", n))
			.collect(joining(","));

		// Prepare proxys as sidecars
		Map<String, GenericContainer> sidecars = configuration.iterateCoreMembers()
			.collect(toMap(
				Function.identity(),
				name -> new GenericContainer("alpine/socat:1.0.3")
					.withNetwork(network)
					// Expose the default bolt port on the sidecar
					.withExposedPorts(DEFAULT_BOLT_PORT)
					// And redirect that port to the corresponding Neo4j instance
					.withCommand(String
						.format("tcp-listen:%d,fork,reuseaddr tcp-connect:%s:%1$d", DEFAULT_BOLT_PORT, name))
			));

		// Start the sidecars so that the exposed ports are available
		sidecars.values().forEach(GenericContainer::start);

		Function<GenericContainer, String> getProxyUrl = instance ->
			String.format("%s:%d", instance.getContainerIpAddress(), instance.getMappedPort(DEFAULT_BOLT_PORT));

		// Build the cluster
		List<Neo4jContainer> cluster = configuration.iterateCoreMembers()
			.map(name -> new Neo4jContainer<>(configuration.getImageName())
				.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
				.withAdminPassword(configuration.getPassword())
				.withNetwork(network)
				.withNetworkAliases(name)
				.withNeo4jConfig("dbms.mode", "CORE")
				.withNeo4jConfig("dbms.memory.pagecache.size", configuration.getPagecacheSize() + "M")
				.withNeo4jConfig("dbms.memory.heap.initial_size", configuration.getInitialHeapSize() + "M")
				.withNeo4jConfig("dbms.connectors.default_listen_address", "0.0.0.0")
				.withNeo4jConfig("dbms.connectors.default_advertised_address", name)
				.withNeo4jConfig("dbms.connector.bolt.advertised_address", getProxyUrl.apply(sidecars.get(name)))
				.withNeo4jConfig("causal_clustering.initial_discovery_members", initialDiscoveryMembers)
				.waitingFor(waitForBolt))
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

		return new CausalCluster(cluster, sidecars.values());
	}

	private final Collection<Neo4jContainer> clusterMembers;
	private final Collection<GenericContainer> sidecars;

	public CausalCluster(Collection<Neo4jContainer> clusterMembers,
		Collection<GenericContainer> sidecars) {
		this.clusterMembers = clusterMembers;
		this.sidecars = sidecars;
	}

	public URI getURI() {
		return this.sidecars.stream().findAny()
			.map(instance -> String.format("bolt+routing://%s:%d", instance.getContainerIpAddress(),
				instance.getMappedPort(DEFAULT_BOLT_PORT)))
			.map(uri -> {
				try {
					return new URI(uri);
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
			})
			.orElseThrow(() -> new IllegalStateException("No sidecar as entrypoint into the cluster available."));
	}

	@Override
	public void close() {
		sidecars.forEach(GenericContainer::stop);
		clusterMembers.forEach(GenericContainer::stop);
	}
}
