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
import java.util.Collection;

import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.SocatContainer;

/**
 * This allows us to interact with the Neo4j Causal Cluster
 *
 * @author Michael J. Simons
 */
class CausalCluster implements CloseableResource {

	private final SocatContainer boltProxy;
	private final Collection<Neo4jContainer> clusterMembers;
	private final int boltPort;

	CausalCluster(SocatContainer boltProxy, int boltPort, Collection<Neo4jContainer> clusterMembers) {

		this.boltProxy = boltProxy;
		this.clusterMembers = clusterMembers;
		this.boltPort = boltPort;
	}

	public URI getURI() {
		return URI.create(String.format(
			"neo4j://%s:%d",
			boltProxy.getContainerIpAddress(),
			boltProxy.getMappedPort(boltPort)
		));
	}

	@Override
	public void close() {

		boltProxy.stop();
		clusterMembers.forEach(GenericContainer::stop);
	}
}
