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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.testcontainers.containers.Neo4jContainer;

/**
 * @author Andrew Jefferson
 * @author Michael J. Simons
 */
final class DefaultNeo4jServer implements Neo4jServer, AutoCloseable {

	private final static ScriptEngine SCRIPT_ENGINE;

	static {
		ScriptEngineManager sem = new ScriptEngineManager();
		SCRIPT_ENGINE = sem.getEngineByName("javascript");
	}

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

	@Override
	public boolean isContainerRunning() {
		return container.isRunning();
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public List<String> getRolesFor(String database) {

		CharSequence response = callDbmsClusterRole(this.container.getHttpUrl() + "/db/system/tx/commit", authToken,
			database);
		String result = evaluateResponse(response);

		if (result.startsWith("Error: ")) {
			throw new RuntimeException(result.substring(result.indexOf(" ") + 1));
		} else {
			return Arrays.stream(result.split(",")).map(String::trim).collect(Collectors.toList());
		}
	}

	private static CharSequence callDbmsClusterRole(String txEndpoint, String authToken, String database) {

		try {
			HttpURLConnection con = (HttpURLConnection) new URL(txEndpoint).openConnection();

			con.setDoOutput(true);
			con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("Authorization", authToken);

			try (OutputStream os = con.getOutputStream()) {
				String payload =
					"{\"statements\":[{\"statement\":\"CALL dbms.cluster.role($databaseName)\",\"parameters\":{\"databaseName\":\""
						+ database + "\"}}]}";
				byte[] input = payload.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
				os.flush();
			}

			con.connect();
			int status = con.getResponseCode();
			if (status != 200) {
				throw new RuntimeException(status + ": " + con.getResponseMessage());
			}

			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
				return reader.lines().collect(Collectors.joining());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String evaluateResponse(CharSequence response) {

		String scriptTemplate = "var response = JSON.parse(response);\n"
			+ "if(response.errors.length > 0) {\n"
			+ "    function x(error) {\n"
			+ "        return error.message + \" (\" + error.code + \")\"\n"
			+ "    }\n"
			+ "    \"Error: \" + response.errors.map(x).join(\",\");"
			+ "} else {\n"
			+ "    response.results[0].data[0].row.join(\",\");\n"
			+ "}";
		Bindings parameter = SCRIPT_ENGINE.createBindings();
		parameter.put("response", response);
		try {
			return (String) SCRIPT_ENGINE.eval(scriptTemplate, parameter);
		} catch (ScriptException e) {
			throw new RuntimeException(e);
		}
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
