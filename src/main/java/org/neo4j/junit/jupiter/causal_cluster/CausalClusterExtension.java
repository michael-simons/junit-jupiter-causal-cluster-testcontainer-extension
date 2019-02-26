/*
 * Copyright (c) 2019 "Neo4j,"
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Duration;
import java.util.function.Predicate;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;

/**
 * Provides exactly one instance of {@link CausalCluster}.
 *
 * @author Michael J. Simons
 */
class CausalClusterExtension implements BeforeAllCallback {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
		.create(CausalClusterExtension.class);

	static final String DEFAULT_NEO4J_VERSION = "3.5.3";
	static final int DEFAULT_NUMBER_OF_CORE_MEMBERS = 3;
	static final int DEFAULT_NUMBER_OF_READ_REPLICAS = 0;
	static final long DEFAULT_STARTUP_TIMEOUT_IN_MILLIS = 5 * 60 * 1_000;
	static final String DEFAULT_PASSWORD = "password";

	private static final String KEY_NUMBER_OF_CORE_MEMBERS = "numberOfCoreMembers";
	private static final String KEY_NEO4J_VERSION = "neo4jVersion";
	private static final String KEY_STARTUP_TIMEOUT = "startupTimeout";
	private static final String KEY_PASSWORD = "password";
	private static final String KEY = "neo4j.causalCluster";

	public void beforeAll(ExtensionContext context) {

		AnnotationSupport.findAnnotation(context.getRequiredTestClass(), NeedsCausalCluster.class)
			.ifPresent(annotation -> {
				ExtensionContext.Store store = context.getStore(NAMESPACE);
				store.put(KEY_NUMBER_OF_CORE_MEMBERS, annotation.numberOfCoreMembers());
				store.put(KEY_NEO4J_VERSION, annotation.neo4jVersion());
				store.put(KEY_STARTUP_TIMEOUT, annotation.startupTimeOutInMillis());
				store.put(KEY_PASSWORD, annotation.password());
			});

		injectFields(context, context.getTestInstances().map(TestInstances::getInnermostInstance).orElse(null));
	}

	private void injectFields(ExtensionContext context, Object testInstance) {

		Predicate<Field> selectedFields = field -> true;
		if (testInstance == null) {
			selectedFields = field -> Modifier.isStatic(field.getModifiers());
		}

		AnnotationSupport.findAnnotatedFields(context.getRequiredTestClass(), Neo4jUri.class, selectedFields,
			HierarchyTraversalMode.TOP_DOWN).forEach(field -> {
			assertSupportedType("field", field.getType());
			field.setAccessible(true);
			try {
				field.set(testInstance, createCausalCluster(field.getType(), context));
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static void assertSupportedType(String target, Class<?> type) {
		if (type != URI.class && type != String.class) {
			throw new ExtensionConfigurationException(String
				.format("Can only resolve @%s %s of type %s or %s but was: %s", Neo4jUri.class.getSimpleName(), target,
					URI.class.getName(), String.class.getName(), type.getName()));
		}
	}

	private static Object createCausalCluster(Class<?> type, ExtensionContext extensionContext) {

		ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);

		String neo4jVersion = store.getOrComputeIfAbsent(KEY_NEO4J_VERSION, key -> DEFAULT_NEO4J_VERSION, String.class);
		int numberOfCoreMembers = store
			.getOrComputeIfAbsent(KEY_NUMBER_OF_CORE_MEMBERS, key -> DEFAULT_NUMBER_OF_CORE_MEMBERS, int.class);
		int numberOfReadReplicas = DEFAULT_NUMBER_OF_READ_REPLICAS;
		Duration startupTimeout = Duration.ofMillis(
			store.getOrComputeIfAbsent(KEY_STARTUP_TIMEOUT, key -> DEFAULT_STARTUP_TIMEOUT_IN_MILLIS, long.class));
		String password = store.getOrComputeIfAbsent(KEY_PASSWORD, key -> DEFAULT_PASSWORD, String.class);

		Configuration configuration = new Configuration(neo4jVersion, numberOfCoreMembers, numberOfReadReplicas,
			startupTimeout, password);

		URI uri = extensionContext.getStore(NAMESPACE)
			.getOrComputeIfAbsent(KEY, key -> CausalCluster.start(configuration), CausalCluster.class).getURI();

		return type == URI.class ? uri : uri.toString();
	}
}
