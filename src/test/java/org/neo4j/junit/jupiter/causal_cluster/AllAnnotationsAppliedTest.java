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

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@NeedsCausalCluster(numberOfReadReplicas = 1)
class AllAnnotationsAppliedTest {

	@CausalCluster
	static String clusterUriString;

	@CausalCluster
	static URI clusterUri;

	@CausalCluster
	static List<String> clusterUriListOfStrings;

	@CausalCluster
	static Collection<String> clusterUriCollectionOfStrings;

	@CausalCluster
	static List<URI> clusterUriListOfURIs;

	@CausalCluster
	static Collection<URI> clusterUriCollectionOfURIs;

	@CausalCluster
	static Neo4jCluster cluster;

	private static final String coreMessage = "I am a core hear me roar";
	private static final MountableFile coreScript = MountableFile
		.forHostPath(createTemporaryBashScript(String.format("echo '%s'\n", coreMessage)).getAbsolutePath());

	/**
	 * This approach can be used to do things like modify neo4j configuration or add a plugin.
	 * In this case we're using the EXTENSION_SCRIPT functionality of the docker container because it's easier and
	 * quicker to test than adding a plugin or modifying neo4j configuration.
	 *
	 * @param input a not-yet-started Neo4jContainer object configured for the cluster
	 * @return a modified Neo4jContainer object
	 */
	@CoreModifier
	static Neo4jContainer<?> coreModifier(Neo4jContainer<?> input) {
		String extensionScriptPath = "/extension.sh";

		return input
			.withCopyFileToContainer(coreScript, extensionScriptPath)
			.withEnv("EXTENSION_SCRIPT", extensionScriptPath);
	}

	private static final String rrMessage = "I am a read replica hear me roar";
	private static final MountableFile rrScript = MountableFile
		.forHostPath(createTemporaryBashScript(String.format("echo '%s'\n", rrMessage)).getAbsolutePath());

	/**
	 * This approach can be used to do things like modify neo4j configuration or add a plugin.
	 * In this case we're using the EXTENSION_SCRIPT functionality of the docker container because it's easier and
	 * quicker to test than adding a plugin or modifying neo4j configuration.
	 *
	 * @param input a not-yet-started Neo4jContainer object configured for the cluster
	 * @return a modified Neo4jContainer object
	 */
	@ReadReplicaModifier
	static Neo4jContainer<?> rrModifier(Neo4jContainer<?> input) {
		String extensionScriptPath = "/extension.sh";

		return input
			.withCopyFileToContainer(rrScript, extensionScriptPath)
			.withEnv("EXTENSION_SCRIPT", extensionScriptPath);
	}

	@Test
	void nothingIsNull() {

		assertThat(clusterUriString).isNotNull();
		assertThat(clusterUri).isNotNull();
		assertThat(clusterUriListOfStrings).isNotNull();
		assertThat(clusterUriCollectionOfStrings).isNotNull();
		assertThat(clusterUriListOfURIs).isNotNull();
		assertThat(clusterUriCollectionOfURIs).isNotNull();
		assertThat(cluster).isNotNull();
	}

	@Test
	void clusterTest() {

		assertThat(cluster.getURIs())
			.containsExactlyInAnyOrderElementsOf(clusterUriCollectionOfURIs);

		Set<Neo4jServer> allServers = cluster.getAllServers();
		assertThat(allServers).hasSize(4);

		Set<Neo4jServer> allCoreServers = cluster.getAllServersOfType(Neo4jServer.Type.CORE_SERVER);
		assertThat(allCoreServers).hasSize(3);

		List<URI> allCoreUris = allCoreServers.stream().map(Neo4jServer::getURI).collect(Collectors.toList());
		assertThat(allCoreUris).hasSize(3);
		assertThat(allCoreUris).containsExactlyInAnyOrderElementsOf(clusterUriCollectionOfURIs);
	}

	@Test
	void serverComparisonTest() throws URISyntaxException, IllegalAccessException {
		// Get the container. 🤠
		Field field = ReflectionUtils
			.findFields(DefaultNeo4jServer.class, f -> "container".equals(f.getName()),
				ReflectionUtils.HierarchyTraversalMode.TOP_DOWN).get(0);
		field.setAccessible(true);

		// check that equality & hash code implementation doesn't mess up if we try adding the same servers to a set repeatedly
		Set<Neo4jServer> allServers = cluster.getAllServers();
		assertThat(allServers).hasSize(4);

		for (Neo4jServer server : allServers) {

			DefaultNeo4jServer newServer = new DefaultNeo4jServer(
				(Neo4jContainer<?>) field.get(server), new URI(server.getURI().toString()), server.getType());

			assertThat(newServer.hashCode()).isEqualTo(server.hashCode());
			assertThat(newServer).isEqualTo(server);

			allServers.add(newServer);
		}
		assertThat(allServers).hasSize(4);
	}

	@Test
	void typesTest() {

		// Runtime annotation processing & reflection has the potential to let you assign any class to a field
		// (particularly with generics). So here we explicitly check the types at runtime

		assertThat(clusterUri).isInstanceOf(URI.class);
		assertThat(clusterUriString).isInstanceOf(String.class);

		assertThat(clusterUriListOfURIs.get(0)).isInstanceOf(URI.class);
		assertThat(clusterUriListOfStrings.get(0)).isInstanceOf(String.class);

		assertThat(cluster).isInstanceOf(Neo4jCluster.class);
	}

	@Test
	void singleValuesExistInCollection() {

		assertThat(clusterUriCollectionOfStrings).containsOnlyOnce(clusterUriString);
		assertThat(clusterUriCollectionOfURIs).containsOnlyOnce(clusterUri);
	}

	@Test
	void collectionAndListsContainSameElements() {

		assertThat(clusterUriCollectionOfStrings).containsExactlyInAnyOrderElementsOf(clusterUriListOfStrings);
		assertThat(clusterUriCollectionOfURIs).containsExactlyInAnyOrderElementsOf(clusterUriListOfURIs);
	}

	@Test
	void collectionsHaveExpectedSize() {

		assertThat(clusterUriCollectionOfStrings).hasSize(3);
		assertThat(clusterUriCollectionOfURIs).hasSize(3);

	}

	@Test
	void stringAndUriListsContainSameElements() {

		List<String> urisToStrings = clusterUriListOfURIs.stream().map(URI::toString).collect(Collectors.toList());
		assertThat(urisToStrings).containsExactlyInAnyOrderElementsOf(clusterUriListOfStrings);

	}

	@Test
	void verifyConnectivity() {
		// Verify connectivity
		DriverUtils.verifyConnectivity(clusterUriListOfStrings);
	}

	@Test
	void verifyConnectivityReadReplica() {
		// Verify connectivity
		List<String> readReplicaAddresses = cluster.getAllServersOfType(Neo4jServer.Type.REPLICA_SERVER)
			.stream()
			.map(s -> s.getDirectBoltUri().toString())
			.collect(Collectors.toList());
		DriverUtils.verifyConnectivity(readReplicaAddresses);
	}

	@Test
	void verifyContainerModifiers() {
		// Verify connectivity

		Set<Neo4jServer> cores = cluster.getAllServersOfType(Neo4jServer.Type.CORE_SERVER);
		assertThat(cores).hasSize(3);
		cores.forEach(s -> assertThat(s.getContainerLogs()).contains(coreMessage));

		Set<Neo4jServer> readReplicas = cluster.getAllServersOfType(Neo4jServer.Type.REPLICA_SERVER);
		assertThat(readReplicas).hasSize(1);
		readReplicas.forEach(s -> assertThat(s.getContainerLogs()).contains(rrMessage));
	}

	private static File createTemporaryBashScript(final String bash) {
		try {
			File f = File.createTempFile("extension", ".sh");

			try (FileOutputStream out = new FileOutputStream(f)) {

				out.write(bash.getBytes());
			}
			return f;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
