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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

@NeedsCausalCluster(numberOfReadReplicas = 1, createMultipleClusters = true)
class MultipleClustersTest {
	@CausalCluster
	static Neo4jCluster clusterOne;

	@CausalCluster
	static Neo4jCluster clusterTwo;

	@CausalCluster
	static Neo4jCluster clusterThree;

	private Stream<Neo4jCluster> getClusters() {
		return Stream.of(clusterOne, clusterTwo, clusterThree);
	}

	@Test
	void nothingIsNull() {
		assertThat(clusterOne).isNotNull();
		assertThat(clusterTwo).isNotNull();
		assertThat(clusterThree).isNotNull();
	}

	@Test
	void nothingIsTheSame() {
		assertThat(clusterOne).isNotEqualTo(clusterTwo);
		assertThat(clusterTwo).isNotEqualTo(clusterThree);
		assertThat(clusterThree).isNotEqualTo(clusterOne);

		assertThat(clusterOne.getAllServers()).doesNotContainAnyElementsOf(clusterTwo.getAllServers());
		assertThat(clusterTwo.getAllServers()).doesNotContainAnyElementsOf(clusterThree.getAllServers());
		assertThat(clusterThree.getAllServers()).doesNotContainAnyElementsOf(clusterOne.getAllServers());
	}

	@Test
	void verifyConnectivity() {
		// Verify connectivity
		getClusters().flatMap(c -> c.getURIs().stream())
			.parallel().forEach(DriverUtils::verifyConnectivity);
	}

	@Test
	void verifyConnectivityCores() {
		// Verify connectivity
		getClusters().flatMap(c -> c.getAllServersOfType(Neo4jServer.Type.CORE_SERVER).stream())
			.map(Neo4jServer::getDirectBoltUri)
			.parallel().forEach(DriverUtils::verifyConnectivity);
	}

	@Test
	void verifyConnectivityReadReplica() {
		// Verify connectivity
		getClusters().flatMap(c -> c.getAllServersOfType(Neo4jServer.Type.REPLICA_SERVER).stream())
			.map(Neo4jServer::getDirectBoltUri)
			.parallel().forEach(DriverUtils::verifyConnectivity);
	}
}
