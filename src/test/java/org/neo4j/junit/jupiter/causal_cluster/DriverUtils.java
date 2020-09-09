package org.neo4j.junit.jupiter.causal_cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class DriverUtils {
	static void verifyAllServersConnectivity(Collection<Neo4jServer> servers) {
		verifyAllServersBoltConnectivity(servers);
		verifyAllServersNeo4jConnectivity(servers);
	}

	static void verifyAllServersBoltConnectivity(Collection<Neo4jServer> servers) {
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

	static void verifyAllServersConnectivity(Neo4jCluster cluster) {
		verifyAllServersConnectivity(cluster.getAllServers());
	}

	static void verifyAnyServerNeo4jConnectivity(Neo4jCluster cluster) {
		verifyAnyServerNeo4jConnectivity(cluster.getURIs());
	}

	static void verifyAnyServerNeo4jConnectivity(Collection<URI> clusterUris) {
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
			throw new RuntimeException(exceptions.get(0));
		}
	}
}
