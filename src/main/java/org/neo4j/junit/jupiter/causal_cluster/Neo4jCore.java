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

import org.testcontainers.containers.Neo4jContainer;

import java.net.URI;
import java.util.Objects;

public class Neo4jCore implements AutoCloseable {

	private final Neo4jContainer<?> container;
	private final URI neo4jUri;

	public Neo4jCore(Neo4jContainer<?> container, URI neo4jUri) {
		this.container = container;
		this.neo4jUri = neo4jUri;
	}

	public URI getNeo4jUri() {
		return neo4jUri;
	}

	@Override public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Neo4jCore neo4jCore = (Neo4jCore) o;
		return getNeo4jUri().equals(neo4jCore.getNeo4jUri()) &&
			container.equals(neo4jCore.container);
	}

	@Override
	public int hashCode() {
		return Objects.hash(container, getNeo4jUri());
	}

	@Override
	public void close() {
		container.close();
	}

	Neo4jContainer<?> unwrap() {
		return container;
	}
}
