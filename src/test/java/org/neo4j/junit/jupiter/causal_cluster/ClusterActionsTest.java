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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This class contains the basic structure and helper functions that are useful for testing changes to Neo4j Clusters.
 */
public abstract class ClusterActionsTest {
	@CausalCluster
	static Collection<URI> clusterUris;
	@CausalCluster
	static Neo4jCluster cluster;

	protected static void verifyAllServersConnectivity(Collection<Neo4jServer> servers) {
		ClusterActionsTest.verifyAllServersBoltConnectivity(servers);
		ClusterActionsTest.verifyAllServersNeo4jConnectivity(servers);
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

	protected void verifyAllServersConnectivity() {
		ClusterActionsTest.verifyAllServersConnectivity(cluster.getAllServers());
	}

	protected void verifyAnyServerNeo4jConnectivity() throws Exception {
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
			// TODO aggregate exceptions properly
			throw exceptions.get(0);
		}
	}
}
