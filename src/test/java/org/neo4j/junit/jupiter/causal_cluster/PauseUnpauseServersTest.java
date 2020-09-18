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

import org.junit.Assume;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

@NeedsCausalCluster
class PauseUnpauseServersTest {

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
		DriverUtils.verifyAllServersHaveConnectivity(cluster);
	}

	@ParameterizedTest()
	@ValueSource(ints = { 1, 35000 })
	void pauseOneTest(int pauseMilliseconds) throws Exception {
		// given
		Set<Neo4jServer> paused = cluster.pauseRandomServers(1);
		Instant deadline = Instant.now().plus(Duration.ofMillis(pauseMilliseconds));

		// then
		DriverUtils.verifyAllServersHaveConnectivity(cluster.getAllServersExcept(paused));

		Duration timeRemaining = Duration.between(Instant.now(), deadline);
		if (!timeRemaining.isNegative()) {
			Thread.sleep(timeRemaining.toMillis());
		} else {
			log.info(() -> "Unpausing server after " + Duration.ofMillis(pauseMilliseconds).plus(timeRemaining.abs()));
		}
		// check that we can still read log files even though the container is stopped
		assertThat(paused).allSatisfy(s -> assertThat(s.getDebugLog()).isNotEmpty());
		assertThat(paused).allSatisfy(s -> assertThat(s.getQueryLog()).isNotEmpty());

		// when
		Set<Neo4jServer> unpaused = cluster.unpauseServers(paused);
		cluster.waitForBoltOnAll(unpaused, Duration.ofMinutes(1));

		// then
		assertThat(unpaused).containsExactlyInAnyOrderElementsOf(paused);
		verifyClusterResumedSuccessfully();
	}

	@ParameterizedTest()
	@ValueSource(ints = { 1, 35000 })
	void pauseAllTest(int pauseMilliseconds) throws Exception {

		// ignore this for Neo4j versions below 4.2 - it will fail
		int[] neo4jVersion = DriverUtils.getNeo4jVersion(cluster);
		Assume.assumeTrue(neo4jVersion[0] >= 4);
		Assume.assumeTrue(neo4jVersion[1] >= 2);

		// given
		// I stop all the servers
		Set<Neo4jServer> pausedServers = cluster.pauseRandomServers(cluster.getAllServers().size());
		Instant deadline = Instant.now().plus(Duration.ofMillis(pauseMilliseconds));

		Duration timeRemaining = Duration.between(Instant.now(), deadline);
		if (!timeRemaining.isNegative()) {
			Thread.sleep(timeRemaining.toMillis());
		} else {
			log.info(() -> "Unpausing server after " + Duration.ofMillis(pauseMilliseconds).plus(timeRemaining.abs()));
		}

		// then
		assertThatThrownBy(() -> DriverUtils.verifyAnyServersHaveConnectivity(cluster))
			.satisfies(DriverUtils::hasSuppressedNeo4jException);

		// check that we can still read log files even though the container is stopped
		assertThat(pausedServers).allSatisfy(s -> assertThat(s.getDebugLog()).isNotEmpty());
		assertThat(pausedServers).allSatisfy(s -> assertThat(s.getQueryLog()).isNotEmpty());

		// when
		Set<Neo4jServer> unpaused = cluster.unpauseServers(pausedServers);
		cluster.waitForBoltOnAll(unpaused, Duration.ofMinutes(1));

		// then
		assertThat(unpaused).containsExactlyInAnyOrderElementsOf(pausedServers);
		verifyClusterResumedSuccessfully();

	}

	private void verifyClusterResumedSuccessfully() throws TimeoutException {
		// handling recovery from a pause is a bit strange because _immediately_ after the unpause everything functions
		// as it did before. But then (usually within seconds) the cores realise that various things have timed out and
		// the cores may then be unusable for a short period while they figure out what's going on.

		DriverUtils.retryOnceWithWaitForConnectivity(cluster, Duration.ofMinutes(5), () -> {
			DriverUtils.verifyContinuouslyAllServersHaveConnectivity(cluster, Duration.ofMinutes(2));
		});
	}

	/**
	 * This test is required because the TestContainer object can change in various ways when containers are
	 * stopped/started/killed and we want to ensure that does not mess with our ideas of server equality.
	 */
	@Test
	void serverComparisonTest() throws Exception {

		// given
		Set<Neo4jServer> allServersBefore = cluster.getAllServers();
		List<Integer> hashCodesBefore = allServersBefore.stream().map(Object::hashCode).collect(Collectors.toList());

		// pause a server
		Set<Neo4jServer> paused = cluster.pauseRandomServers(1);
		assertThat(allServersBefore).containsAll(paused);

		// check hash codes etc,
		Set<Neo4jServer> allServersAfter = cluster.getAllServers();
		List<Integer> hashCodesAfter = allServersAfter.stream().map(Object::hashCode).collect(Collectors.toList());
		assertThat(allServersAfter).containsExactlyInAnyOrderElementsOf(allServersBefore);
		assertThat(hashCodesAfter).containsExactlyInAnyOrderElementsOf(hashCodesBefore);

		// start the server
		cluster.unpauseServers(paused);
		cluster.waitForBoltOnAll(paused, Duration.ofMinutes(1));

		verifyClusterResumedSuccessfully();

		// then
		// equality and hash codes have not changed
		allServersAfter = cluster.getAllServers();
		hashCodesAfter = allServersAfter.stream().map(Object::hashCode).collect(Collectors.toList());
		assertThat(allServersAfter).containsExactlyInAnyOrderElementsOf(allServersBefore);
		assertThat(hashCodesAfter).containsExactlyInAnyOrderElementsOf(hashCodesBefore);
	}
}
