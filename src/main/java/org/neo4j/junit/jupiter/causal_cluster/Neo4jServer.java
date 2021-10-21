/*
 * Copyright (c) 2019-2021 "Neo4j,"
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

import org.testcontainers.containers.Container;

import java.io.IOException;
import java.net.URI;

/**
 * This is a Neo4j instance that is a part of causal cluster, with {@literal Neo4jServer} being used equally for Core and
 * Replica Servers as defined in
 * <a href="https://neo4j.com/docs/operations-manual/current/clustering/introduction/">the Neo4j introduction to clustering</a>.
 *
 * @author Michael J. Simons
 * @author Andrew Jefferson
 */
public interface Neo4jServer {

	/**
	 * Server can either be a core or replica server.
	 */
	enum Type {
		CORE_SERVER,
		REPLICA_SERVER
	}

	/**
	 * @return The complete Neo4j debug log.
	 */
	default String getDebugLog() {
		return getDebugLogFrom(0);
	}

	/**
	 * @return The number of lines in the Neo4j debug log.
	 */
	long getDebugLogPosition();

	/**
	 * @param offset the offset to read from
	 * @return The Neo4j debug log, omitting the first {@code offset} lines.
	 */
	String getDebugLogFrom(long offset);

	/**
	 * @return The query log.
	 */
	default String getQueryLog() {
		return getQueryLogFrom(0);
	}

	/**
	 * @return The number of lines in the Neo4j debug log.
	 */
	long getQueryLogPosition();

	/**
	 * @param offset the offset to read from
	 * @return The Neo4j debug log, omitting the first {@code offset} lines.
	 */
	String getQueryLogFrom(long offset);

	/**
	 * @return The complete container logs.
	 */
	String getContainerLogs();

	/**
	 * @return The container logs since Neo4j started.
	 */
	String getContainerLogsSinceStart();

	/**
	 * @return An URI into this server.
	 */
	URI getURI();

	/**
	 * @return The URI into this server but using the {@code bolt} instead of the {@code neo4j} protocol.
	 */
	URI getDirectBoltUri();

	/**
	 * @return True if the underlying container is running.
	 */
	boolean isContainerRunning();

	/**
	 * @return The type of this server.
	 */
	Type getType();

	/**
	 * Run a command inside the Neo4j container, as though using "docker exec".
	 * @param command to run
	 * @return the result of execution
	 * @see org.testcontainers.containers.ContainerState#execInContainer(String...)
	 * @throws IOException Just passing those on
	 * @throws InterruptedException These as well
	 */
	Container.ExecResult execInContainer(String... command) throws IOException, InterruptedException;
}
