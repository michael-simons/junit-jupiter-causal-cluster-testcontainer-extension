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

import com.github.dockerjava.api.DockerClient;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.testcontainers.containers.Neo4jContainer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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

	private final static String START_TOKEN = "Starting Neo4j.\n";

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
			Path tempFile = null;
			try {
				tempFile = Files.createTempFile(logFile.name, ".log");
				copyFileFromContainer(container, "/logs/" + logFile.name, tempFile.toAbsolutePath().toString());
				try (Stream<String> lines = Files.lines(tempFile)) {
					return handler.apply(lines.skip(offset));
				}
			} finally {
				if (tempFile != null) {
					Files.deleteIfExists(tempFile);
				}
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

	@Override
	public boolean isContainerRunning() {
		return container.isRunning();
	}

	@Override
	public Type getType() {
		return type;
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

	/**
	 * Copies a file which resides inside the container to user defined directory
	 * N.b. The {@link org.testcontainers.containers.ContainerState} method of the same name throws an error when run
	 * against a stopped container - but it is in fact possible to copy a file from a stopped container.
	 *
	 * @param containerPath   path to file which is copied from container
	 * @param destinationPath destination path to which file is copied with file name
	 * @throws IOException if there's an issue communicating with Docker or receiving entry from TarArchiveInputStream
	 */
	private static void copyFileFromContainer(Neo4jContainer<?> container, String containerPath, String destinationPath)
		throws IOException {
		try (
			DockerClient dockerClient = container.getDockerClient();
			InputStream inputStream = dockerClient
				.copyArchiveFromContainerCmd(container.getContainerId(), containerPath).exec();
			TarArchiveInputStream tarInputStream = new TarArchiveInputStream(inputStream);
			FileOutputStream output = new FileOutputStream(destinationPath);
		) {
			tarInputStream.getNextTarEntry();
			byte[] buffer = new byte[8 * 1024];
			int len;
			while ((len = inputStream.read(buffer)) > 0) {
				output.write(buffer, 0, len);
			}
		}
	}
}
