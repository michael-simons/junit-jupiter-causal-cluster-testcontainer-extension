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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This allows us to interact with a Neo4j Causal Cluster.
 *
 * @author Michael J. Simons
 * @author Andrew Jefferson
 */
public interface Neo4jCluster {

	/**
	 * @return An URI into this cluster.
	 */
	URI getURI();

	/**
	 * @return All URIs into this cluster.
	 */
	default Collection<URI> getURIs() {
		return getAllServers().stream().map(Neo4jServer::getURI).collect(Collectors.toList());
	}

	/**
	 * @return The Neo4j servers contained by this cluster.
	 */
	Set<Neo4jServer> getAllServers();

	Set<Neo4jServer> getAllServersExcept(Set<Neo4jServer> except);

	Set<Neo4jServer> stopRandomServers(int n);

	Set<Neo4jServer> stopRandomServersExcept(int n, Set<Neo4jServer> except);

	Set<Neo4jServer> startServers(Set<Neo4jServer> servers);

	void waitForLogMessageOnAll(Set<Neo4jServer> servers, String message, Duration timeout)
		throws Neo4jTimeoutException;

	void waitForBoltOnAll(Set<Neo4jServer> servers, Duration timeout)
		throws Neo4jTimeoutException;

	class Neo4jTimeoutException extends Exception {
		Neo4jTimeoutException(Exception cause) {
			super(cause);
		}
	}
}
