///usr/bin/env jbang "$0" "$@" ; exit $?
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

//DEPS info.picocli:picocli:4.2.0
//DEPS eu.michael-simons.neo4j:junit-jupiter-causal-cluster-testcontainer-extension:2020.0.7
//DEPS org.slf4j:slf4j-simple:1.7.30

package org.neo4j.junit.jupiter.causal_cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.UnaryOperator;

/**
 * Starts a Neo4j causal cluster based on the JUnit extension in this repository.
 * The defaults are the same as the extension has, all configuration options available
 * via {@link NeedsCausalCluster @NeedsCausalCluster} are available.
 *
 * @author Michael J. Simons
 */
@Command(name = "start",
		description = "Starts a Neo4j causal cluster")
public final class start implements Callable<Integer> {

	private final Logger log = LoggerFactory.getLogger(start.class);

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
	boolean usageHelpRequested;

	@Option(names = {"--accept-license"}, description = "Using a Neo4j Enterprise Docker image will require you to accept the official Enterprise license agreement.")
	boolean acceptLicense = false;

	@Option(names = "--neo4jVersion", description = "Configure the Neo4j version being used (all released 4.x versions are supported).")
	private String neo4jVersion = CausalClusterExtension.DEFAULT_NEO4J_VERSION;

	@Option(names = "--number-of-core-servers", description = "Configure the number of core servers")
	private int numberOfCoreServers = CausalClusterExtension.DEFAULT_NUMBER_OF_CORE_SERVERS;

	@Option(names = "--number-of-read-replicas", description = "Configure the number of read replicas")
	private int numberOfReadReplicas = CausalClusterExtension.DEFAULT_NUMBER_OF_READ_REPLICAS;

	@Option(names = "--startup-timeout", description = "How long shall we wait for the cluster to form?")
	private Duration startupTimeout = Duration.ofMillis(CausalClusterExtension.DEFAULT_STARTUP_TIMEOUT_IN_MILLIS);

	@Option(names = "--password", description = "The server password to configure.")
	private String password = CausalClusterExtension.DEFAULT_PASSWORD;

	@Option(names = "--initial-heap-size", description = "Initial heap size per server in MB.")
	private int initialHeapSize = CausalClusterExtension.DEFAULT_HEAP_SIZE_IN_MB;

	@Option(names = "--pagecache-size", description = "Pagecache size per server in MB.")
	private int pagecacheSize = CausalClusterExtension.DEFAULT_PAGE_CACHE_IN_MB;

	public static void main(String... args) {

		System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
		System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false");
		System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");

		int exitCode = new CommandLine(new start()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {

		if (!acceptLicense) {
			log.warn("Using a Neo4j Enterprise Docker image will require you to accept the official Enterprise license agreement.");
			log.warn("Please call this script with `--accept-license`.");
			return CommandLine.ExitCode.USAGE;
		}

		CountDownLatch latch = new CountDownLatch(1);
		log.info("Starting causal cluster, please be patient.");
		Configuration configuration = new Configuration(neo4jVersion,
				numberOfCoreServers,
				numberOfReadReplicas, startupTimeout, password,
				initialHeapSize, pagecacheSize, UnaryOperator.identity(), UnaryOperator.identity(),
				null);
		Neo4jCluster cluster = new ClusterFactory(configuration).createCluster();
		log.info("Cluster is running.");
		log.info("Core servers:");
		cluster.getAllServersOfType(Neo4jServer.Type.CORE_SERVER)
				.forEach(s -> log.info("{} (bolt direct: {})", s.getURI(), s.getDirectBoltUri()));

		Set<Neo4jServer> replicas = cluster.getAllServersOfType(Neo4jServer.Type.REPLICA_SERVER);
		if (!replicas.isEmpty()) {
			log.info("Replica servers:");
			replicas.forEach(s -> log.info("{} (bolt direct: {})", s.getURI(), s.getDirectBoltUri()));
		}
		log.info("Press Ctrl+C to stop the cluster.");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			((DefaultNeo4jCluster) cluster).close();
			latch.countDown();
		}));
		latch.await();
		return CommandLine.ExitCode.OK;
	}
}
