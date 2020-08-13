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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.junit.jupiter.causal_cluster.Neo4jServer.Type;

/**
 * @author Michael J. Simons
 * @soundtrack Juse Ju - Millennium
 */
class CoreAndReadReplicasTest {

	@Nested @TestInstance(TestInstance.Lifecycle.PER_CLASS)
	@NeedsCausalCluster(numberOfReadReplicas = 2)
	class WithDefaultImage {

		@CausalCluster
		Neo4jCluster cluster;

		@Test
		void shouldProvideCoreAndReadReplicas() {

			Set<Neo4jServer> servers = cluster.getAllServers();
			assertCorrectNumberOfCoreAndReadReplicaServers(servers);
		}
	}

	@Nested @TestInstance(TestInstance.Lifecycle.PER_CLASS)
	@NeedsCausalCluster(numberOfReadReplicas = 2, neo4jVersion = "4.1")
	class With41 {

		@CausalCluster
		Neo4jCluster cluster;

		@Test
		void shouldProvideCoreAndReadReplicas() {

			Set<Neo4jServer> servers = cluster.getAllServers();
			assertCorrectNumberOfCoreAndReadReplicaServers(servers);
		}
	}

	static void assertCorrectNumberOfCoreAndReadReplicaServers(Set<Neo4jServer> servers) {
		assertThat(servers.stream().map(Neo4jServer::getType).distinct())
			.containsExactlyInAnyOrder(Neo4jServer.Type.CORE_SERVER, Neo4jServer.Type.REPLICA_SERVER);

		Map<Neo4jServer.Type, List<Neo4jServer>> serversByType = servers.stream()
			.collect(Collectors.groupingBy(Neo4jServer::getType));
		assertThat(serversByType.get(Neo4jServer.Type.CORE_SERVER)).hasSize(3);
		assertThat(serversByType.get(Type.REPLICA_SERVER)).hasSize(2);

		assertThat(servers).allSatisfy(server -> {
			switch (server.getType()) {
				case CORE_SERVER:
					assertThat(server.getRolesFor("neo4j")).isSubsetOf("LEADER", "FOLLOWER");
					break;
				case REPLICA_SERVER:
					assertThat(server.getRolesFor("neo4j")).isSubsetOf("READ_REPLICA");
					break;
				case UNKNOWN:
					Assertions.fail("Cluster must not contain an unknown server type");
			}
		});
	}
}
