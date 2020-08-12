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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.testcontainers.containers.Neo4jContainer;

/**
 * @author Andrew Jefferson
 * @author Michael J. Simons
 */
final class DefaultNeo4jServer implements Neo4jServer, AutoCloseable {

	private enum LogFile {
		DEBUG("debug.log"),
		QUERY("query.log");

		final String name;

		LogFile(String name) {
			this.name = name;
		}
	}

	private final static String START_TOKEN = "Starting Neo4j.\n";

	/**
	 * The underlying test container instance.
	 */
	private final Neo4jContainer<?> container;
	/**
	 * The external URI under which this server is reachable, not to be confused with the internal URI returned by {@link Neo4jContainer#getBoltUrl()}.
	 */
	private final URI externalURI;

	DefaultNeo4jServer(Neo4jContainer<?> container, URI externalURI) {
		this.container = container;
		this.externalURI = externalURI;
	}

	@Override
	public String getDebugLog() {
		return retrieveLogFile(LogFile.DEBUG);
	}

	@Override
	public String getQueryLog() {
		return retrieveLogFile(LogFile.QUERY);
	}

	private String retrieveLogFile(LogFile logFile) {

		try {
			return container.execInContainer("cat", "/logs/" + logFile.name).getStdout();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getContainerLogs() {
		return container.getLogs();
	}

	@Override
	public String getContainerLogsSinceStart() {
		String allLogs = container.getLogs();
		return allLogs.substring(allLogs.lastIndexOf(START_TOKEN));
	}

	@Override
	public URI getURI() {
		return externalURI;
	}

	@Override
	public URI getDirectBoltUri() {
		try {
			return new URI(externalURI.toString().replace("neo4j://", "bolt://"));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DefaultNeo4jServer that = (DefaultNeo4jServer) o;
		return container.equals(that.container) &&
			externalURI.equals(that.externalURI);
	}

	@Override
	public int hashCode() {
		return Objects.hash(container, externalURI);
	}

	@Override
	public void close() {
		container.close();
	}

	Neo4jContainer<?> unwrap() {
		return container;
	}
}
