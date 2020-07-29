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
					selectClass(WrongClusterExtensionPoint.class)
				)
				.execute().testEvents();

			assertThat(testEvents.started().stream().collect(Collectors.toList()))
				.hasSize(0);
		}
	}

	static void verifyConnectivity(Cluster cluster) {
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
	static class StaticClusterUriFieldOnPerMethodLifecycleTest {

		@CausalCluster
		static String clusterUri;

		@Test
		void aTest() {

			assertThat(clusterUri).isNotNull();
			verifyConnectivity(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class StaticClusterFieldOnPerMethodLifecycleTest {

		@CausalCluster
		static Cluster cluster;

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
		static Cluster cluster1;

		@CausalCluster
		static Cluster cluster2;

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
		static Cluster cluster;

		@Test
		void aTest() throws URISyntaxException {

			assertThat(clusterUris).containsOnlyOnce(clusterUri);
			assertThat(clusterUris).containsOnlyOnce(new URI(clusterUriString));

			assertThat(cluster.getAllServers().stream().map(Server::getURI))
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
		Cluster cluster;

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
		Cluster cluster1;

		@CausalCluster
		Cluster cluster2;

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
		Cluster cluster;

		@Test
		void aTest() throws URISyntaxException {

			assertThat(clusterUris).containsOnlyOnce(clusterUri);
			assertThat(clusterUris).containsOnlyOnce(new URI(clusterUriString));

			assertThat(cluster.getAllServers().stream().map(Server::getURI))
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
