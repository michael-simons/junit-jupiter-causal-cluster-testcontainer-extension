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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

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
					DiscoverySelectors.selectClass(StaticUriFieldOnPerMethodLifecycleTest.class),
					DiscoverySelectors.selectClass(StaticCollectionFieldOnPerMethodLifecycleTest.class),
					DiscoverySelectors.selectClass(StaticClusterFieldOnPerMethodLifecycleTest.class),
					DiscoverySelectors.selectClass(InstanceUriFieldInPerClassLifecycleTest.class),
					DiscoverySelectors.selectClass(InstanceCollectionFieldInPerClassLifecycleTest.class),
					DiscoverySelectors.selectClass(InstanceClusterFieldInPerClassLifecycleTest.class),
					DiscoverySelectors.selectClass(NestedClassWithAllNeededAnnotationsTest.class)
				)
				.execute().testEvents();

			assertThat(testEvents.failed().stream().collect(Collectors.toList()))
				.hasSize(0);
			assertThat(testEvents.succeeded().stream().collect(Collectors.toList()))
				.hasSize(7);
		}

		@Test
		void shouldNotStartMultipleClusters() {
			Events testEvents = EngineTestKit.engine(ENGINE_ID)
				.selectors(
					DiscoverySelectors.selectClass(MultipleCollectionInjectionPointsPerMethodLifeCycleTest.class),
					DiscoverySelectors.selectClass(MultipleUriInjectionPointsPerMethodLifeCycleTest.class),
					DiscoverySelectors.selectClass(MultipleClusterInjectionPointsPerMethodLifeCycleTest.class),
					DiscoverySelectors.selectClass(MultipleInjectionPointsDifferentTypePerMethodLifeCycleTest.class),
					DiscoverySelectors.selectClass(MultipleCollectionInjectionPointsPerClassLifeCycleTest.class),
					DiscoverySelectors.selectClass(MultipleUriInjectionPointsPerClassLifeCycleTest.class),
					DiscoverySelectors.selectClass(MultipleClusterInjectionPointsPerClassLifeCycleTest.class),
					DiscoverySelectors.selectClass(MultipleInjectionPointsDifferentTypePerClassLifeCycleTest.class)
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
					DiscoverySelectors.selectClass(MissingExtensionTest.class),
					DiscoverySelectors.selectClass(MissingInjectionPoint.class),
					DiscoverySelectors.selectClass(LifecyleOnOuterClass.class))
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
					DiscoverySelectors.selectClass(WrongUriExtensionPoint.class),
					DiscoverySelectors.selectClass(WrongUriCollectionExtensionPoint.class),
					DiscoverySelectors.selectClass(WrongCollectionSubclass1ExtensionPoint.class),
					DiscoverySelectors.selectClass(WrongCollectionSubclass2ExtensionPoint.class),
					DiscoverySelectors.selectClass(WrongClusterExtensionPoint.class)
				)
				.execute().testEvents();

			assertThat(testEvents.started().stream().collect(Collectors.toList()))
				.hasSize(0);
		}
	}

	static void verifyConnectivity(Neo4jCluster cluster) {
		List<String> clusterUris = cluster.getAllServers().stream()
			.map(c -> c.getURI().toString())
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
	static class StaticUriFieldOnPerMethodLifecycleTest {

		@CausalCluster
		static String clusterUri;

		@Test
		void aTest() {

			assertThat(clusterUri).isNotNull();
			verifyConnectivity(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class StaticCollectionFieldOnPerMethodLifecycleTest {

		@CausalCluster
		static Collection<String> clusterUris;

		@Test
		void aTest() {

			assertThat(clusterUris).isNotNull();
			assertThat(clusterUris).isNotEmpty();
			verifyConnectivity(clusterUris);
		}
	}

	@NeedsCausalCluster
	static class StaticClusterFieldOnPerMethodLifecycleTest {

		@CausalCluster
		static Neo4jCluster cluster;

		@Test
		void aTest() {

			assertThat(cluster).isNotNull();
			verifyConnectivity(cluster);
		}
	}

	@NeedsCausalCluster
	static class MultipleUriInjectionPointsPerMethodLifeCycleTest {

		@CausalCluster
		static String clusterUri1;

		@CausalCluster
		static String clusterUri2;

		@CausalCluster
		static String clusterUri3;

		@CausalCluster
		static String clusterUri4;

		@CausalCluster
		static String clusterUri5;

		@CausalCluster
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

		@CausalCluster
		static Collection<String> clusterUris1;

		@CausalCluster
		static Collection<String> clusterUris2;

		@Test
		void aTest() {

			assertThat(clusterUris1).containsExactlyInAnyOrderElementsOf(clusterUris2);
			verifyConnectivity(clusterUris1);
		}
	}

	@NeedsCausalCluster
	static class MultipleClusterInjectionPointsPerMethodLifeCycleTest {

		@CausalCluster
		static Neo4jCluster cluster1;

		@CausalCluster
		static Neo4jCluster cluster2;

		@Test
		void aTest() {

			assertThat(cluster1.getAllServers()).isEqualTo(cluster2.getAllServers());
			verifyConnectivity(cluster1);
		}
	}

	@NeedsCausalCluster
	static class MultipleInjectionPointsDifferentTypePerMethodLifeCycleTest {

		@CausalCluster
		static String clusterUriString;

		@CausalCluster
		static URI clusterUri;

		@CausalCluster
		static List<URI> clusterUris;

		@CausalCluster
		static Neo4jCluster cluster;

		@Test
		void aTest() throws URISyntaxException {

			assertThat(clusterUris).containsOnlyOnce(clusterUri);
			assertThat(clusterUris).containsOnlyOnce(new URI(clusterUriString));

			assertThat(cluster.getAllServers().stream().map(Neo4jServer::getURI))
				.containsExactlyInAnyOrderElementsOf(clusterUris);

			verifyConnectivity(cluster);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class InstanceUriFieldInPerClassLifecycleTest {

		@CausalCluster
		String clusterUri;

		@Test
		void aTest() {

			assertThat(clusterUri).isNotNull();
			verifyConnectivity(clusterUri);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class InstanceCollectionFieldInPerClassLifecycleTest {

		@CausalCluster
		Collection<String> clusterUris;

		@Test
		void aTest() {

			assertThat(clusterUris)
				.isNotNull()
				.isNotEmpty();
			verifyConnectivity(clusterUris);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class InstanceClusterFieldInPerClassLifecycleTest {

		@CausalCluster
		Neo4jCluster cluster;

		@Test
		void aTest() {

			assertThat(cluster).isNotNull();
			verifyConnectivity(cluster);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class MultipleUriInjectionPointsPerClassLifeCycleTest {

		@CausalCluster
		String clusterUri1;

		@CausalCluster
		String clusterUri2;

		@CausalCluster
		String clusterUri3;

		@CausalCluster
		String clusterUri4;

		@CausalCluster
		String clusterUri5;

		@CausalCluster
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

		@CausalCluster
		Collection<String> clusterUris1;

		@CausalCluster
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

		@CausalCluster
		Neo4jCluster cluster1;

		@CausalCluster
		Neo4jCluster cluster2;

		@Test
		void aTest() {

			assertThat(cluster1.getAllServers()).isEqualTo(cluster2.getAllServers());
			verifyConnectivity(cluster1);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class MultipleInjectionPointsDifferentTypePerClassLifeCycleTest {

		@CausalCluster
		String clusterUriString;

		@CausalCluster
		URI clusterUri;

		@CausalCluster
		List<URI> clusterUris;

		@CausalCluster
		Neo4jCluster cluster;

		@Test
		void aTest() throws URISyntaxException {

			assertThat(clusterUris).containsOnlyOnce(clusterUri);
			assertThat(clusterUris).containsOnlyOnce(new URI(clusterUriString));

			assertThat(cluster.getAllServers().stream().map(Neo4jServer::getURI))
				.containsExactlyInAnyOrderElementsOf(clusterUris);

			verifyConnectivity(cluster);
		}
	}

	static class NestedClassWithAllNeededAnnotationsTest {

		@TestInstance(TestInstance.Lifecycle.PER_CLASS)
		@Nested
		@NeedsCausalCluster
		class NestedTest {

			@CausalCluster
			String clusterUri;

			@Test
			void aTest() {

				assertThat(clusterUri).isNotNull();
				verifyConnectivity(clusterUri);
			}
		}
	}

	// Invalid usage

	static class MissingExtensionTest {

		@CausalCluster
		static String clusterUri;

		@Test
		void aTest() {
			assertThat(clusterUri).isNotNull();
		}
	}

	@NeedsCausalCluster
	static class MissingInjectionPoint {

		static String clusterUri;

		@Test
		void aTest() {
			assertThat(clusterUri).isNotNull();
		}
	}

	@NeedsCausalCluster
	static class WrongUriExtensionPoint {

		@CausalCluster
		static Integer clusterUri;

		@Test
		void aTest() {
			assertThat(clusterUri).isNotNull();
		}
	}

	@NeedsCausalCluster
	static class WrongUriCollectionExtensionPoint {

		@CausalCluster
		static Collection<Object> clusterUris;

		@Test
		void aTest() {
			assertThat(clusterUris).isNotNull();
		}
	}

	@NeedsCausalCluster
	static class WrongCollectionSubclass1ExtensionPoint {

		@CausalCluster
		static Set<URI> clusterUris;

		@Test
		void aTest() {
			assertThat(clusterUris).isNotNull();
		}
	}

	@NeedsCausalCluster
	static class WrongCollectionSubclass2ExtensionPoint {

		@CausalCluster
		static ArrayList<URI> clusterUris;

		@Test
		void aTest() {
			assertThat(clusterUris).isNotNull();
		}
	}

	@NeedsCausalCluster
	static class WrongClusterExtensionPoint {

		@CausalCluster
		static Object cluster;

		@Test
		void aTest() {
			assertThat(cluster).isNotNull();
		}
	}

	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class LifecyleOnOuterClass {

		@Nested
		@NeedsCausalCluster
		class NestedTest {

			@CausalCluster
			String clusterUri;

			@Test
			void aTest() {
				assertThat(clusterUri).isNotNull();
			}
		}
	}
}
