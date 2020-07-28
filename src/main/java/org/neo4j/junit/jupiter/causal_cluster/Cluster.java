package org.neo4j.junit.jupiter.causal_cluster;

import java.util.Set;

public interface Cluster {

	Set<Neo4jCore> getAllCores();
}
