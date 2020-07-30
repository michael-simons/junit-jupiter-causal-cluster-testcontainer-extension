/*
 * Copyright (c) 2019-2020 "Neo4j,"
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

import java.io.Serializable;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The clusters configuration.
 *
 * @author Michael J. Simons
 */
final class Configuration implements Serializable {

	private final String neo4jVersion;

	private final int numberOfCoreMembers;

	private final int numberOfReadReplicas;

	private final Duration startupTimeout;

	private final String password;

	private final int initialHeapSize;

	private final int pagecacheSize;

	/**
	 * Optional custom image name. Has precedence of the version number if set.
	 */
	private final String customImageName;

	Configuration(String neo4jVersion, int numberOfCoreMembers, int numberOfReadReplicas,
		Duration startupTimeout, String password, int initialHeapSize, int pagecacheSize, String customImageName) {
		this.neo4jVersion = neo4jVersion;
		this.numberOfCoreMembers = numberOfCoreMembers;
		this.numberOfReadReplicas = numberOfReadReplicas;
		this.startupTimeout = startupTimeout;
		this.password = password;
		this.initialHeapSize = initialHeapSize;
		this.pagecacheSize = pagecacheSize;
		this.customImageName = customImageName;
	}

	/**
	 * Get the core index and hostname for all configured cores
	 * @return A stream mapping a core index (integer) -> hostname (String)
	 */
	Stream<Map.Entry<Integer, String>> iterateCoreMembers() {
		final IntFunction<String> generateInstanceName = i -> String.format("neo4j%d", i);

		return IntStream.rangeClosed(1, numberOfCoreMembers)
			.mapToObj(i -> new AbstractMap.SimpleEntry<>(i - 1, generateInstanceName.apply(i)));
	}

	String getImageName() {
		return Optional.ofNullable(customImageName).filter(s -> !s.isEmpty())
			.orElseGet(() -> String.format("neo4j:%s-enterprise", neo4jVersion));
	}

	public String getNeo4jVersion() {
		return this.neo4jVersion;
	}

	public int getNumberOfCoreMembers() {
		return this.numberOfCoreMembers;
	}

	public int getNumberOfReadReplicas() {
		return this.numberOfReadReplicas;
	}

	public Duration getStartupTimeout() {
		return this.startupTimeout;
	}

	public String getPassword() {
		return this.password;
	}

	public int getInitialHeapSize() {
		return this.initialHeapSize;
	}

	public int getPagecacheSize() {
		return this.pagecacheSize;
	}

	public String getCustomImageName() {
		return this.customImageName;
	}

	public Configuration withNeo4jVersion(String newNeo4jVersion) {
		return this.neo4jVersion == newNeo4jVersion ?
			this :
			new Configuration(newNeo4jVersion, this.numberOfCoreMembers, this.numberOfReadReplicas, this.startupTimeout,
				this.password, this.initialHeapSize, this.pagecacheSize, this.customImageName);
	}

	public Configuration withNumberOfCoreMembers(int newNumberOfCoreMembers) {
		return this.numberOfCoreMembers == newNumberOfCoreMembers ?
			this :
			new Configuration(this.neo4jVersion, newNumberOfCoreMembers, this.numberOfReadReplicas, this.startupTimeout,
				this.password, this.initialHeapSize, this.pagecacheSize, this.customImageName);
	}

	public Configuration withNumberOfReadReplicas(int newNumberOfReadReplicas) {
		return this.numberOfReadReplicas == newNumberOfReadReplicas ?
			this :
			new Configuration(this.neo4jVersion, this.numberOfCoreMembers, newNumberOfReadReplicas, this.startupTimeout,
				this.password, this.initialHeapSize, this.pagecacheSize, this.customImageName);
	}

	public Configuration withStartupTimeout(Duration newStartupTimeout) {
		return this.startupTimeout == newStartupTimeout ?
			this :
			new Configuration(this.neo4jVersion, this.numberOfCoreMembers, this.numberOfReadReplicas, newStartupTimeout,
				this.password, this.initialHeapSize, this.pagecacheSize, this.customImageName);
	}

	public Configuration withPassword(String newPassword) {
		return this.password == newPassword ?
			this :
			new Configuration(this.neo4jVersion, this.numberOfCoreMembers, this.numberOfReadReplicas,
				this.startupTimeout, newPassword, this.initialHeapSize, this.pagecacheSize, this.customImageName);
	}

	public Configuration withInitialHeapSize(int newInitialHeapSize) {
		return this.initialHeapSize == newInitialHeapSize ?
			this :
			new Configuration(this.neo4jVersion, this.numberOfCoreMembers, this.numberOfReadReplicas,
				this.startupTimeout, this.password, newInitialHeapSize, this.pagecacheSize, this.customImageName);
	}

	public Configuration withPagecacheSize(int newPagecacheSize) {
		return this.pagecacheSize == newPagecacheSize ?
			this :
			new Configuration(this.neo4jVersion, this.numberOfCoreMembers, this.numberOfReadReplicas,
				this.startupTimeout, this.password, this.initialHeapSize, newPagecacheSize, this.customImageName);
	}

	public Configuration withCustomImageName(String newCustomImageName) {
		return this.customImageName == newCustomImageName ?
			this :
			new Configuration(this.neo4jVersion, this.numberOfCoreMembers, this.numberOfReadReplicas,
				this.startupTimeout, this.password, this.initialHeapSize, this.pagecacheSize, newCustomImageName);
	}

	public String toString() {
		return "Configuration(neo4jVersion=" + this.getNeo4jVersion() + ", numberOfCoreMembers=" + this
			.getNumberOfCoreMembers() + ", numberOfReadReplicas=" + this.getNumberOfReadReplicas() + ", startupTimeout="
			+ this.getStartupTimeout() + ", password=" + this.getPassword() + ", initialHeapSize=" + this
			.getInitialHeapSize() + ", pagecacheSize=" + this.getPagecacheSize() + ", customImageName=" + this
			.getCustomImageName() + ")";
	}
}
