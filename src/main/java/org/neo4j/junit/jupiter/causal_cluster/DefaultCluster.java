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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.SocatContainer;

/**
 * @author Michael J. Simons
 */
final class DefaultCluster implements Neo4jCluster, CloseableResource {

	private final SocatContainer boltProxy;
	private final List<DefaultNeo4jServer> servers;

	DefaultCluster(SocatContainer boltProxy, List<DefaultNeo4jServer> servers) {

		this.boltProxy = boltProxy;
		this.servers = servers;
	}

	@Override
	public URI getURI() {
		// Choose a random bolt port from the available ports
		DefaultNeo4jServer core = servers.get(ThreadLocalRandom.current().nextInt(0, servers.size()));
		return core.getURI();
	}

	@Override
	public void close() {

		boltProxy.stop();
		servers.forEach(DefaultNeo4jServer::close);
	}

	@Override
	public Set<Neo4jServer> getAllServers() {
		return new HashSet<>(servers);
	}
}
