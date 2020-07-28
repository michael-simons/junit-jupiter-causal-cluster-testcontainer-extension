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

import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.SocatContainer;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.stream.Collectors.toList;

/**
 * This allows us to interact with the Neo4j Causal Cluster
 *
 * @author Michael J. Simons
 */
class CausalCluster implements Cluster, CloseableResource {

	private final SocatContainer boltProxy;
	private final List<Neo4jCore> clusterCores;

	CausalCluster(SocatContainer boltProxy, List<Neo4jCore> clusterCores) {

		this.boltProxy = boltProxy;
		this.clusterCores = clusterCores;
	}

	public URI getURI() {
		// Choose a random bolt port from the available ports
		Neo4jCore core = clusterCores.get(ThreadLocalRandom.current().nextInt(0, clusterCores.size()));
		return core.getNeo4jUri();
	}

	public List<URI> getURIs() {
		return clusterCores.stream().map(Neo4jCore::getNeo4jUri).collect(toList());
	}

	@Override
	public void close() {

		boltProxy.stop();
		clusterCores.forEach(Neo4jCore::close);
	}

	@Override
	public Set<Neo4jCore> getAllCores() {
		return new HashSet<>(clusterCores);
	}
}
