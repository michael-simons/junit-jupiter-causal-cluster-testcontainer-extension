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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Testing the test.
 *
 * @author Michael J. Simons
 */
class NeedsCausalClusterTest {

	public static final String ENGINE_ID = "junit-jupiter";

	@Nested
	class ValidUses {

		@Test
		void shouldWorkWithIntendedUsage() {
			Events testEvents = EngineTestKit.engine(ENGINE_ID)
				.selectors(
					selectClass(StaticClusterUriFieldOnPerMethodLifecycleTest.class),
					selectClass(StaticClusterFieldOnPerMethodLifecycleTest.class),
					selectClass(InstanceUriFieldInPerClassLifecycleTest.class),
					selectClass(InstanceCollectionFieldInPerClassLifecycleTest.class),
					selectClass(InstanceClusterFieldInPerClassLifecycleTest.class),
					selectClass(NestedClassWithAllNeededAnnotationsTest.class)
				)
				.execute().testEvents();

			assertThat(testEvents.failed().stream().collect(Collectors.toList()))
				.hasSize(0);
			assertThat(testEvents.succeeded().stream().collect(Collectors.toList()))
				.hasSize(6);
		}

		@Test
		void shouldNotStartMultipleClusters() {
			Events testEvents = EngineTestKit.engine(ENGINE_ID)
				.selectors(
					selectClass(MultipleCollectionInjectionPointsPerMethodLifeCycleTest.class),
					selectClass(MultipleUriInjectionPointsPerMethodLifeCycleTest.class),
					selectClass(MultipleClusterInjectionPointsPerMethodLifeCycleTest.class),
					selectClass(MultipleInjectionPointsDifferentTypePerMethodLifeCycleTest.class),
					selectClass(MultipleCollectionInjectionPointsPerClassLifeCycleTest.class),
					selectClass(MultipleUriInjectionPointsPerClassLifeCycleTest.class),
					selectClass(MultipleClusterInjectionPointsPerClassLifeCycleTest.class),
					selectClass(MultipleInjectionPointsDifferentTypePerClassLifeCycleTest.class)
				)
				.execute().testEvents();

			assertThat(testEvents.failed().stream().collect(Collectors.toList()))
				.hasSize(0);
			assertThat(testEvents.succeeded().stream().collect(Collectors.toList()))
				.hasSize(8);
		}
	}

	@Nested
	class InvalidUses {

		@Test
		void shouldNotWorkWithInvalidUsage() {
			Events testEvents = EngineTestKit.engine(ENGINE_ID)
				.selectors(
					selectClass(MissingExtensionTest.class),
					selectClass(MissingInjectionPoint.class),
					selectClass(LifecyleOnOuterClass.class))
				.execute().testEvents();

			assertThat(testEvents.succeeded().stream().collect(Collectors.toList()))
				.hasSize(0);
			assertThat(testEvents.failed().stream().collect(Collectors.toList()))
				.hasSize(3);
		}

		@Test
		void shouldCheckTypeOfInjectionPoint() {
			Events testEvents = EngineTestKit.engine(ENGINE_ID)
				.selectors(
					selectClass(WrongUriExtensionPoint.class),
					selectClass(WrongUriCollectionExtensionPoint.class),
					selectClass(WrongClusterExtensionPoint.class),
					selectClass(MixedUpClusterExtensionPoint.class),
					selectClass(MixedUpUriExtensionPoint.class)
				)
				.execute().testEvents();

			assertThat(testEvents.started().stream().collect(Collectors.toList()))
				.hasSize(0);
		}
	}

	static void verifyConnectivity(Cluster cluster) {
		List<String> clusterUris = cluster.getAllCores().stream()
			.map(c -> c.getNeo4jUri().toString())
			.collect(Collectors.toList());

		verifyConnectivity(clusterUris);
	}

	static void verifyConnectivity(Collection<String> clusterUris) {
		for (String clusterUri : clusterUris) {
			verifyConnectivity(clusterUri);
		}
	}

	static void verifyConnectivity(String uri) {

		try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic("neo4j", "password"))) {
			driver.verifyConnectivity();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	@NeedsCausalCluster
	static class StaticClusterUriFieldOnPerMethodLifecycleTest {

		@Neo4jUri
		static String clusterUri;

		@Test
		void aTest() {

			assertNotNull(clusterUri);
			verifyConnectivity(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class StaticClusterFieldOnPerMethodLifecycleTest {

		@Neo4jCluster
		static Cluster cluster;

		@Test
		void aTest() {

			assertNotNull(cluster);
			verifyConnectivity(cluster);
		}
	}

	@NeedsCausalCluster
	static class MultipleUriInjectionPointsPerMethodLifeCycleTest {

		@Neo4jUri
		static String clusterUri1;

		@Neo4jUri
		static String clusterUri2;

		@Neo4jUri
		static String clusterUri3;

		@Neo4jUri
		static String clusterUri4;

		@Neo4jUri
		static String clusterUri5;

		@Neo4jUri
		static String clusterUri6;

		@Test
		void aTest() {

			String[] injectedUris = new String[] {
				clusterUri1, clusterUri2, clusterUri3, clusterUri4, clusterUri5, clusterUri6 };

			List<String> distinctUris = Arrays.stream(injectedUris).distinct().collect(Collectors.toList());

			assertThat(distinctUris.size()).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(3);

			verifyConnectivity(distinctUris);
		}
	}

	@NeedsCausalCluster
	static class MultipleCollectionInjectionPointsPerMethodLifeCycleTest {

		@Neo4jUri
		static Collection<String> clusterUris1;

		@Neo4jUri
		static Collection<String> clusterUris2;

		@Test
		void aTest() {

			assertThat(clusterUris1).containsExactlyInAnyOrderElementsOf(clusterUris2);
			verifyConnectivity(clusterUris1);
		}
	}

	@NeedsCausalCluster
	static class MultipleClusterInjectionPointsPerMethodLifeCycleTest {

		@Neo4jCluster
		static Cluster cluster1;

		@Neo4jCluster
		static Cluster cluster2;

		@Test
		void aTest() {

			assertEquals(cluster1.getAllCores(), cluster2.getAllCores());
			verifyConnectivity(cluster1);
		}
	}

	@NeedsCausalCluster
	static class MultipleInjectionPointsDifferentTypePerMethodLifeCycleTest {

		@Neo4jUri
		static String clusterUriString;

		@Neo4jUri
		static URI clusterUri;

		@Neo4jUri
		static List<URI> clusterUris;

		@Neo4jCluster
		static Cluster cluster;

		@Test
		void aTest() throws URISyntaxException {

			assertThat(clusterUris).containsOnlyOnce(clusterUri);
			assertThat(clusterUris).containsOnlyOnce(new URI(clusterUriString));

			assertThat(cluster.getAllCores().stream().map(Neo4jCore::getNeo4jUri))
				.containsExactlyInAnyOrderElementsOf(clusterUris);

			verifyConnectivity(cluster);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class InstanceUriFieldInPerClassLifecycleTest {

		@Neo4jUri
		String clusterUri;

		@Test
		void aTest() {

			assertNotNull(clusterUri);
			verifyConnectivity(clusterUri);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class InstanceCollectionFieldInPerClassLifecycleTest {

		@Neo4jUri
		Collection<String> clusterUris;

		@Test
		void aTest() {

			assertNotNull(clusterUris);
			assertFalse(clusterUris.isEmpty());
			verifyConnectivity(clusterUris);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class InstanceClusterFieldInPerClassLifecycleTest {

		@Neo4jCluster
		Cluster cluster;

		@Test
		void aTest() {

			assertNotNull(cluster);
			verifyConnectivity(cluster);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class MultipleUriInjectionPointsPerClassLifeCycleTest {

		@Neo4jUri
		String clusterUri1;

		@Neo4jUri
		String clusterUri2;

		@Neo4jUri
		String clusterUri3;

		@Neo4jUri
		String clusterUri4;

		@Neo4jUri
		String clusterUri5;

		@Neo4jUri
		String clusterUri6;

		@Test
		void aTest() {
			String[] injectedUris = new String[] {
				clusterUri1, clusterUri2, clusterUri3, clusterUri4, clusterUri5, clusterUri6 };

			List<String> distinctUris = Arrays.stream(injectedUris).distinct().collect(Collectors.toList());

			assertThat(distinctUris.size()).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(3);

			verifyConnectivity(distinctUris);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class MultipleCollectionInjectionPointsPerClassLifeCycleTest {

		@Neo4jUri
		Collection<String> clusterUris1;

		@Neo4jUri
		Collection<String> clusterUris2;

		@Test
		void aTest() {

			assertThat(clusterUris1).containsExactlyInAnyOrderElementsOf(clusterUris2);
			verifyConnectivity(clusterUris1);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class MultipleClusterInjectionPointsPerClassLifeCycleTest {

		@Neo4jCluster
		Cluster cluster1;

		@Neo4jUri
		Cluster cluster2;

		@Test
		void aTest() {

			assertEquals(cluster1.getAllCores(), cluster2.getAllCores());
			verifyConnectivity(cluster1);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class MultipleInjectionPointsDifferentTypePerClassLifeCycleTest {

		@Neo4jUri
		String clusterUriString;

		@Neo4jUri
		URI clusterUri;

		@Neo4jUri
		List<URI> clusterUris;

		@Neo4jCluster
		Cluster cluster;

		@Test
		void aTest() throws URISyntaxException {

			assertThat(clusterUris).containsOnlyOnce(clusterUri);
			assertThat(clusterUris).containsOnlyOnce(new URI(clusterUriString));

			assertThat(cluster.getAllCores().stream().map(Neo4jCore::getNeo4jUri))
				.containsExactlyInAnyOrderElementsOf(clusterUris);

			verifyConnectivity(cluster);
		}
	}

	static class NestedClassWithAllNeededAnnotationsTest {

		@TestInstance(TestInstance.Lifecycle.PER_CLASS)
		@Nested
		@NeedsCausalCluster
		class NestedTest {

			@Neo4jUri
			String clusterUri;

			@Test
			void aTest() {

				assertNotNull(clusterUri);
				verifyConnectivity(clusterUri);
			}
		}
	}

	// Invalid usage

	static class MissingExtensionTest {

		@Neo4jUri
		static String clusterUri;

		@Test
		void aTest() {
			assertNotNull(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class MissingInjectionPoint {

		static String clusterUri;

		@Test
		void aTest() {
			assertNotNull(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class WrongUriExtensionPoint {

		@Neo4jUri
		static Integer clusterUri;

		@Test
		void aTest() {
			assertNotNull(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class WrongUriCollectionExtensionPoint {

		@Neo4jUri
		static Collection<Object> clusterUris;

		@Test
		void aTest() {
			assertNotNull(clusterUris);
		}
	}

	@NeedsCausalCluster
	static class WrongClusterExtensionPoint {

		@Neo4jCluster
		static Object cluster;

		@Test
		void aTest() {
			assertNotNull(cluster);
		}
	}

	@NeedsCausalCluster
	static class MixedUpClusterExtensionPoint {

		@Neo4jUri
		static Cluster clusterUri;

		@Test
		void aTest() {
			assertNotNull(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class MixedUpUriExtensionPoint {

		@Neo4jCluster
		static URI cluster;

		@Test
		void aTest() {
			assertNotNull(cluster);
		}
	}

	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class LifecyleOnOuterClass {

		@Nested
		@NeedsCausalCluster
		class NestedTest {

			@Neo4jUri
			String clusterUri;

			@Test
			void aTest() {
				assertNotNull(clusterUri);
			}
		}
	}
}
