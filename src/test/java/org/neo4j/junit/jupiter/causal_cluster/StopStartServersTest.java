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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

@NeedsCausalCluster
class StopStartServersTest {

	private final static String NEO4J_UP_MESSAGE = "Remote interface available at";
	private final static String NEO4J_STOPPED_GRACEFULLY_MESSAGE = "Stopped.";
	private final Logger log = LoggerFactory.getLogger(this.getClass());

	@CausalCluster
	static Neo4jCluster cluster;

	@BeforeEach
	void before() throws Neo4jCluster.Neo4jTimeoutException {
		// make sure that bolt is ready to go before we start
		cluster.waitForBoltOnAll(cluster.getAllServers(), Duration.ofSeconds(10));
	}

	@AfterEach
	void after() throws Neo4jCluster.Neo4jTimeoutException {
		// make sure that nothing is broken before the cluster is handed over to the next test
		cluster.waitForBoltOnAll(cluster.getAllServers(), Duration.ofSeconds(10));
		DriverUtils.verifyAllServersHaveConnectivity(cluster);
	}

	@ParameterizedTest()
	@ValueSource(ints = { 1, 35000 })
	void stopOneTest(int stopMilliseconds) throws Exception {
		// given
		Set<Neo4jServer> stopped = cluster.stopRandomServers(1);
		Instant deadline = Instant.now().plus(Duration.ofMillis(stopMilliseconds));

		// then
		assertThat(stopped).hasSize(1).first().satisfies(logsSinceStartContainingStoppedMessage());
		DriverUtils.verifyAllServersHaveConnectivity(cluster.getAllServersExcept(stopped));

		Duration timeRemaining = Duration.between(Instant.now(), deadline);
		if (!timeRemaining.isNegative()) {
			Thread.sleep(timeRemaining.toMillis());
		} else {
			log.info(() -> "Restarting server after " + Duration.ofMillis(stopMilliseconds).plus(timeRemaining.abs()));
		}

		// when
		Set<Neo4jServer> started = cluster.startServers(stopped);
		cluster.waitForBoltOnAll(started, Duration.ofMinutes(1));
		DriverUtils.verifyEventuallyAllServersHaveConnectivity(cluster, Duration.ofMinutes(1));

		// then
		assertThat(started).containsExactlyInAnyOrderElementsOf(stopped);
	}

	private static Consumer<Neo4jServer> logsSinceStartContainingStoppedMessage() {
		return s -> assertThat(s.getContainerLogsSinceStart()).contains(NEO4J_STOPPED_GRACEFULLY_MESSAGE);
	}

	@Test
	void stopAllTest() throws Neo4jCluster.Neo4jTimeoutException, TimeoutException {

		// given
		// I stop all the servers
		Set<Neo4jServer> stoppedServers = cluster.stopRandomServers(cluster.getAllServers().size());

		// then
		assertThat(stoppedServers).allSatisfy(logsSinceStartContainingStoppedMessage());
		assertThatThrownBy(() -> DriverUtils.verifyAnyServersHaveConnectivity(cluster))
			.satisfies(DriverUtils::hasSuppressedNeo4jException);

		// check that we can still read log files even though the container is stopped
		assertThat(stoppedServers).allSatisfy(s -> assertThat(s.getDebugLog()).isNotEmpty());
		assertThat(stoppedServers).allSatisfy(s -> assertThat(s.getQueryLog()).isNotEmpty());

		// when
		cluster.startServers(stoppedServers);
		cluster.waitForBoltOnAll(stoppedServers, Duration.ofMinutes(3));

		// then
		DriverUtils.verifyEventuallyAllServersHaveConnectivity(cluster, Duration.ofMinutes(5));
	}

	@Test
	void loggingWhenStoppedTest() throws Neo4jCluster.Neo4jTimeoutException {

		// given
		Set<Neo4jServer> stopped = cluster.stopRandomServers(1);
		String oldUpMessage = getNeo4jUpMessageLine(stopped.stream().findFirst().get());

		// then
		assertThatIllegalStateException().isThrownBy(() -> waitForNeo4jUpMessage(stopped));

		// when
		Set<Neo4jServer> started = cluster.startServers(stopped);
		cluster.waitForBoltOnAll(started, Duration.ofMinutes(3));
		waitForNeo4jUpMessage(started);

		// then
		assertThat(started).allSatisfy(s -> {
			assertThat(s.getContainerLogsSinceStart())
				.contains(NEO4J_UP_MESSAGE)
				.doesNotContain(oldUpMessage);
			assertThat(s.getContainerLogs())
				.contains(NEO4J_UP_MESSAGE)
				.contains(NEO4J_STOPPED_GRACEFULLY_MESSAGE)
				.contains(oldUpMessage);
		});
		assertThatThrownBy(
			() -> cluster.waitForLogMessageOnAll(started, oldUpMessage, Duration.ofSeconds(10))
		).isInstanceOf(Neo4jCluster.Neo4jTimeoutException.class);
	}

	/**
	 * This test is required because the TestContainer object can change in various ways when containers are
	 * stopped/started/killed and we want to ensure that does not mess with our ideas of server equality.
	 */
	@Test
	void serverComparisonTest() throws Neo4jCluster.Neo4jTimeoutException, TimeoutException {

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
		DriverUtils.verifyEventuallyAllServersHaveConnectivity(cluster, Duration.ofMinutes(1));

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

	private static String getNeo4jUpMessageLine(Neo4jServer server) {
		String oldLogs = server.getContainerLogsSinceStart();
		List<String> upMessages = Arrays.stream(oldLogs.split("\n")).filter(l -> l.contains(NEO4J_UP_MESSAGE))
			.collect(Collectors.toList());
		assertThat(upMessages).hasSize(1);
		return upMessages.get(0).trim();
	}
}
