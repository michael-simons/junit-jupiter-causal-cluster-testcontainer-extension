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

import org.testcontainers.containers.Container;
import org.testcontainers.containers.Neo4jContainer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Andrew Jefferson
 * @author Michael J. Simons
 */
final class DefaultNeo4jServer implements Neo4jServer, AutoCloseable {

	private final static Function<Stream<String>, String> streamToString = lines -> lines
		.collect(Collectors.joining("\n"));

	private enum LogFile {
		DEBUG("debug.log"),
		QUERY("query.log");

		final String name;

		LogFile(String name) {
			this.name = name;
		}
	}

	private final static List<String> startingTokens = Stream.of( "Starting Neo4j.\n", "Starting...\n" ).collect( Collectors.toList() );

	/**
	 * The underlying test container instance.
	 */
	private final Neo4jContainer<?> container;
	/**
	 * The external URI under which this server is reachable, not to be confused with the internal URI returned by
	 * {@link Neo4jContainer#getBoltUrl()}.
	 */
	private final URI externalURI;
	/**
	 * The type of this server.
	 */
	private final Type type;
	/**
	 * Basic auth token against the container.
	 */
	private final String authToken;

	DefaultNeo4jServer(Neo4jContainer<?> container, URI externalURI, Type type) {
		this.container = container;
		this.externalURI = externalURI;
		this.type = type;

		byte[] encodedAuth = Base64.getEncoder()
			.encode(("neo4j:" + container.getAdminPassword()).getBytes(StandardCharsets.UTF_8));
		this.authToken = "Basic " + new String(encodedAuth);
	}

	@Override
	public long getDebugLogPosition() {
		return retrieveLogFile(LogFile.DEBUG, 0, Stream::count);
	}

	@Override
	public String getDebugLogFrom(long offset) {
		return retrieveLogFile(LogFile.DEBUG, offset, streamToString);
	}

	@Override
	public long getQueryLogPosition() {
		return retrieveLogFile(LogFile.QUERY, 0, Stream::count);
	}

	@Override
	public String getQueryLogFrom(long offset) {
		return retrieveLogFile(LogFile.QUERY, offset, streamToString);
	}

	private <T> T retrieveLogFile(LogFile logFile, long offset, Function<Stream<String>, T> handler) {
		try {
			Path tempFile = Files.createTempFile(logFile.name, ".log");
			container.copyFileFromContainer("/logs/" + logFile.name, tempFile.toAbsolutePath().toString());
			try (Stream<String> lines = Files.lines(tempFile)) {
				return handler.apply(lines.skip(offset));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getContainerLogs() {
		return container.getLogs();
	}

	@Override
	public String getContainerLogsSinceStart()
	{
		String allLogs = container.getLogs();
		return startingTokens.stream()
							 .filter( startToken -> allLogs.lastIndexOf( startToken ) > 0 )
							 .map( startToken -> allLogs.substring( allLogs.lastIndexOf( startToken ) ) )
							 .findFirst()
							 .orElse( "" );
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

	@Override
	public boolean isContainerRunning() {
		return container.isRunning();
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public Container.ExecResult execInContainer(String... command) throws IOException, InterruptedException {
		return container.execInContainer(command);
	}

	Neo4jContainer<?> unwrap() {
		return container;
	}

	@Override
	public String toString() {
		return "DefaultNeo4jServer{" +
			"container=" + container.getContainerId() +
			", externalURI=" + externalURI +
			", type=" + type +
			'}';
	}
}
