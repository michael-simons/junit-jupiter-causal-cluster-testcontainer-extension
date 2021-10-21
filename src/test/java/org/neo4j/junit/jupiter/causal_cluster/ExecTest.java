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

import java.net.URI;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.junit.jupiter.causal_cluster.Neo4jServer.Type;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Neo4jContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Andrew Jefferson
 */

class ExecTest {

	@ParameterizedTest
	@ValueSource(strings = { "3.5", "4.0", "4.1", "4.2", CausalClusterExtension.DEFAULT_NEO4J_VERSION })
	void execInContainerShouldWork(String neo4jVersion) {

		try (ServerWrapper serverWrapper = new ServerWrapper(neo4jVersion)) {

			Neo4jServer server = serverWrapper.server;
			Container.ExecResult result = server.execInContainer("neo4j-admin", "--version");

			// some versions print "neo4j-admin <version>" others just print "<version>"
			String stdout = result.getStdout().replace("neo4j-admin", "").trim();

			assertThat(stdout).startsWith(neo4jVersion);
			assertThat(result.getStderr()).isEmpty();
			assertThat(result.getExitCode()).isEqualTo(0);
		} catch (Exception e) {
			Assertions.fail(e);
		}
	}

	private static class ServerWrapper implements AutoCloseable {

		private final Neo4jContainer<?> container;
		private final DefaultNeo4jServer server;

		ServerWrapper(String neo4jVersion) {
			this.container = new Neo4jContainer<>(
				String.format("neo4j:%s-enterprise", neo4jVersion))
				.withoutAuthentication()
				.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes");

			this.container.start();

			this.server = new DefaultNeo4jServer(container, URI.create(container.getBoltUrl()), Type.CORE_SERVER);
		}

		@Override
		public void close() {
			try {
				this.server.close();
			} finally {
				this.container.close();
			}
		}
	}
}
