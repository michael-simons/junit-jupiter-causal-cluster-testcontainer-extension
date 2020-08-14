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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.SocatContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

/**
 * @author Michael J. Simons
 * @author Andrew Jefferson
 */
final class DefaultNeo4jCluster implements Neo4jCluster, CloseableResource {

	private final static String NEO4J_CONTAINER_START_MESSAGE = "======== Neo4j";
	private final static String NEO4J_BOLT_UP_MESSAGE = "Bolt enabled on";
	private final static String NEO4J_STOPPED_MESSAGE = "Stopped.\n";

	private final static Duration NEO4J_CONTAINER_START_TIMEOUT = Duration.ofMinutes(3);
	private final static Duration NEO4J_CONTAINER_STOP_TIMEOUT = Duration.ofMinutes(2);

	private final SocatContainer boltProxy;
	private final List<DefaultNeo4jServer> clusterServers;

	DefaultNeo4jCluster(SocatContainer boltProxy, List<DefaultNeo4jServer> clusterServers) {

		this(boltProxy, clusterServers, Collections.emptyList());
	}

	DefaultNeo4jCluster(
		SocatContainer boltProxy, List<DefaultNeo4jServer> clusterServers,
		List<DefaultNeo4jServer> readReplicaServers
	) {

		this.boltProxy = boltProxy;
		this.clusterServers = new ArrayList<>(clusterServers.size() + readReplicaServers.size());
		this.clusterServers.addAll(clusterServers);
		this.clusterServers.addAll(readReplicaServers);
	}

	@Override
	public void close() {

		boltProxy.close();
		clusterServers.forEach(DefaultNeo4jServer::close);
	}

	@Override
	public Set<Neo4jServer> getAllServers() {
		return new HashSet<>(clusterServers);
	}

	@Override
	public Set<Neo4jServer> getAllServersExcept(Set<Neo4jServer> exclusions) {

		return clusterServers.stream().filter(with(exclusions)).collect(Collectors.toSet());
	}

	static private Predicate<DefaultNeo4jServer> with(Set<Neo4jServer> excludedServers) {
		return c -> !excludedServers.contains(c);
	}

	@Override
	public Set<Neo4jServer> stopRandomServers(int n) {
		return stopRandomServersExcept(n, Collections.emptySet());
	}

	@Override
	public Set<Neo4jServer> stopRandomServersExcept(int n, Set<Neo4jServer> exclusions) {
		List<Neo4jServer> chosenServers = chooseRandomServers(n, exclusions);

		return doWithServers(chosenServers, server -> {
			Neo4jContainer<?> container = unwrap(server);

			WaitingConsumer consumer = new WaitingConsumer();
			container.followOutput(consumer, OutputFrame.OutputType.STDOUT);

			int timeoutSeconds = Math.toIntExact(NEO4J_CONTAINER_STOP_TIMEOUT.toMillis() / 1000);
			container.getDockerClient().stopContainerCmd(container.getContainerId())
				.withTimeout(timeoutSeconds)
				.exec();

			try {
				consumer
					.waitUntil(frame -> frame.getUtf8String().contains(NEO4J_STOPPED_MESSAGE), timeoutSeconds,
						TimeUnit.SECONDS);
				waitUntilContainerIsStopped(container);
			} catch (TimeoutException e) {
				throw new RuntimeException(e);
			}

			return server;
		}).collect(Collectors.toSet());
	}

	@Override
	public Set<Neo4jServer> startServers(Set<Neo4jServer> servers) {
		return doWithServers(servers, server -> {
			Neo4jContainer<?> container = unwrap(server);
			assert !container.isRunning();

			/*
			The test container host ports are no longer valid after a container restart so port based wait strategies don't work

			e.g. these both fail:
			WaitStrategy ws = new HttpWaitStrategy().forPort( 7474 ).forStatusCodeMatching( ( response) -> response == 200 );
			WaitStrategy ws = new HostPortWaitStrategy...;

			And anyway, because we use this for cluster failure testing we can't guarantee that the http or bolt server will ever start.
			so instead we just wait for an indication in the logs that the neo4j process is started.

			It's up to the user to block until bolt is available if that is their expectation/requirement in tests.
			 */
			WaitStrategy ws = WaitForLogMessageAfter
				.waitForLogMessageAfterRestart(NEO4J_CONTAINER_START_MESSAGE, server);

			container.getDockerClient().startContainerCmd(container.getContainerId()).exec();

			try {
				waitUntilContainerIsStarted(container);
			} catch (TimeoutException e) {
				throw new RuntimeException(e);
			}

			ws.withStartupTimeout(NEO4J_CONTAINER_START_TIMEOUT).waitUntilReady(container);

			return server;

		}).collect(Collectors.toSet());
	}

	@Override
	public void waitForLogMessageOnAll(Set<Neo4jServer> servers, String message, Duration timeout) throws
		Neo4jTimeoutException {

		Instant deadline = Instant.now().plus(timeout);

		// TODO can we implement early stopping if any of these fail?
		List<? extends Exception> errors = doWithServers(servers, (server) -> {
			try {
				Neo4jContainer<?> container = unwrap(server);
				if (!container.isRunning()) {
					return new IllegalStateException(
						"Server is not running. Cannot wait for logs on a non running server");
				}
				WaitForLogMessageAfter.waitForMessageInLatestLogs(message, server)
					.withStartupTimeout(Duration.between(Instant.now(), deadline))
					.waitUntilReady(container);
				return null;
			} catch (WaitForLogMessageAfter.NotFoundException e) {
				return e;
			}
		}).filter(Objects::nonNull).collect(Collectors.toList());

		if (!errors.isEmpty()) {
			// TODO aggregate all the errors properly

			// prioritise throwing any IllegalStateException
			for (Exception e : errors) {
				if (e instanceof IllegalStateException) {
					throw (IllegalStateException) e;
				}
			}

			// Otherwise wrap and throw the NotFoundException
			throw new Neo4jTimeoutException(errors.get(0));
		}
	}

	@Override
	public void waitForBoltOnAll(Set<Neo4jServer> servers, Duration timeout)
		throws Neo4jTimeoutException {
		Instant deadline = Instant.now().plus(timeout);
		waitForLogMessageOnAll(servers, NEO4J_BOLT_UP_MESSAGE, timeout);

		List<Neo4jServer> unreachableServers = doWithServers(servers, (server) -> {
			Duration remainingTime = Duration.between(Instant.now(), deadline);
			return new BoltHandshaker(server).isBoltPortReachable(remainingTime) ? null : server;
		}).filter(Objects::nonNull).collect(Collectors.toList());

		if (!unreachableServers.isEmpty()) {
			String message = String.format(
				"Bolt port does not respond to handshake after logging that it is available on [%s]",
				unreachableServers.stream().map(s -> s.getURI().getHost()).collect(Collectors.joining(",")));
			throw new IllegalStateException(message);
		}
	}

	// -- Private Methods --

	private Neo4jContainer<?> unwrap(Neo4jServer server) {
		DefaultNeo4jServer impl = clusterServers.stream()
			.filter(server::equals)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Provided server does not exist in this cluster"));

		return impl.unwrap();
	}

	private void waitUntilContainerIsStopped(Neo4jContainer<?> container) throws TimeoutException {
		Instant deadline = Instant.now().plus(NEO4J_CONTAINER_STOP_TIMEOUT);

		while (container.isRunning()) {
			if (Instant.now().isAfter(deadline)) {
				throw new TimeoutException(
					"Timed out waiting for docker container to stop. " + container.getContainerId());
			}

			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void waitUntilContainerIsStarted(Neo4jContainer<?> container) throws TimeoutException {
		Instant deadline = Instant.now().plus(NEO4J_CONTAINER_START_TIMEOUT);

		while (!container.isRunning()) {
			if (Instant.now().isAfter(deadline)) {
				throw new TimeoutException(
					"Timed out waiting for docker container to start. " + container.getContainerId());
			}

			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private Stream<Neo4jServer> randomServers(int n) {
		return randomServers(n, Collections.emptySet());
	}

	private Stream<Neo4jServer> randomServers(int n, Set<Neo4jServer> exclusions) {

		List<Neo4jServer> candidates = clusterServers.stream().filter(with(exclusions)).collect(
			Collectors.toList());
		int N = candidates.size();
		if (n > N) {
			throw new IllegalArgumentException("There are not enough valid members in the cluster.");
		} else if (n == N) {
			return candidates.stream();
		}

		Set<Neo4jServer> chosen = new HashSet<>(n);
		while (chosen.size() < n) {
			chosen.add(candidates.get(ThreadLocalRandom.current().nextInt(0, N)));
		}

		return chosen.stream();
	}

	private List<Neo4jServer> chooseRandomServers(int n) {
		return randomServers(n).collect(Collectors.toList());
	}

	private List<Neo4jServer> chooseRandomServers(int n, Set<Neo4jServer> excluding) {
		return randomServers(n, excluding).collect(Collectors.toList());
	}

	/**
	 * Performs an operation in parallel on the provided containers.
	 * This method does not return until the operation has completed on ALL containers.
	 *
	 * @param Servers Neo4jServers to operate on
	 * @param fn      operation to perform
	 * @param <T>     operation return type
	 * @return A Stream of the operation results
	 */
	private static <T> Stream<T> doWithServers(Collection<Neo4jServer> Servers, Function<Neo4jServer, T> fn) {

		// TODO better error handling
		final CountDownLatch latch = new CountDownLatch(Servers.size());

		// TODO use an explicit Executor instead of dropping it on the shared FJP so we can keep track of better manage tasks.
		List<CompletableFuture<T>> futures = Servers.stream()
			.map(server -> CompletableFuture.supplyAsync(() -> {
				try {
					return fn.apply(server);
				} finally {
					latch.countDown();
				}
			})).collect(Collectors.toList());

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		return futures.stream().map(CompletableFuture::join);
	}
}
