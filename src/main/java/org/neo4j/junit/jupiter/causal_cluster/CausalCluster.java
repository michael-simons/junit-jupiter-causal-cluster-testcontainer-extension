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

import java.net.URI;
import java.util.Set;

/**
 * This allows us to interact with a Neo4j Causal Cluster.
 *
 * @author Andrew Jefferson
 * @author Michael J. Simons
 */
public interface CausalCluster {

	/**
	 * @return An URI into that cluster.
	 */
	URI getURI();

	/**
	 * @return The Neo4j instances contained by this cluster.
	 */
	Set<Neo4jCore> getAllCores();
}
