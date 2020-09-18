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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.Neo4jException;

/**
 * Test utilities for tasks that require a Neo4j Driver
 */
final class DriverUtils {

	private static final Config SINGLE_CONNECTION_FAIL_FAST_NO_LOGS = Config.builder()
		.withMaxConnectionPoolSize(1)
		.withEventLoopThreads(1)
		.withConnectionTimeout(10, TimeUnit.SECONDS)
		.withConnectionAcquisitionTimeout(10, TimeUnit.SECONDS)
		.withMaxTransactionRetryTime(20, TimeUnit.SECONDS)
		.withConnectionLivenessCheckTimeout(1, TimeUnit.MINUTES)
		.withLogging(Logging.javaUtilLogging(Level.OFF))
		.build();

	private static final AuthToken neo4jUserToken = AuthTokens.basic("neo4j", "password");

	static void verifyAllServersHaveConnectivity(Neo4jCluster cluster) {
		verifyAllServersHaveConnectivity(cluster.getAllServers());
	}

	static void verifyAllServersHaveConnectivity(Collection<Neo4jServer> servers) {
		verifyAllServersBoltConnectivity(servers);
		verifyAllServersNeo4jConnectivity(servers);
	}

	static void verifyAnyServersHaveConnectivity(Neo4jCluster cluster) {
		verifyAnyServersHaveConnectivity(cluster.getAllServers());
	}

	static void verifyAnyServersHaveConnectivity(Collection<Neo4jServer> servers) {
		verifyAnyServersBoltConnectivity(servers);
		verifyAnyServersNeo4jConnectivity(servers);
	}

	static void verifyEventuallyAllServersHaveConnectivity(Neo4jCluster cluster, Duration timeout)
		throws TimeoutException {
		eventually(() -> verifyAllServersHaveConnectivity(cluster.getAllServers()),
			timeout, "Verifying all servers have connectivity");
	}

	static void verifyContinuouslyAllServersHaveConnectivity(Neo4jCluster cluster, Duration timeout) {
		continuously(() -> verifyAllServersHaveConnectivity(cluster.getAllServers()),
			timeout, "Verifying all servers have connectivity");
	}

	static void retryOnceWithWaitForConnectivity(Neo4jCluster cluster, Duration connectivityTimeout, Runnable fn)
		throws TimeoutException {
		retryOnceWithWaitForConnectivity(cluster, connectivityTimeout, fn, true);
	}

	static void retryOnceWithWaitForConnectivity(Neo4jCluster cluster, Duration connectivityTimeout, Runnable fn,
		boolean retryOnce)
		throws TimeoutException {

		// first wait for full connectivity
		DriverUtils.verifyEventuallyAllServersHaveConnectivity(cluster, connectivityTimeout);
		try {
			fn.run();
		} catch (Exception e) {
			// if something goes wrong we retry once
			if (retryOnce) {
				// TODO: log the suppressed exception
				retryOnceWithWaitForConnectivity(cluster, connectivityTimeout, fn, false);
			} else {
				throw e;
			}
		}
	}

	static int[] getNeo4jVersion(Neo4jCluster cluster) {
		try (
			Driver driver = GraphDatabase.driver(cluster.getURI(), neo4jUserToken, SINGLE_CONNECTION_FAIL_FAST_NO_LOGS);
			Session session = driver.session();
		) {
			String version = session.readTransaction(tx ->
				tx.run("call dbms.components() yield name, versions, edition " +
					"UNWIND versions as version return name, version, edition "
				).single().get("version").asString());
			String[] parts = version.split("\\.");
			return Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
		}
	}

	private static void verifyAllServersBoltConnectivity(Collection<Neo4jServer> servers) {
		// Verify connectivity
		List<String> boltAddresses = servers.stream()
			.map(Neo4jServer::getDirectBoltUri)
			.map(URI::toString)
			.collect(Collectors.toList());
		verifyConnectivity(boltAddresses);
	}

	private static void verifyAllServersNeo4jConnectivity(Collection<Neo4jServer> servers) {
		// Verify connectivity
		List<String> boltAddresses = servers.stream()
			.map(Neo4jServer::getURI)
			.map(URI::toString)
			.peek(s -> assertThat(s).startsWith("neo4j://"))
			.collect(Collectors.toList());
		verifyConnectivity(boltAddresses);
	}

	private static void eventually(Runnable fn, Duration timeout, String description) throws TimeoutException {

		Instant deadline = Instant.now().plus(timeout);
		Exception lastException = null;
		while (Instant.now().isBefore(deadline)) {
			try {
				fn.run();
				return;
			} catch (Exception e) {
				lastException = e;
			}
		}
		TimeoutException e = new TimeoutException("Timed out performing: " + description);
		e.addSuppressed(lastException == null ?
			new Exception("Timeout elapsed before first attempt.") : lastException);
		throw e;
	}

	private static void continuously(Runnable fn, Duration timeout, String description) {

		Instant deadline = Instant.now().plus(timeout);
		int count = 0;
		while (Instant.now().isBefore(deadline)) {
			fn.run();
			count += 1;
		}
		assertThat(count).as(description).isGreaterThan(0);
	}

	private static void verifyAnyServersNeo4jConnectivity(Neo4jCluster cluster) {
		verifyAnyServersNeo4jConnectivity(cluster.getAllServers());
	}

	private static void verifyAnyServersNeo4jConnectivity(Collection<Neo4jServer> servers) {
		// Verify connectivity
		List<Exception> exceptions = new ArrayList<>();
		for (URI clusterUri : servers.stream().map(Neo4jServer::getURI).collect(Collectors.toList())) {
			assertThat(clusterUri.toString()).startsWith("neo4j://");
			try {
				verifyConnectivity(clusterUri);
				return;
			} catch (Exception e) {
				exceptions.add(e);
			}
		}

		// If we reach here they all failed. This assertion will fail and log the exceptions
		if (!exceptions.isEmpty()) {
			// TODO aggregate exceptions better?
			RuntimeException e = new RuntimeException();
			exceptions.forEach(e::addSuppressed);
			throw e;
		}
	}

	private static void verifyAnyServersBoltConnectivity(Collection<Neo4jServer> servers) {
		// Verify connectivity
		List<Exception> exceptions = new ArrayList<>();
		for (URI clusterUri : servers.stream().map(Neo4jServer::getDirectBoltUri).collect(Collectors.toList())) {
			assertThat(clusterUri.toString()).startsWith("bolt://");
			try {
				verifyConnectivity(clusterUri);
				return;
			} catch (Exception e) {
				exceptions.add(e);
			}
		}

		// If we reach here they all failed. This assertion will fail and log the exceptions
		if (!exceptions.isEmpty()) {
			// TODO aggregate exceptions better?
			RuntimeException e = new RuntimeException();
			exceptions.forEach(e::addSuppressed);
			throw e;
		}
	}

	static <T extends Throwable> void hasSuppressedNeo4jException(T exception) {
		for (Throwable suppressed : exception.getSuppressed()) {
			if (suppressed instanceof Neo4jException) {
				return;
			}
		}
		assertThat(exception).isInstanceOf(Neo4jException.class);
	}

	static void verifyConnectivity(Collection<String> clusterUris) {
		assertThat(clusterUris.size()).isGreaterThanOrEqualTo(1);
		for (String clusterUri : clusterUris) {
			verifyConnectivity(URI.create(clusterUri));
		}
	}

	static void verifyConnectivity(String uri) {
		verifyConnectivity(URI.create(uri));
	}

	static void verifyConnectivity(URI uri) {
		try (Driver driver = GraphDatabase.driver(uri, neo4jUserToken, SINGLE_CONNECTION_FAIL_FAST_NO_LOGS)) {
			driver.verifyConnectivity();
		}
	}

	private DriverUtils() {
	}
}
