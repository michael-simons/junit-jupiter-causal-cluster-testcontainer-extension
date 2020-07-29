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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as an injection point for a Neo4j Causal Cluster.
 * The cluster will be started before all tests and be shutdown, including all servers, after the test.
 * The following list shows valid injection points:
 * <dl>
 *     <dt><pre>@CausalCluster String uri</pre></dt>
 *     <dd>Injects a random, externally accessible URI as a {@link String}.</dd>
 *     <dt><pre>@CausalCluster URI uri</pre></dt>
 *     <dd>Injects a random, externally accessible URI as an {@link java.net.URI}.</dd>
 *     <dt><pre>@CausalCluster List&lt;String&gt; uris</pre></dt>
 *     <dd>Injects all externally accessible URIs as {@link String strings}</dd>
 *     <dt><pre>@CausalCluster List&lt;URI&gt; uris</pre></dt>
 *     <dd>Injects all externally accessible URIs as {@link java.net.URI uris}</dd>
 *     <dt><pre>@CausalCluster Cluster cluster</pre></dt>
 *     <dd>Injects the complete cluster for advanced tests as {@link Cluster}</dd>
 * </dl>
 *
 * @author Michael J. Simons
 * @author Andrew Jefferson
 */
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CausalCluster {
}
