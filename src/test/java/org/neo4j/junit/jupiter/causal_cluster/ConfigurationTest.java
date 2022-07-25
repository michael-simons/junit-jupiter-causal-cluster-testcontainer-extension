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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jLabsPlugin;

/**
 * @author Michael J. Simons
 */
class ConfigurationTest {

	@Test
	void emptyPluginsShouldWork() {
		Configuration configuration = CausalClusterExtension.DEFAULT_CONFIGURATION;
		assertThat(configuration.withPlugins()).isSameAs(configuration);
		assertThat(configuration.withPlugins((Neo4jLabsPlugin[]) null)).isSameAs(configuration);
	}

	@Test
	void pluginsShouldWork() {
		Configuration configuration = CausalClusterExtension.DEFAULT_CONFIGURATION;
		Configuration newConfiguration = configuration.withPlugins(Neo4jLabsPlugin.APOC);
		assertThat(newConfiguration).isNotSameAs(configuration);
		assertThat(newConfiguration.getPlugins()).containsExactly(Neo4jLabsPlugin.APOC);
	}
}
