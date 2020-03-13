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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.testkit.engine.EngineTestKit;
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
			EngineTestKit.engine(ENGINE_ID)
				.selectors(selectClass(StaticFieldOnPerMethodLifecycleTest.class),
					selectClass(InstanceFieldInPerClassLifecycleTest.class),
					selectClass(NestedClassWithAllNeededAnnotationsTest.class))
				.execute().testEvents()
				.assertStatistics(stats -> stats.succeeded(3).failed(0));
		}

		@Test
		void shouldNotStartMultipleClusters() {
			EngineTestKit.engine(ENGINE_ID)
				.selectors(selectClass(MultipleInjectionPointsPerMethodLifeCycleTest.class),
					selectClass(MultipleInjectionPointsPerClassLifeCycleTest.class))
				.execute().testEvents()
				.assertStatistics(stats -> stats.succeeded(2).failed(0));
		}
	}

	@Nested
	class InvalidUses {
		@Test
		void shouldNotWorkWithInvalidUsage() {
			EngineTestKit.engine(ENGINE_ID)
				.selectors(
					selectClass(MissingExtensionTest.class),
					selectClass(MissingInjectionPoint.class),
					selectClass(LifecyleOnOuterClass.class))
				.execute().testEvents()
				.assertStatistics(stats -> stats.succeeded(0).failed(3));
		}

		@Test
		void shouldCheckTypeOfInjectionPoint() {
			EngineTestKit.engine(ENGINE_ID)
				.selectors(
					selectClass(WrongExtensionPoint.class))
				.execute().testEvents()
				.assertStatistics(stats -> stats.started(0));
		}
	}

	static void verifyConnectivity(String uri) {

		try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic("neo4j", "password"))) {
			driver.verifyConnectivity();
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	@NeedsCausalCluster
	static class StaticFieldOnPerMethodLifecycleTest {

		@Neo4jUri
		static String clusterUri;

		@Test
		void aTest() {

			assertNotNull(clusterUri);
			verifyConnectivity(clusterUri);
		}
	}

	@NeedsCausalCluster
	static class MultipleInjectionPointsPerMethodLifeCycleTest {

		@Neo4jUri
		static String clusterUri1;

		@Neo4jUri
		static String clusterUri2;

		@Test
		void aTest() {

			assertTrue(clusterUri1.equals(clusterUri2));
			verifyConnectivity(clusterUri1);
		}
	}

	@NeedsCausalCluster
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class InstanceFieldInPerClassLifecycleTest {

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
	static class MultipleInjectionPointsPerClassLifeCycleTest {

		@Neo4jUri
		String clusterUri1;

		@Neo4jUri
		String clusterUri2;

		@Test
		void aTest() {

			assertTrue(clusterUri1.equals(clusterUri2));
			verifyConnectivity(clusterUri1);
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
	static class WrongExtensionPoint {

		@Neo4jUri
		static Integer clusterUri;

		@Test
		void aTest() {
			assertNotNull(clusterUri);
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
