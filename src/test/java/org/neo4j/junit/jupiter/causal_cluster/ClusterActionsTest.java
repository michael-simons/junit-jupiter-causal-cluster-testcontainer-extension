/*
 * Copyright (c) 2019-2020 "Neo4j,"
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

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.Neo4jException;

@NeedsCausalCluster()
public class ClusterActionsTest {

	private final static String NEO4J_UP_MESSAGE = "Remote interface available at";
	private final static String NEO4J_STOPPED_GRACEFULLY_MESSAGE = "Stopped.";

	@CausalCluster
	static Collection<URI> clusterUris;

	@CausalCluster
	static Neo4jCluster cluster;

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	@BeforeEach
	void before() throws Neo4jCluster.Neo4jTimeoutException {
		// make sure that bolt is ready to go before we start
		cluster.waitForBoltOnAll(cluster.getAllServers(), Duration.ofSeconds(10));
	}

	@AfterEach
	void after() throws Neo4jCluster.Neo4jTimeoutException {
		// make sure that nothing is broken before the cluster is handed over to the next test
		cluster.waitForBoltOnAll(cluster.getAllServers(), Duration.ofSeconds(10));
		verifyAllServersConnectivity();
	}

	@ParameterizedTest()
	@ValueSource(ints = { 1, 5000, 15000, 75000 })
	void stopOneTest(int stopMilliseconds) throws Neo4jCluster.Neo4jTimeoutException, InterruptedException {
		// given
		Set<Neo4jServer> stopped = cluster.stopRandomServers(1);
		Instant deadline = Instant.now().plus(Duration.ofMillis(stopMilliseconds));

		// then
		stopped.forEach(s -> assertThat(s.getLatestContainerLogs()).contains(NEO4J_STOPPED_GRACEFULLY_MESSAGE));
		verifyAllServersConnectivity(cluster.getAllServersExcept(stopped));

		Duration timeRemaining = Duration.between(Instant.now(), deadline);
		if (!timeRemaining.isNegative()) {
			Thread.sleep(timeRemaining.toMillis());
		} else {
			log.info(() -> "Restarting server after " + Duration.ofMillis(stopMilliseconds).plus(timeRemaining.abs()));
		}

		// when
		Set<Neo4jServer> started = cluster.startServers(stopped);
		cluster.waitForBoltOnAll(started, Duration.ofMinutes(1));

		// then
		assertThat(started).containsExactlyInAnyOrderElementsOf(stopped);
	}

	@Test
	void stopAllTest() throws Neo4jCluster.Neo4jTimeoutException {

		// given
		// I stop all the servers
		Set<Neo4jServer> allServers = cluster.stopRandomServers(cluster.getAllServers().size());

		// then
		allServers.forEach(s -> assertThat(s.getLatestContainerLogs()).contains(NEO4J_STOPPED_GRACEFULLY_MESSAGE));
		assertThatThrownBy(this::verifyAnyServerNeo4jConnectivity).isInstanceOf(Neo4jException.class);

		// when
		cluster.startServers(allServers);
		cluster.waitForBoltOnAll(allServers, Duration.ofMinutes(3));

		// then
		verifyAllServersConnectivity();
	}

	@Test
	void loggingWhenStoppedTest() throws Neo4jCluster.Neo4jTimeoutException {

		// given
		Set<Neo4jServer> stopped = cluster.stopRandomServers(1);
		String oldUpMessage = getNeo4jUpMessageLine(stopped.stream().findFirst().get());

		// then
		assertThatThrownBy(() -> waitForNeo4jUpMessage(stopped))
			.isInstanceOf(IllegalStateException.class);

		// when
		Set<Neo4jServer> started = cluster.startServers(stopped);
		cluster.waitForBoltOnAll(started, Duration.ofMinutes(3));
		waitForNeo4jUpMessage(started);

		// then
		started.forEach(s ->
			assertThat(s.getLatestContainerLogs())
				.contains(NEO4J_UP_MESSAGE)
				.doesNotContain(oldUpMessage)
		);
		started.forEach(s ->
			assertThat(s.getAllContainerLogs())
				.contains(NEO4J_UP_MESSAGE)
				.contains(oldUpMessage)
		);
		assertThatThrownBy(
			() -> cluster.waitForLogMessageOnAll(started, oldUpMessage, Duration.ofSeconds(10))
		).isInstanceOf(Neo4jCluster.Neo4jTimeoutException.class);
	}

	/**
	 * This test is required because the TestContainer object can change in various ways when containers are
	 * stopped/started/killed and we want to ensure that does not mess with our ideas of server equality.
	 */
	@Test
	void serverComparisonTest() throws Neo4jCluster.Neo4jTimeoutException {

		// given
		Set<Neo4jServer> allServersBefore = cluster.getAllServers();
		List<Integer> hashCodesBefore = allServersBefore.stream().map(Object::hashCode).collect(Collectors.toList());

		// stop a server
		Set<Neo4jServer> stopped = cluster.stopRandomServers(1);
		assertThat(allServersBefore).containsAll(stopped);

		// check hash codes etc,
		Set<Neo4jServer> allServersAfter = cluster.getAllServers();
		List<Integer> hashCodesAfter = allServersAfter.stream().map(Object::hashCode).collect(Collectors.toList());
		assertThat(allServersAfter).containsExactlyInAnyOrderElementsOf(allServersBefore);
		assertThat(hashCodesAfter).containsExactlyInAnyOrderElementsOf(hashCodesBefore);

		// start the server
		cluster.startServers(stopped);
		cluster.waitForBoltOnAll(stopped, Duration.ofMinutes(1));
		verifyAllServersConnectivity();

		// then
		// equality and hash codes have not changed
		allServersAfter = cluster.getAllServers();
		hashCodesAfter = allServersAfter.stream().map(Object::hashCode).collect(Collectors.toList());
		assertThat(allServersAfter).containsExactlyInAnyOrderElementsOf(allServersBefore);
		assertThat(hashCodesAfter).containsExactlyInAnyOrderElementsOf(hashCodesBefore);
	}

	private void waitForNeo4jUpMessage(Set<Neo4jServer> servers) throws Neo4jCluster.Neo4jTimeoutException {
		// Heuristic - allow one minute per server
		Duration timeout = Duration.ofMinutes(servers.size());
		cluster.waitForLogMessageOnAll(servers, NEO4J_UP_MESSAGE, timeout);
	}

	private void verifyAllServersConnectivity() {
		verifyAllServersConnectivity(cluster.getAllServers());
	}

	private void verifyAnyServerNeo4jConnectivity() throws Exception {
		// Verify connectivity
		List<Exception> exceptions = new ArrayList<>();
		for (URI clusterUri : clusterUris) {
			assertThat(clusterUri.toString()).startsWith("neo4j://");
			try (Driver driver = GraphDatabase.driver(
				clusterUri,
				AuthTokens.basic("neo4j", "password"),
				Config.defaultConfig()
			)) {
				driver.verifyConnectivity();

				// If any connection succeeds we return
				return;
			} catch (Exception e) {
				exceptions.add(e);
			}
		}

		// If we reach here they all failed. This assertion will fail and log the exceptions
		if (!exceptions.isEmpty()) {
			// TODO: aggregate exceptions properly
			throw exceptions.get(0);
		}
	}

	private static void verifyAllServersConnectivity(Collection<Neo4jServer> servers) {
		verifyAllServersBoltConnectivity(servers);
		verifyAllServersNeo4jConnectivity(servers);
	}

	private static void verifyAllServersBoltConnectivity(Collection<Neo4jServer> servers) {
		// Verify connectivity
		List<String> boltAddresses = servers.stream()
			.map(Neo4jServer::getDirectBoltUri)
			.map(URI::toString)
			.collect(Collectors.toList());
		NeedsCausalClusterTest.verifyConnectivity(boltAddresses);
	}

	private static void verifyAllServersNeo4jConnectivity(Collection<Neo4jServer> servers) {
		// Verify connectivity
		List<String> boltAddresses = servers.stream()
			.map(Neo4jServer::getURI)
			.map(URI::toString)
			.peek(s -> assertThat(s).startsWith("neo4j://"))
			.collect(Collectors.toList());
		NeedsCausalClusterTest.verifyConnectivity(boltAddresses);
	}

	private static String getNeo4jUpMessageLine(Neo4jServer server) {
		String oldLogs = server.getLatestContainerLogs();
		List<String> upMessages = Arrays.stream(oldLogs.split("\n")).filter(l -> l.contains(NEO4J_UP_MESSAGE))
			.collect(Collectors.toList());
		assertThat(upMessages).hasSize(1);
		return upMessages.get(0).trim();
	}
}
