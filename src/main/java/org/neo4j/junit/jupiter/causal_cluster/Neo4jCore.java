package org.neo4j.junit.jupiter.causal_cluster;

import java.net.URI;
import java.util.Objects;

import org.testcontainers.containers.Neo4jContainer;

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
