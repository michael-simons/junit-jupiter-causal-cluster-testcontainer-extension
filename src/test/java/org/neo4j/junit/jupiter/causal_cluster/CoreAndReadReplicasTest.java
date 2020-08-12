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
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.junit.jupiter.causal_cluster.Neo4jServer.Role;

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
		assertThat(servers.stream().map(Neo4jServer::getRole).distinct())
			.containsExactlyInAnyOrder(Role.CORE_SERVER, Role.REPLICA_SERVER);

		Map<Role, List<Neo4jServer>> serversByType = servers.stream()
			.collect(Collectors.groupingBy(Neo4jServer::getRole));
		assertThat(serversByType.get(Role.CORE_SERVER)).hasSize(3);
		assertThat(serversByType.get(Role.REPLICA_SERVER)).hasSize(2);

		assertThat(servers).allSatisfy(server -> {
			switch (server.getRole()) {
				case CORE_SERVER:
					assertRole(server, "LEADER", "FOLLOWER");
					break;
				case REPLICA_SERVER:
					assertRole(server, "READ_REPLICA");
					break;
				case UNKNOWN:
					Assertions.fail("Cluster must not contain an unknown server type");
			}
		});
	}

	static void assertRole(Neo4jServer server, String... expectedRoles) {

		try (Driver driver = GraphDatabase
			.driver(server.getDirectBoltUri(), AuthTokens.basic("neo4j", "password"), Config.builder().withLogging(
				Logging.console(Level.OFF)).build());
			Session session = driver.session()) {
			String role = session.readTransaction(tx -> tx.run("CALL dbms.cluster.role($databaseName)",
				Values.parameters("databaseName", "neo4j")).single().get(0).asString());

			assertThat(expectedRoles).describedAs("Server of type " + server.getRole() + " had role " + role)
				.contains(role);
		}
	}
}
