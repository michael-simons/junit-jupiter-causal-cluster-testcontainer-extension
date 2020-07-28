/*
 * Copyright (c) 2019-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.junit.jupiter.causal_cluster;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NeedsCausalCluster
public class AllAnnotationsAppliedTest {

	@Neo4jUri
	static String clusterUriString;

	@Neo4jUri
	static URI clusterUri;

	@Neo4jUri
	static List<String> clusterUriListOfStrings;

	@Neo4jUri
	static Collection<String> clusterUriCollectionOfStrings;

	@Neo4jUri
	static List<URI> clusterUriListOfURIs;

	@Neo4jUri
	static Collection<URI> clusterUriCollectionOfURIs;

	@Neo4jCluster
	static Cluster cluster;

	@Test
	void nothingIsNull() {

		assertNotNull(clusterUriString);
		assertNotNull(clusterUri);
		assertNotNull(clusterUriListOfStrings);
		assertNotNull(clusterUriCollectionOfStrings);
		assertNotNull(clusterUriListOfURIs);
		assertNotNull(clusterUriCollectionOfURIs);
		assertNotNull(cluster);
	}

	@Test
	void clusterTest() throws URISyntaxException {
		Set<Neo4jCore> allCores = cluster.getAllCores();
		assertThat(allCores).hasSize(3);

		List<URI> allUris = allCores.stream().map(Neo4jCore::getNeo4jUri).collect(Collectors.toList());
		assertThat(allUris).hasSize(3);
		assertThat(allUris).containsExactlyInAnyOrderElementsOf(clusterUriCollectionOfURIs);

		// check that equality & hash code implementation doesn't mess up if we try adding the same cores to a set repeatedly
		for (Neo4jCore core : cluster.getAllCores()) {

			Neo4jCore newCore = new Neo4jCore(core.unwrap(), new URI(core.getNeo4jUri().toString()));

			assertThat(newCore.hashCode()).isEqualTo(core.hashCode());
			assertThat(newCore).isEqualTo(core);

			allCores.add(newCore);
		}
		assertThat(allCores).hasSize(3);
	}

	@Test
	void typesTest() {
		// Runtime annotation processing & reflection has the potential to let you assign any class to a field
		// (particularly with generics). So here we explicitly check the types at runtime

		assertThat(clusterUri).isInstanceOf(URI.class);
		assertThat(clusterUriString).isInstanceOf(String.class);

		assertThat(clusterUriListOfURIs.get(0)).isInstanceOf(URI.class);
		assertThat(clusterUriListOfStrings.get(0)).isInstanceOf(String.class);

		assertThat(cluster).isInstanceOf(Cluster.class);
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
		NeedsCausalClusterTest.verifyConnectivity(clusterUriListOfStrings);
	}

}
