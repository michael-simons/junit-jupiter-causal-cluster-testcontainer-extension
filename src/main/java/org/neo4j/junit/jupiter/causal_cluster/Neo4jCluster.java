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
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.neo4j.junit.jupiter.causal_cluster.Neo4jServer.Type;

/**
 * This allows us to interact with a Neo4j Causal Cluster.
 *
 * @author Michael J. Simons
 * @author Andrew Jefferson
 */
public interface Neo4jCluster {

	/**
	 * This method is guaranteed to return the URI of a core server and never a replica.
	 *
	 * @return An URI into this cluster.
	 */
	default URI getURI() {

		Collection<URI> allCoreUris = getURIs();
		int n = ThreadLocalRandom.current().nextInt(allCoreUris.size());
		return allCoreUris.stream().skip(n).findFirst().get();
	}

	/**
	 * @return All URIs into this cluster (only core servers included)
	 */
	default Collection<URI> getURIs() {
		return getAllServers().stream().filter(s -> s.getType() == Type.CORE_SERVER).map(Neo4jServer::getURI)
			.collect(Collectors.toList());
	}

	/**
	 * @return The Neo4j servers contained by this cluster.
	 */
	Set<Neo4jServer> getAllServers();

	/**
	 * Retrieves all the servers of that cluster except the given exclusions.
	 *
	 * @param exclusions Servers not to return
	 * @return A set of server that are part of this cluster.
	 */
	default Set<Neo4jServer> getAllServersExcept(Neo4jServer... exclusions) {
		return getAllServersExcept(
			exclusions == null ? Collections.emptySet() : Stream.of(exclusions).collect(Collectors.toSet()));
	}

	/**
	 * Retrieves all the servers of that cluster except the given exclusions.
	 *
	 * @param exclusions Servers not to return
	 * @return A set of server that are part of this cluster.
	 */
	Set<Neo4jServer> getAllServersExcept(Set<Neo4jServer> exclusions);

	/**
	 * Stops {@code n} random server.
	 *
	 * @param n The number of servers to stop
	 * @return The stopped servers
	 */
	Set<Neo4jServer> stopRandomServers(int n);

	/**
	 * Stops {@code n} random server but none of the given exclusions.
	 *
	 * @param n          The number of servers to stop
	 * @param exclusions Servers not to be stopped
	 * @return The stopped servers
	 */
	default Set<Neo4jServer> stopRandomServersExcept(int n, Neo4jServer... exclusions) {
		return stopRandomServersExcept(n,
			exclusions == null ? Collections.emptySet() : Stream.of(exclusions).collect(Collectors.toSet()));
	}

	/**
	 * Stops {@code n} random server but none of the given exclusions
	 *
	 * @param n          The number of servers to stop
	 * @param exclusions Servers not to be stopped
	 * @return The stopped servers
	 */
	Set<Neo4jServer> stopRandomServersExcept(int n, Set<Neo4jServer> exclusions);

	/**
	 * Starts the given set of servers.
	 *
	 * @param servers Servers to start
	 * @return The started servers
	 */
	Set<Neo4jServer> startServers(Set<Neo4jServer> servers);

	/**
	 * Pauses {@code n} random servers.
	 *
	 * @param n The number of servers to pause
	 * @return The paused servers
	 */
	Set<Neo4jServer> pauseRandomServers(int n);

	/**
	 * Pauses {@code n} random servers but none of the given exclusions.
	 *
	 * @param n          The number of servers to pause
	 * @param exclusions Servers not to be paused
	 * @return The paused servers
	 */
	default Set<Neo4jServer> pauseRandomServersExcept(int n, Neo4jServer... exclusions) {
		return pauseRandomServersExcept(n,
			exclusions == null ? Collections.emptySet() : Stream.of(exclusions).collect(Collectors.toSet()));
	}

	/**
	 * Pauses {@code n} random servers but none of the given exclusions
	 *
	 * @param n          The number of servers to pause
	 * @param exclusions Servers not to be paused
	 * @return The paused servers
	 */
	Set<Neo4jServer> pauseRandomServersExcept(int n, Set<Neo4jServer> exclusions);

	/**
	 * Unpauses the given set of servers.
	 *
	 * @param servers Servers to unpause
	 * @return The unpaused servers
	 */
	Set<Neo4jServer> unpauseServers(Set<Neo4jServer> servers);

	void waitForLogMessageOnAll(Set<Neo4jServer> servers, String message, Duration timeout)
		throws Neo4jTimeoutException;

	void waitForBoltOnAll(Set<Neo4jServer> servers, Duration timeout)
		throws Neo4jTimeoutException;

	/**
	 * Thrown in case of time outs when dealing with the cluster or its servers.
	 */
	class Neo4jTimeoutException extends Exception {

		Neo4jTimeoutException(Exception cause) {
			super(cause);
		}
	}
}
