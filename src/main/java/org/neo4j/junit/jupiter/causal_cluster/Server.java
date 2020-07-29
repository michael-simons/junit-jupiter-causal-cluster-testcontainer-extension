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

/**
 * This is a Neo4j instance that is a part of causal cluster, with {@literal Server} being used equally for Core and
 * Replica Servers as defined in
 * <a href="https://neo4j.com/docs/operations-manual/current/clustering/introduction/">the Neo4j introduction to clustering</a>.
 *
 * @author Michael J. Simons
 */
public interface Server {

	/**
	 * @return An URI into this server.
	 */
	URI getURI();
}