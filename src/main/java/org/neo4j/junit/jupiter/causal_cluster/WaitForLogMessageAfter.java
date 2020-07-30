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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.utility.LogUtils;

public class WaitForLogMessageAfter extends AbstractWaitStrategy {

	protected final static Pattern startsWithATimestampPattern = Pattern.compile(
		"^\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{1,4}.*$",
		Pattern.MULTILINE
	);
	private static final int neo4J_timestamp_length = 29;

	// TODO: support making the query a regex?
	private final String query;
	private final WaitPredicate waitPredicate;

	public WaitForLogMessageAfter(String query, String afterLine) {

		this.query = query;

		if (!lineStartsWithATimestamp(afterLine)) {
			String message = "Queries must match neo4j log output that prints with a preceding timestamp."
				+ " This was not satisfied for '" + afterLine + "'";
			throw new IllegalArgumentException(message);
		}

		if (query.contains("\n")) {
			throw new IllegalArgumentException("log queries cannot span multiple lines");
		}

		this.waitPredicate = new WaitPredicate(afterLine, query);
	}

	@Override
	protected void waitUntilReady() {

		WaitingConsumer waitingConsumer = new WaitingConsumer();
		LogUtils.followOutput(
			DockerClientFactory.instance().client(),
			this.waitStrategyTarget.getContainerId(),
			waitingConsumer
		);

		// Now wait for the messages we are looking for to appear in the logs in the correct order
		try {
			waitingConsumer.waitUntil(waitPredicate, this.startupTimeout.getSeconds(), TimeUnit.SECONDS, 1);
		} catch (TimeoutException e) {
			throw new NotFoundException(
				"Timed out waiting for log output matching '" + query + "' after " + this.startupTimeout.toString());
		}

	}

	private static class WaitPredicate implements Predicate<OutputFrame> {
		private final String query;
		private final String afterTimestamp;

		private WaitPredicate(String afterThisLine, String query) {
			this.afterTimestamp = afterThisLine.trim().substring(0, neo4J_timestamp_length);
			this.query = query;
		}

		@Override
		public boolean test(OutputFrame outputFrame) {
			String utf8String = outputFrame.getUtf8String();

			for (String line : utf8String.split("\n")) {
				if (line.contains(query)) {
					if (!lineStartsWithATimestamp(line)) {
						String message = "log queries must match neo4j logs that include a preceding timestamp. "
							+ "This was not satisfied for '" + query + "'. Which matched: '" + line + "'";
						throw new IllegalStateException(message);
					}

					// After extensive testing I discovered that the log consumer does not always consume in
					// chronological order.
					// So the only way that I could figure out to be sure that the query line happened _after_
					// afterThisLine is to compare the neo4j timestamps
					String lineTimestamp = line.trim().substring(0, neo4J_timestamp_length);
					if (lineTimestamp.compareTo(afterTimestamp) > 0) {
						return true;
					}
				}
			}
			return false;
		}
	}

	private static boolean lineStartsWithATimestamp(String line) {
		return startsWithATimestampPattern.matcher(line).matches();
	}

	/**
	 * Only used internally to find logs that are after the last log line recorded _before_ the container is restarted.
	 */
	static WaitForLogMessageAfter lastTimestampedLine(String query, Neo4jServer server) {

		Matcher matcher = startsWithATimestampPattern.matcher(server.getLatestContainerLogs());
		String lastMatchingLine = null;

		// Get the last matching line, there doesn't seem to be a better method than looping over every match
		while (matcher.find()) {
			lastMatchingLine = matcher.group();
		}
		if (lastMatchingLine == null) {
			throw new IllegalStateException(
				"Container has no existing logs starting with a recognisable timestamp. Did Neo4j fail to start?");
		}
		return new WaitForLogMessageAfter(query, lastMatchingLine);
	}

	static class NotFoundException extends RuntimeException {

		NotFoundException(String message) {
			super(message);
		}
	}
}
