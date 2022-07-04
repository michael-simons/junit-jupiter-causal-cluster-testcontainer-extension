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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Registers the Causal Cluster extension.
 *
 * @author Michael J. Simons
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(CausalClusterExtension.class)
public @interface NeedsCausalCluster {

	/**
	 * @return The number of core servers. A minimum number of 3 is required.
	 */
	int numberOfCoreServers() default CausalClusterExtension.DEFAULT_NUMBER_OF_CORE_SERVERS;

	/**
	 * @return The number of read replicas. A minimum number of 0 is required.
	 */
	int numberOfReadReplicas() default CausalClusterExtension.DEFAULT_NUMBER_OF_READ_REPLICAS;

	/**
	 * @return A valid Neo4j version number.
	 */
	String neo4jVersion() default CausalClusterExtension.DEFAULT_NEO4J_VERSION;

	/**
	 * @return A completely custom image name. Must refer to a Neo4j enterprise image.
	 */
	String customImageName() default "";

	/**
	 * @return Startup timeout for the cluster. Defaults to 5 minutes.
	 */
	long startupTimeOutInMillis() default CausalClusterExtension.DEFAULT_STARTUP_TIMEOUT_IN_MILLIS;

	/**
	 * @return The password to use for bolt connections into the cluster.
	 */
	String password() default "password";
}
