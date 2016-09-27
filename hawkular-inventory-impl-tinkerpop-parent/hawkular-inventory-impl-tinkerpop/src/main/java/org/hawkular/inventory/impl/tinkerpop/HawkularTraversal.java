/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.impl.tinkerpop;

import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__from;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.computer.VertexProgram;
import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.process.traversal.Pop;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalSideEffects;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.TraverserGenerator;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.BulkSet;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.Tree;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.util.TraverserSet;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalExplanation;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
import org.apache.tinkerpop.gremlin.structure.Column;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.hawkular.inventory.base.spi.Discriminator;
import org.hawkular.inventory.impl.tinkerpop.spi.Constants;

/**
 * @author Lukas Krejci
 * @since 0.20.0
 */
public class HawkularTraversal<S, E> implements GraphTraversal<S, E>, GraphTraversal.Admin<S, E> {

    private final GraphTraversal<S, E> delegate;

    public static <A, B> HawkularTraversal<A, B> hwk(GraphTraversal<A, B> tr) {
        return new HawkularTraversal<>(tr);
    }

    public static <A> HawkularTraversal<A, A> hwk__() {
        return hwk(__.<A>start());
    }

    public static <A> HawkularTraversal<A, A> hwk__(A... starts) {
        return hwk(__.__(starts));
    }

    public HawkularTraversal(GraphTraversal<S, E> delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("unchecked")
    public HawkularTraversal<Edge, Edge> restrictTo(Discriminator discriminator) {
        if (discriminator == null || discriminator.getTime() == null) {
            return (HawkularTraversal<Edge, Edge>) this;
        }

        long time = discriminator.getTime().toEpochMilli();
        return (HawkularTraversal<Edge, Edge>) this.has(__from.name(), P.lte(time))
                .has(Constants.Property.__to.name(), P.gt(time));
    }

    @SuppressWarnings("unchecked")
    public HawkularTraversal<Vertex, Vertex> existsAt(Discriminator discriminator) {
        return (HawkularTraversal<Vertex, Vertex>) where(hwk__().outE(Constants.InternalEdge.__inState.name())
                .restrictTo(discriminator));
    }

    @SuppressWarnings("unchecked")
    public HawkularTraversal<Vertex, Vertex> doesntExistAt(Discriminator discriminator) {
        if (discriminator == null || discriminator.getTime() == null) {
            return (HawkularTraversal<Vertex, Vertex>) this;
        }

        GraphTraversal<?, Edge> check = __.not(
                hwk__().outE(Constants.InternalEdge.__inState.name()).restrictTo(discriminator));

        return (HawkularTraversal<Vertex, Vertex>) where(check);
    }

    @SuppressWarnings("unchecked")
    private <A, B> HawkularTraversal<A, B> castThis(GraphTraversal<A, B> that) {
        return (HawkularTraversal<A, B>) this;
    }

    ////// DELEGATED methods

    @Override public Admin<S, E> asAdmin() {
        return delegate.asAdmin();
    }

    @Override public <E2> HawkularTraversal<S, E2> map(Function<Traverser<E>, E2> function) {
        return castThis(delegate.map(function));
    }

    @Override public <E2> HawkularTraversal<S, E2> map(Traversal<?, E2> mapTraversal) {
        return castThis(delegate.map(mapTraversal));
    }

    @Override public <E2> HawkularTraversal<S, E2> flatMap(
            Function<Traverser<E>, Iterator<E2>> function) {
        return castThis(delegate.flatMap(function));
    }

    @Override public <E2> HawkularTraversal<S, E2> flatMap(
            Traversal<?, E2> flatMapTraversal) {
        return castThis(delegate.flatMap(flatMapTraversal));
    }

    @Override public HawkularTraversal<S, Object> id() {
        return castThis(delegate.id());
    }

    @Override public HawkularTraversal<S, String> label() {
        return castThis(delegate.label());
    }

    @Override public HawkularTraversal<S, E> identity() {
        return castThis(delegate.identity());
    }

    @Override public <E2> HawkularTraversal<S, E2> constant(E2 e) {
        return castThis(delegate.constant(e));
    }

    @Override public HawkularTraversal<S, Vertex> V(Object... vertexIdsOrElements) {
        return castThis(delegate.V(vertexIdsOrElements));
    }

    @Override public HawkularTraversal<S, Vertex> to(
            Direction direction, String... edgeLabels) {
        return castThis(delegate.to(direction, edgeLabels));
    }

    @Override public HawkularTraversal<S, Vertex> out(String... edgeLabels) {
        return castThis(delegate.out(edgeLabels));
    }

    @Override public HawkularTraversal<S, Vertex> in(String... edgeLabels) {
        return castThis(delegate.in(edgeLabels));
    }

    @Override public HawkularTraversal<S, Vertex> both(String... edgeLabels) {
        return castThis(delegate.both(edgeLabels));
    }

    @Override public HawkularTraversal<S, Edge> toE(
            Direction direction, String... edgeLabels) {
        return castThis(delegate.toE(direction, edgeLabels));
    }

    @Override public HawkularTraversal<S, Edge> outE(String... edgeLabels) {
        return castThis(delegate.outE(edgeLabels));
    }

    @Override public HawkularTraversal<S, Edge> inE(String... edgeLabels) {
        return castThis(delegate.inE(edgeLabels));
    }

    @Override public HawkularTraversal<S, Edge> bothE(String... edgeLabels) {
        return castThis(delegate.bothE(edgeLabels));
    }

    @Override public HawkularTraversal<S, Vertex> toV(
            Direction direction) {
        return castThis(delegate.toV(direction));
    }

    @Override public HawkularTraversal<S, Vertex> inV() {
        return castThis(delegate.inV());
    }

    @Override public HawkularTraversal<S, Vertex> outV() {
        return castThis(delegate.outV());
    }

    @Override public HawkularTraversal<S, Vertex> bothV() {
        return castThis(delegate.bothV());
    }

    @Override public HawkularTraversal<S, Vertex> otherV() {
        return castThis(delegate.otherV());
    }

    @Override public HawkularTraversal<S, E> order() {
        return castThis(delegate.order());
    }

    @Override public HawkularTraversal<S, E> order(Scope scope) {
        return castThis(delegate.order(scope));
    }

    @Override public <E2> HawkularTraversal<S, ? extends Property<E2>> properties(String... propertyKeys) {
        return castThis(delegate.<E2>properties(propertyKeys));
    }

    @Override public <E2> HawkularTraversal<S, E2> values(String... propertyKeys) {
        return castThis(delegate.values(propertyKeys));
    }

    @Override public <E2> HawkularTraversal<S, Map<String, E2>> propertyMap(String... propertyKeys) {
        return castThis(delegate.propertyMap(propertyKeys));
    }

    @Override public <E2> HawkularTraversal<S, Map<String, E2>> valueMap(String... propertyKeys) {
        return castThis(delegate.valueMap(propertyKeys));
    }

    @Override public <E2> HawkularTraversal<S, Map<String, E2>> valueMap(boolean includeTokens, String... propertyKeys) {
        return castThis(delegate.valueMap(includeTokens, propertyKeys));
    }

    @Override public <E2> HawkularTraversal<S, Collection<E2>> select(Column column) {
        return castThis(delegate.select(column));
    }

    @Override @Deprecated public <E2> HawkularTraversal<S, E2> mapValues() {
        return castThis(delegate.mapValues());
    }

    @Override @Deprecated public <E2> HawkularTraversal<S, E2> mapKeys() {
        return castThis(delegate.mapKeys());
    }

    @Override public HawkularTraversal<S, String> key() {
        return castThis(delegate.key());
    }

    @Override public <E2> HawkularTraversal<S, E2> value() {
        return castThis(delegate.value());
    }

    @Override public HawkularTraversal<S, Path> path() {
        return castThis(delegate.path());
    }

    @Override public <E2> HawkularTraversal<S, Map<String, E2>> match(
            Traversal<?, ?>... matchTraversals) {
        return castThis(delegate.match(matchTraversals));
    }

    @Override public <E2> HawkularTraversal<S, E2> sack() {
        return castThis(delegate.sack());
    }

    @Override public HawkularTraversal<S, Integer> loops() {
        return castThis(delegate.loops());
    }

    @Override public <E2> HawkularTraversal<S, Map<String, E2>> project(String projectKey, String... otherProjectKeys) {
        return castThis(delegate.project(projectKey, otherProjectKeys));
    }

    @Override public <E2> HawkularTraversal<S, Map<String, E2>> select(Pop pop,
                                                                    String selectKey1, String selectKey2,
                                                                    String... otherSelectKeys) {
        return castThis(delegate.select(pop, selectKey1, selectKey2, otherSelectKeys));
    }

    @Override public <E2> HawkularTraversal<S, Map<String, E2>> select(String selectKey1, String selectKey2,
                                                                    String... otherSelectKeys) {
        return castThis(delegate.select(selectKey1, selectKey2, otherSelectKeys));
    }

    @Override public <E2> HawkularTraversal<S, E2> select(Pop pop,
                                                       String selectKey) {
        return castThis(delegate.select(pop, selectKey));
    }

    @Override public <E2> HawkularTraversal<S, E2> select(String selectKey) {
        return castThis(delegate.select(selectKey));
    }

    @Override public <E2> HawkularTraversal<S, E2> unfold() {
        return castThis(delegate.unfold());
    }

    @Override public HawkularTraversal<S, List<E>> fold() {
        return castThis(delegate.fold());
    }

    @Override public <E2> HawkularTraversal<S, E2> fold(E2 seed, BiFunction<E2, E, E2> foldFunction) {
        return castThis(delegate.fold(seed, foldFunction));
    }

    @Override public HawkularTraversal<S, Long> count() {
        return castThis(delegate.count());
    }

    @Override public HawkularTraversal<S, Long> count(Scope scope) {
        return castThis(delegate.count(scope));
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> sum() {
        return castThis(delegate.sum());
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> sum(Scope scope) {
        return castThis(delegate.sum(scope));
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> max() {
        return castThis(delegate.max());
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> max(Scope scope) {
        return castThis(delegate.max(scope));
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> min() {
        return castThis(delegate.min());
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> min(Scope scope) {
        return castThis(delegate.min(scope));
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> mean() {
        return castThis(delegate.mean());
    }

    @Override public <E2 extends Number> HawkularTraversal<S, E2> mean(Scope scope) {
        return castThis(delegate.mean(scope));
    }

    @Override public <K, V> HawkularTraversal<S, Map<K, V>> group() {
        return castThis(delegate.group());
    }

    @Override @Deprecated public <K, V> HawkularTraversal<S, Map<K, V>> groupV3d0() {
        return castThis(delegate.groupV3d0());
    }

    @Override public <K> HawkularTraversal<S, Map<K, Long>> groupCount() {
        return castThis(delegate.groupCount());
    }

    @Override public HawkularTraversal<S, Tree> tree() {
        return castThis(delegate.tree());
    }

    @Override public HawkularTraversal<S, Vertex> addV(String vertexLabel) {
        return castThis(delegate.addV(vertexLabel));
    }

    @Override public HawkularTraversal<S, Vertex> addV() {
        return castThis(delegate.addV());
    }

    @Override @Deprecated public HawkularTraversal<S, Vertex> addV(Object... propertyKeyValues) {
        return castThis(delegate.addV(propertyKeyValues));
    }

    @Override public HawkularTraversal<S, Edge> addE(String edgeLabel) {
        return castThis(delegate.addE(edgeLabel));
    }

    @Override public HawkularTraversal<S, E> to(String toStepLabel) {
        return castThis(delegate.to(toStepLabel));
    }

    @Override public HawkularTraversal<S, E> from(String fromStepLabel) {
        return castThis(delegate.from(fromStepLabel));
    }

    @Override public HawkularTraversal<S, E> to(
            Traversal<E, Vertex> toVertex) {
        return castThis(delegate.to(toVertex));
    }

    @Override public HawkularTraversal<S, E> from(
            Traversal<E, Vertex> fromVertex) {
        return castThis(delegate.from(fromVertex));
    }

    @Override @Deprecated public HawkularTraversal<S, Edge> addE(
            Direction direction, String firstVertexKeyOrEdgeLabel,
            String edgeLabelOrSecondVertexKey, Object... propertyKeyValues) {
        return castThis(delegate.addE(direction, firstVertexKeyOrEdgeLabel, edgeLabelOrSecondVertexKey, propertyKeyValues));
    }

    @Override @Deprecated public HawkularTraversal<S, Edge> addOutE(String firstVertexKeyOrEdgeLabel,
                                                                 String edgeLabelOrSecondVertexKey,
                                                                 Object... propertyKeyValues) {
        return castThis(delegate.addOutE(firstVertexKeyOrEdgeLabel, edgeLabelOrSecondVertexKey, propertyKeyValues));
    }

    @Override @Deprecated public HawkularTraversal<S, Edge> addInE(String firstVertexKeyOrEdgeLabel,
                                                                String edgeLabelOrSecondVertexKey,
                                                                Object... propertyKeyValues) {
        return castThis(delegate.addInE(firstVertexKeyOrEdgeLabel, edgeLabelOrSecondVertexKey, propertyKeyValues));
    }

    @Override public HawkularTraversal<S, E> filter(
            Predicate<Traverser<E>> predicate) {
        return castThis(delegate.filter(predicate));
    }

    @Override public HawkularTraversal<S, E> filter(Traversal<?, ?> filterTraversal) {
        return castThis(delegate.filter(filterTraversal));
    }

    @Override public HawkularTraversal<S, E> or(Traversal<?, ?>... orTraversals) {
        return castThis(delegate.or(orTraversals));
    }

    @Override public HawkularTraversal<S, E> and(Traversal<?, ?>... andTraversals) {
        return castThis(delegate.and(andTraversals));
    }

    @Override public HawkularTraversal<S, E> inject(E... injections) {
        return castThis(delegate.inject(injections));
    }

    @Override public HawkularTraversal<S, E> dedup(Scope scope,
                                                String... dedupLabels) {
        return castThis(delegate.dedup(scope, dedupLabels));
    }

    @Override public HawkularTraversal<S, E> dedup(String... dedupLabels) {
        return castThis(delegate.dedup(dedupLabels));
    }

    @Override public HawkularTraversal<S, E> where(String startKey,
                                                P<String> predicate) {
        return castThis(delegate.where(startKey, predicate));
    }

    @Override public HawkularTraversal<S, E> where(P<String> predicate) {
        return castThis(delegate.where(predicate));
    }

    @Override public HawkularTraversal<S, E> where(Traversal<?, ?> whereTraversal) {
        return castThis(delegate.where(whereTraversal));
    }

    @Override public HawkularTraversal<S, E> has(String propertyKey, P<?> predicate) {
        return castThis(delegate.has(propertyKey, predicate));
    }

    @Override public HawkularTraversal<S, E> has(T accessor,
                                              P<?> predicate) {
        return castThis(delegate.has(accessor, predicate));
    }

    @Override public HawkularTraversal<S, E> has(String propertyKey, Object value) {
        return castThis(delegate.has(propertyKey, value));
    }

    @Override public HawkularTraversal<S, E> has(T accessor, Object value) {
        return castThis(delegate.has(accessor, value));
    }

    @Override public HawkularTraversal<S, E> has(String label, String propertyKey,
                                              P<?> predicate) {
        return castThis(delegate.has(label, propertyKey, predicate));
    }

    @Override public HawkularTraversal<S, E> has(String label, String propertyKey, Object value) {
        return castThis(delegate.has(label, propertyKey, value));
    }

    @Override public HawkularTraversal<S, E> has(T accessor,
                                              Traversal<?, ?> propertyTraversal) {
        return castThis(delegate.has(accessor, propertyTraversal));
    }

    @Override public HawkularTraversal<S, E> has(String propertyKey,
                                              Traversal<?, ?> propertyTraversal) {
        return castThis(delegate.has(propertyKey, propertyTraversal));
    }

    @Override public HawkularTraversal<S, E> has(String propertyKey) {
        return castThis(delegate.has(propertyKey));
    }

    @Override public HawkularTraversal<S, E> hasNot(String propertyKey) {
        return castThis(delegate.hasNot(propertyKey));
    }

    @Override public HawkularTraversal<S, E> has(T accessor, Object value,
                                              Object... values) {
        return castThis(delegate.has(accessor, value, values));
    }

    @Override public HawkularTraversal<S, E> hasLabel(Object value, Object... values) {
        return castThis(delegate.hasLabel(value, values));
    }

    @Override public HawkularTraversal<S, E> hasId(Object value, Object... values) {
        return castThis(delegate.hasId(value, values));
    }

    @Override public HawkularTraversal<S, E> hasKey(Object value, Object... values) {
        return castThis(delegate.hasKey(value, values));
    }

    @Override public HawkularTraversal<S, E> hasValue(Object value, Object... values) {
        return castThis(delegate.hasValue(value, values));
    }

    @Override public HawkularTraversal<S, E> is(P<E> predicate) {
        return castThis(delegate.is(predicate));
    }

    @Override public HawkularTraversal<S, E> is(Object value) {
        return castThis(delegate.is(value));
    }

    @Override public HawkularTraversal<S, E> not(Traversal<?, ?> notTraversal) {
        return castThis(delegate.not(notTraversal));
    }

    @Override public HawkularTraversal<S, E> coin(double probability) {
        return castThis(delegate.coin(probability));
    }

    @Override public HawkularTraversal<S, E> range(long low, long high) {
        return castThis(delegate.range(low, high));
    }

    @Override public <E2> HawkularTraversal<S, E2> range(Scope scope, long low, long high) {
        return castThis(delegate.range(scope, low, high));
    }

    @Override public HawkularTraversal<S, E> limit(long limit) {
        return castThis(delegate.limit(limit));
    }

    @Override public <E2> HawkularTraversal<S, E2> limit(Scope scope, long limit) {
        return castThis(delegate.limit(scope, limit));
    }

    @Override public HawkularTraversal<S, E> tail() {
        return castThis(delegate.tail());
    }

    @Override public HawkularTraversal<S, E> tail(long limit) {
        return castThis(delegate.tail(limit));
    }

    @Override public <E2> HawkularTraversal<S, E2> tail(Scope scope) {
        return castThis(delegate.tail(scope));
    }

    @Override public <E2> HawkularTraversal<S, E2> tail(Scope scope, long limit) {
        return castThis(delegate.tail(scope, limit));
    }

    @Override public HawkularTraversal<S, E> timeLimit(long timeLimit) {
        return castThis(delegate.timeLimit(timeLimit));
    }

    @Override public HawkularTraversal<S, E> simplePath() {
        return castThis(delegate.simplePath());
    }

    @Override public HawkularTraversal<S, E> cyclicPath() {
        return castThis(delegate.cyclicPath());
    }

    @Override public HawkularTraversal<S, E> sample(int amountToSample) {
        return castThis(delegate.sample(amountToSample));
    }

    @Override public HawkularTraversal<S, E> sample(Scope scope, int amountToSample) {
        return castThis(delegate.sample(scope, amountToSample));
    }

    @Override public HawkularTraversal<S, E> drop() {
        return castThis(delegate.drop());
    }

    @Override public HawkularTraversal<S, E> sideEffect(
            Consumer<Traverser<E>> consumer) {
        return castThis(delegate.sideEffect(consumer));
    }

    @Override public HawkularTraversal<S, E> sideEffect(
            Traversal<?, ?> sideEffectTraversal) {
        return castThis(delegate.sideEffect(sideEffectTraversal));
    }

    @Override public <E2> HawkularTraversal<S, E2> cap(String sideEffectKey, String... sideEffectKeys) {
        return castThis(delegate.cap(sideEffectKey, sideEffectKeys));
    }

    @Override public HawkularTraversal<S, Edge> subgraph(String sideEffectKey) {
        return castThis(delegate.subgraph(sideEffectKey));
    }

    @Override public HawkularTraversal<S, E> aggregate(String sideEffectKey) {
        return castThis(delegate.aggregate(sideEffectKey));
    }

    @Override public HawkularTraversal<S, E> group(String sideEffectKey) {
        return castThis(delegate.group(sideEffectKey));
    }

    @Override public HawkularTraversal<S, E> groupV3d0(String sideEffectKey) {
        return castThis(delegate.groupV3d0(sideEffectKey));
    }

    @Override public HawkularTraversal<S, E> groupCount(String sideEffectKey) {
        return castThis(delegate.groupCount(sideEffectKey));
    }

    @Override public HawkularTraversal<S, E> tree(String sideEffectKey) {
        return castThis(delegate.tree(sideEffectKey));
    }

    @Override public <V, U> HawkularTraversal<S, E> sack(BiFunction<V, U, V> sackOperator) {
        return castThis(delegate.sack(sackOperator));
    }

    @Override @Deprecated public <V, U> HawkularTraversal<S, E> sack(BiFunction<V, U, V> sackOperator,
                                                                  String elementPropertyKey) {
        return castThis(delegate.sack(sackOperator, elementPropertyKey));
    }

    @Override public HawkularTraversal<S, E> store(String sideEffectKey) {
        return castThis(delegate.store(sideEffectKey));
    }

    @Override public HawkularTraversal<S, E> profile(String sideEffectKey) {
        return castThis(delegate.profile(sideEffectKey));
    }

    @Override public HawkularTraversal<S, TraversalMetrics> profile() {
        return castThis(delegate.profile());
    }

    @Override public HawkularTraversal<S, E> property(VertexProperty.Cardinality cardinality,
                                                   Object key, Object value, Object... keyValues) {
        return castThis(delegate.property(cardinality, key, value, keyValues));
    }

    @Override public HawkularTraversal<S, E> property(Object key, Object value, Object... keyValues) {
        return castThis(delegate.property(key, value, keyValues));
    }

    @Override public <M, E2> HawkularTraversal<S, E2> branch(
            Traversal<?, M> branchTraversal) {
        return castThis(delegate.branch(branchTraversal));
    }

    @Override public <M, E2> HawkularTraversal<S, E2> branch(
            Function<Traverser<E>, M> function) {
        return castThis(delegate.branch(function));
    }

    @Override public <M, E2> HawkularTraversal<S, E2> choose(
            Traversal<?, M> choiceTraversal) {
        return castThis(delegate.choose(choiceTraversal));
    }

    @Override public <E2> HawkularTraversal<S, E2> choose(
            Traversal<?, ?> traversalPredicate,
            Traversal<?, E2> trueChoice,
            Traversal<?, E2> falseChoice) {
        return castThis(delegate.choose(traversalPredicate, trueChoice, falseChoice));
    }

    @Override public <M, E2> HawkularTraversal<S, E2> choose(Function<E, M> choiceFunction) {
        return castThis(delegate.choose(choiceFunction));
    }

    @Override public <E2> HawkularTraversal<S, E2> choose(Predicate<E> choosePredicate,
                                                       Traversal<?, E2> trueChoice,
                                                       Traversal<?, E2> falseChoice) {
        return castThis(delegate.choose(choosePredicate, trueChoice, falseChoice));
    }

    @Override public <E2> HawkularTraversal<S, E2> optional(
            Traversal<?, E2> optionalTraversal) {
        return castThis(delegate.optional(optionalTraversal));
    }

    @Override public <E2> HawkularTraversal<S, E2> union(
            Traversal<?, E2>... unionTraversals) {
        return castThis(delegate.union(unionTraversals));
    }

    @Override public <E2> HawkularTraversal<S, E2> coalesce(
            Traversal<?, E2>... coalesceTraversals) {
        return castThis(delegate.coalesce(coalesceTraversals));
    }

    @Override public HawkularTraversal<S, E> repeat(Traversal<?, E> repeatTraversal) {
        return castThis(delegate.repeat(repeatTraversal));
    }

    @Override public HawkularTraversal<S, E> emit(Traversal<?, ?> emitTraversal) {
        return castThis(delegate.emit(emitTraversal));
    }

    @Override public HawkularTraversal<S, E> emit(
            Predicate<Traverser<E>> emitPredicate) {
        return castThis(delegate.emit(emitPredicate));
    }

    @Override public HawkularTraversal<S, E> emit() {
        return castThis(delegate.emit());
    }

    @Override public HawkularTraversal<S, E> until(Traversal<?, ?> untilTraversal) {
        return castThis(delegate.until(untilTraversal));
    }

    @Override public HawkularTraversal<S, E> until(
            Predicate<Traverser<E>> untilPredicate) {
        return castThis(delegate.until(untilPredicate));
    }

    @Override public HawkularTraversal<S, E> times(int maxLoops) {
        return castThis(delegate.times(maxLoops));
    }

    @Override public <E2> HawkularTraversal<S, E2> local(Traversal<?, E2> localTraversal) {
        return castThis(delegate.local(localTraversal));
    }

    @Override public HawkularTraversal<S, E> pageRank() {
        return castThis(delegate.pageRank());
    }

    @Override public HawkularTraversal<S, E> pageRank(double alpha) {
        return castThis(delegate.pageRank(alpha));
    }

    @Override public HawkularTraversal<S, E> peerPressure() {
        return castThis(delegate.peerPressure());
    }

    @Override public HawkularTraversal<S, E> program(VertexProgram<?> vertexProgram) {
        return castThis(delegate.program(vertexProgram));
    }

    @Override public HawkularTraversal<S, E> as(String stepLabel, String... stepLabels) {
        return castThis(delegate.as(stepLabel, stepLabels));
    }

    @Override public HawkularTraversal<S, E> barrier() {
        return castThis(delegate.barrier());
    }

    @Override public HawkularTraversal<S, E> barrier(int maxBarrierSize) {
        return castThis(delegate.barrier(maxBarrierSize));
    }

    @Override public HawkularTraversal<S, E> barrier(
            Consumer<TraverserSet<Object>> barrierConsumer) {
        return castThis(delegate.barrier(barrierConsumer));
    }

    @Override public HawkularTraversal<S, E> by() {
        return castThis(delegate.by());
    }

    @Override public HawkularTraversal<S, E> by(Traversal<?, ?> traversal) {
        return castThis(delegate.by(traversal));
    }

    @Override public HawkularTraversal<S, E> by(T token) {
        return castThis(delegate.by(token));
    }

    @Override public HawkularTraversal<S, E> by(String key) {
        return castThis(delegate.by(key));
    }

    @Override public <V> HawkularTraversal<S, E> by(Function<V, Object> function) {
        return castThis(delegate.by(function));
    }

    @Override public <V> HawkularTraversal<S, E> by(Traversal<?, ?> traversal,
                                                 Comparator<V> comparator) {
        return castThis(delegate.by(traversal, comparator));
    }

    @Override public HawkularTraversal<S, E> by(Comparator<E> comparator) {
        return castThis(delegate.by(comparator));
    }

    @Override public HawkularTraversal<S, E> by(Order order) {
        return castThis(delegate.by(order));
    }

    @Override public <V> HawkularTraversal<S, E> by(String key, Comparator<V> comparator) {
        return castThis(delegate.by(key, comparator));
    }

    @Override public <U> HawkularTraversal<S, E> by(Function<U, Object> function, Comparator comparator) {
        return castThis(delegate.by(function, comparator));
    }

    @Override public <M, E2> HawkularTraversal<S, E> option(M pickToken,
                                                         Traversal<E, E2> traversalOption) {
        return castThis(delegate.option(pickToken, traversalOption));
    }

    @Override public <E2> HawkularTraversal<S, E> option(Traversal<E, E2> traversalOption) {
        return castThis(delegate.option(traversalOption));
    }

    @Override public HawkularTraversal<S, E> iterate() {
        return castThis(delegate.iterate());
    }

    @Override public Optional<E> tryNext() {
        return delegate.tryNext();
    }

    @Override public List<E> next(int amount) {
        return delegate.next(amount);
    }

    @Override public List<E> toList() {
        return delegate.toList();
    }

    @Override public Set<E> toSet() {
        return delegate.toSet();
    }

    @Override public BulkSet<E> toBulkSet() {
        return delegate.toBulkSet();
    }

    @Override public Stream<E> toStream() {
        return delegate.toStream();
    }

    @Override public <C extends Collection<E>> C fill(C collection) {
        return delegate.fill(collection);
    }

    @Override public TraversalExplanation explain() {
        return delegate.explain();
    }

    @Override public <E2> void forEachRemaining(Class<E2> endType, Consumer<E2> consumer) {
        delegate.forEachRemaining(endType, consumer);
    }

    @Override public void forEachRemaining(Consumer<? super E> action) {
        delegate.forEachRemaining(action);
    }

    @Override public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override public E next() {
        return delegate.next();
    }

    @Override public void remove() {
        delegate.remove();
    }

    @Override public Admin<S, E> clone() {
        return ((Admin<S, E>) delegate).clone();
    }

    @Override public Bytecode getBytecode() {
        return ((Admin<S, E>) delegate).getBytecode();
    }

    @Override public List<Step> getSteps() {
        return ((Admin<S, E>) delegate).getSteps();
    }

    @Override public <S2, E2> Traversal.Admin<S2, E2> addStep(int index, Step<?, ?> step) throws IllegalStateException {
        return ((Admin<S, E>) delegate).addStep(index, step);
    }

    @Override public <S2, E2> Traversal.Admin<S2, E2> removeStep(int index) throws IllegalStateException {
        return ((Admin<S, E>) delegate).removeStep(index);
    }

    @Override public void applyStrategies() throws IllegalStateException {
        ((Admin<S, E>) delegate).applyStrategies();
    }

    @Override public TraverserGenerator getTraverserGenerator() {
        return ((Admin<S, E>) delegate).getTraverserGenerator();
    }

    @Override public Set<TraverserRequirement> getTraverserRequirements() {
        return ((Admin<S, E>) delegate).getTraverserRequirements();
    }

    @Override public void setSideEffects(TraversalSideEffects sideEffects) {
        ((Admin<S, E>) delegate).setSideEffects(sideEffects);
    }

    @Override public TraversalSideEffects getSideEffects() {
        return ((Admin<S, E>) delegate).getSideEffects();
    }

    @Override public void setStrategies(TraversalStrategies strategies) {
        ((Admin<S, E>) delegate).setStrategies(strategies);
    }

    @Override public TraversalStrategies getStrategies() {
        return ((Admin<S, E>) delegate).getStrategies();
    }

    @Override public void setParent(TraversalParent step) {
        ((Admin<S, E>) delegate).setParent(step);
    }

    @Override public TraversalParent getParent() {
        return ((Admin<S, E>) delegate).getParent();
    }

    @Override public boolean isLocked() {
        return ((Admin<S, E>) delegate).isLocked();
    }

    @Override public Optional<Graph> getGraph() {
        return ((Admin<S, E>) delegate).getGraph();
    }

    @Override public void setGraph(Graph graph) {
        ((Admin<S, E>) delegate).setGraph(graph);
    }

    @Override public <E2> Admin<S, E2> addStep(Step<?, E2> step) {
        return ((Admin<S, E>) delegate).addStep(step);
    }

    @Override public void addStarts(Iterator<Traverser.Admin<S>> starts) {
        ((Admin<S, E>) delegate).addStarts(starts);
    }

    @Override public void addStart(Traverser.Admin<S> start) {
        ((Admin<S, E>) delegate).addStart(start);
    }

    @Override public <S2, E2> Traversal.Admin<S2, E2> removeStep(Step<?, ?> step) throws IllegalStateException {
        return ((Admin<S, E>) delegate).removeStep(step);
    }

    @Override public Step<S, ?> getStartStep() {
        return ((Admin<S, E>) delegate).getStartStep();
    }

    @Override public Step<?, E> getEndStep() {
        return ((Admin<S, E>) delegate).getEndStep();
    }

    @Override public void reset() {
        ((Admin<S, E>) delegate).reset();
    }

    @Override public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override public boolean equals(Traversal.Admin<S, E> other) {
        return ((Admin<S, E>) delegate).equals(other);
    }

    @Override public int hashCode() {
        return delegate.hashCode();
    }

    @Override public Traverser.Admin<E> nextTraverser() {
        return ((Admin<S, E>) delegate).nextTraverser();
    }

    @Override public String toString() {
        return delegate.toString();
    }
}
