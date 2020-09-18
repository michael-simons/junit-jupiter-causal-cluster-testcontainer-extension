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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.Neo4jContainer;

@NeedsCausalCluster
class IsolateUnisolateServersTest {

	private final static String NODE_UNREACHABLE_MESSAGE = "Marking node(s) as UNREACHABLE";
	private final static String CLUSTER_JOINED_MESSAGE = "ClusterJoiningActor] Join successful, exiting";

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	@CausalCluster
	static Neo4jCluster cluster;

	@CoreModifier
	static Neo4jContainer configure(Neo4jContainer core) {
		return core.withNeo4jConfig("dbms.logs.debug.level", "DEBUG")
			.withNeo4jConfig("causal_clustering.middleware.logging.level", "DEBUG");
	}

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
	void isolateOneTest(int stopMilliseconds) throws Exception {

		// given
		Set<Neo4jServer> isolated = cluster.isolateRandomServers(1);
		Instant deadline = Instant.now().plus(Duration.ofMillis(stopMilliseconds));

		Neo4jServer isolatedServer = isolated.stream().findFirst().get();
		long logPositionBefore = isolatedServer.getDebugLogPosition();

		// when
		Duration timeRemaining = Duration.between(Instant.now(), deadline);
		if (!timeRemaining.isNegative()) {
			Thread.sleep(timeRemaining.toMillis());
		} else {
			log.info(() -> "Attaching server after " + Duration.ofMillis(stopMilliseconds).plus(timeRemaining.abs()));
		}

		Set<Neo4jServer> unisolated = cluster.unisolateServers(isolated);
		cluster.waitForBoltOnAll(unisolated, Duration.ofMinutes(1));

		// then
		assertThat(unisolated).containsExactlyInAnyOrderElementsOf(isolated);
		DriverUtils.retryOnceWithWaitForConnectivity(cluster, Duration.ofMinutes(2), () -> {
			DriverUtils.verifyContinuouslyAllServersHaveConnectivity(cluster, Duration.ofMinutes(1));
		});

		if (stopMilliseconds > 10000) {
			assertThat(isolatedServer.getDebugLogFrom(logPositionBefore))
				.contains(NODE_UNREACHABLE_MESSAGE)
				.contains(CLUSTER_JOINED_MESSAGE);
		} else {
			assertThat(isolatedServer.getDebugLogFrom(logPositionBefore))
				.doesNotContain(NODE_UNREACHABLE_MESSAGE)
				.doesNotContain(CLUSTER_JOINED_MESSAGE);
		}
	}

	@Test
	void isolateAllTest() throws Neo4jCluster.Neo4jTimeoutException, TimeoutException {

		// given
		// I isolate all the servers
		Set<Neo4jServer> isolated = cluster.isolateRandomServers(cluster.getAllServers().size());

		// then
		assertThatThrownBy(() -> DriverUtils.verifyAnyServersHaveConnectivity(cluster))
			.satisfies(DriverUtils::hasSuppressedNeo4jException);

		// when
		cluster.unisolateServers(isolated);
		cluster.waitForBoltOnAll(isolated, Duration.ofMinutes(3));

		// then
		DriverUtils.verifyEventuallyAllServersHaveConnectivity(cluster, Duration.ofMinutes(5));
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
		Set<Neo4jServer> isolated = cluster.isolateRandomServers(1);
		assertThat(allServersBefore).containsAll(isolated);

		// check hash codes etc,
		Set<Neo4jServer> allServersAfter = cluster.getAllServers();
		List<Integer> hashCodesAfter = allServersAfter.stream().map(Object::hashCode).collect(Collectors.toList());
		assertThat(allServersAfter).containsExactlyInAnyOrderElementsOf(allServersBefore);
		assertThat(hashCodesAfter).containsExactlyInAnyOrderElementsOf(hashCodesBefore);

		// start the server
		cluster.unisolateServers(isolated);
		cluster.waitForBoltOnAll(isolated, Duration.ofMinutes(1));
		DriverUtils.verifyEventuallyAllServersHaveConnectivity(cluster, Duration.ofMinutes(1));

		// then
		// equality and hash codes have not changed
		allServersAfter = cluster.getAllServers();
		hashCodesAfter = allServersAfter.stream().map(Object::hashCode).collect(Collectors.toList());
		assertThat(allServersAfter).containsExactlyInAnyOrderElementsOf(allServersBefore);
		assertThat(hashCodesAfter).containsExactlyInAnyOrderElementsOf(hashCodesBefore);
	}

}
