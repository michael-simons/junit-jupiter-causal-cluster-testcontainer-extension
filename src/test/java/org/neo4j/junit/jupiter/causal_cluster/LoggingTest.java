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

import java.net.URI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.junit.jupiter.causal_cluster.Neo4jServer.Role;
import org.testcontainers.containers.Neo4jContainer;

/**
 * @author Michael J. Simons
 * @soundtrack Helge Schneider - Heart Attack No. 1
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoggingTest {

	private Neo4jContainer<?> container = new Neo4jContainer<>(
		String.format("neo4j:%s-enterprise", CausalClusterExtension.DEFAULT_NEO4J_VERSION))
		.withoutAuthentication()
		.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes");

	@BeforeAll
	void startContainer() {
		container.start();
	}

	@Test
	void getDebugLogShouldWork() {

		Neo4jServer server = new DefaultNeo4jServer(container, URI.create(container.getBoltUrl()), Role.UNKNOWN);
		String debugLogs = server.getDebugLog();
		assertThat(debugLogs).doesNotContain("OCI runtime exec failed");
	}

	@Test
	void getQueryLogShouldWork() {

		String query = "MATCH (n) RETURN COUNT(n) as theNodeCount";

		Neo4jServer server = new DefaultNeo4jServer(container, URI.create(container.getBoltUrl()),
			Role.UNKNOWN);
		try (Driver driver = GraphDatabase.driver(server.getDirectBoltUri(), AuthTokens.none());
			Session session = driver.session()) {
			long nodeCount = session.readTransaction(tx -> tx.run(query).single().get(0).asLong());
			assertThat(nodeCount).isEqualTo(0L);
		}
		String queryLog = server.getQueryLog();
		assertThat(queryLog).contains(query);
	}

	@AfterAll
	void stopContainer() {
		container.stop();
	}

}
