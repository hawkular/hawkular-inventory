/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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

package org.hawkular.inventory.impl.blueprints;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Predicate;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.gremlin.Tokens;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.Pipe;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.pipes.branch.LoopPipe;
import com.tinkerpop.pipes.transform.TransformPipe;
import com.tinkerpop.pipes.util.structures.Pair;
import com.tinkerpop.pipes.util.structures.Row;
import com.tinkerpop.pipes.util.structures.Table;
import com.tinkerpop.pipes.util.structures.Tree;
import org.hawkular.inventory.api.Relationships;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class HawkularPipeline<S, E> extends GremlinPipeline<S, E> implements Cloneable {

    public HawkularPipeline() {
    }

    public HawkularPipeline(Object starts) {
        super(starts);
    }

    public HawkularPipeline(Object starts, boolean doQueryOptimization) {
        super(starts, doQueryOptimization);
    }

    @SuppressWarnings("unchecked")
    public HawkularPipeline<S, Vertex> hasType(Constants.Type type) {
        return (HawkularPipeline<S, Vertex>) has(Constants.Property.type.name(), type.name());
    }

    @SuppressWarnings("unchecked")
    public HawkularPipeline<S, Vertex> hasUid(String uid) {
        return (HawkularPipeline<S, Vertex>) has(Constants.Property.uid.name(), uid);
    }

    public HawkularPipeline<S, Vertex> out(Relationships.WellKnown... rel) {
        String[] srels = new String[rel.length];
        Arrays.setAll(srels, i -> rel[i].name());

        return out(srels);
    }

    public HawkularPipeline<S, Vertex> in(Relationships.WellKnown... rel) {
        String[] srels = new String[rel.length];
        Arrays.setAll(srels, i -> rel[i].name());

        return in(srels);
    }

    @Override
    public HawkularPipeline<S, E> _() {
        return cast(super._());
    }

    @Override
    public <T> HawkularPipeline<S, T> add(Pipe<?, T> pipe) {
        return cast(super.add(pipe));
    }

    @Override
    public HawkularPipeline<S, E> aggregate() {
        return cast(super.aggregate());
    }

    @Override
    public HawkularPipeline<S, E> aggregate(Collection aggregate, PipeFunction<E, ?> aggregateFunction) {
        return cast(super.aggregate(aggregate, aggregateFunction));
    }

    @Override
    public HawkularPipeline<S, E> aggregate(Collection<E> aggregate) {
        return cast(super.aggregate(aggregate));
    }

    @Override
    public HawkularPipeline<S, E> aggregate(PipeFunction<E, ?> aggregateFunction) {
        return cast(super.aggregate(aggregateFunction));
    }

    @Override
    public HawkularPipeline<S, E> and(Pipe<E, ?>... pipes) {
        return cast(super.and(pipes));
    }

    @Override
    public HawkularPipeline<S, E> as(String name) {
        return cast(super.as(name));
    }

    @Override
    public HawkularPipeline<S, ?> back(String namedStep) {
        return cast(super.back(namedStep));
    }

    @Override
    @Deprecated
    public HawkularPipeline<S, ?> back(int numberedStep) {
        return cast(super.back(numberedStep));
    }

    @Override
    public HawkularPipeline<S, Vertex> both(int branchFactor, String... labels) {
        return cast(super.both(branchFactor, labels));
    }

    @Override
    public HawkularPipeline<S, Vertex> both(String... labels) {
        return cast(super.both(labels));
    }

    @Override
    public HawkularPipeline<S, Edge> bothE(int branchFactor, String... labels) {
        return cast(super.bothE(branchFactor, labels));
    }

    @Override
    public HawkularPipeline<S, Edge> bothE(String... labels) {
        return cast(super.bothE(labels));
    }

    @Override
    public HawkularPipeline<S, Vertex> bothV() {
        return cast(super.bothV());
    }

    @Override
    public HawkularPipeline<S, ?> cap() {
        return cast(super.cap());
    }

    @Override
    public <E1> HawkularPipeline<S, E1> cast(Class<E1> end) {
        return cast(super.cast(end));
    }

    @Override
    public HawkularPipeline<S, ?> copySplit(Pipe<E, ?>... pipes) {
        return cast(super.copySplit(pipes));
    }

    @Override
    public HawkularPipeline<S, E> dedup() {
        return cast(super.dedup());
    }

    @Override
    public HawkularPipeline<S, E> dedup(PipeFunction<E, ?> dedupFunction) {
        return cast(super.dedup(dedupFunction));
    }

    @Override
    public HawkularPipeline<S, Edge> E() {
        return cast(super.E());
    }

    @Override
    public HawkularPipeline<S, Edge> E(String key, Object value) {
        return cast(super.E(key, value));
    }

    @Override
    public HawkularPipeline<S, E> enablePath() {
        return cast(super.enablePath());
    }

    @Override
    public HawkularPipeline<S, E> except(Collection<E> collection) {
        return cast(super.except(collection));
    }

    @Override
    public HawkularPipeline<S, E> except(String... namedSteps) {
        return cast(super.except(namedSteps));
    }

    @Override
    public HawkularPipeline<S, ?> exhaustMerge() {
        return cast(super.exhaustMerge());
    }

    @Override
    public HawkularPipeline<S, ?> fairMerge() {
        return cast(super.fairMerge());
    }

    @Override
    public HawkularPipeline<S, E> filter(PipeFunction<E, Boolean> filterFunction) {
        return cast(super.filter(filterFunction));
    }

    @Override
    public HawkularPipeline<S, List> gather() {
        return cast(super.gather());
    }

    @Override
    public HawkularPipeline<S, ?> gather(PipeFunction<List, ?> function) {
        return cast(super.gather(function));
    }

    @Override
    public HawkularPipeline<S, E> groupBy(PipeFunction keyFunction, PipeFunction valueFunction) {
        return cast(super.groupBy(keyFunction, valueFunction));
    }

    @Override
    public HawkularPipeline<S, E> groupBy(PipeFunction keyFunction, PipeFunction valueFunction,
                                          PipeFunction reduceFunction) {
        return cast(super.groupBy(keyFunction, valueFunction, reduceFunction));
    }

    @Override
    public HawkularPipeline<S, E> groupBy(Map<?, List<?>> map, PipeFunction keyFunction, PipeFunction valueFunction) {
        return cast(super.groupBy(map, keyFunction, valueFunction));
    }

    @Override
    public HawkularPipeline<S, E> groupBy(Map reduceMap, PipeFunction keyFunction, PipeFunction valueFunction,
                                          PipeFunction reduceFunction) {
        return cast(super.groupBy(reduceMap, keyFunction, valueFunction, reduceFunction));
    }

    @Override
    public HawkularPipeline<S, E> groupCount() {
        return cast(super.groupCount());
    }

    @Override
    public HawkularPipeline<S, E> groupCount(PipeFunction keyFunction) {
        return cast(super.groupCount(keyFunction));
    }

    @Override
    public HawkularPipeline<S, E> groupCount(PipeFunction keyFunction,
                                             PipeFunction<Pair<?, Number>, Number> valueFunction) {
        return cast(super.groupCount(keyFunction, valueFunction));
    }

    @Override
    public HawkularPipeline<S, E> groupCount(Map<?, Number> map) {
        return cast(super.groupCount(map));
    }

    @Override
    public HawkularPipeline<S, E> groupCount(Map<?, Number> map, PipeFunction keyFunction) {
        return cast(super.groupCount(map, keyFunction));
    }

    @Override
    public HawkularPipeline<S, E> groupCount(Map<?, Number> map, PipeFunction keyFunction,
                                             PipeFunction<Pair<?, Number>, Number> valueFunction) {
        return cast(super.groupCount(map, keyFunction, valueFunction));
    }

    @Override
    public HawkularPipeline<S, ? extends Element> has(String key) {
        return cast(super.has(key));
    }

    @Override
    public HawkularPipeline<S, ? extends Element> has(String key, Tokens.T compareToken, Object value) {
        return cast(super.has(key, compareToken, value));
    }

    @Override
    public HawkularPipeline<S, ? extends Element> has(String key, Predicate predicate, Object value) {
        return cast(super.has(key, predicate, value));
    }

    @Override
    public HawkularPipeline<S, ? extends Element> has(String key, Object value) {
        return cast(super.has(key, value));
    }

    @Override
    public HawkularPipeline<S, ? extends Element> hasNot(String key) {
        return cast(super.hasNot(key));
    }

    @Override
    public HawkularPipeline<S, ? extends Element> hasNot(String key, Object value) {
        return cast(super.hasNot(key, value));
    }

    @Override
    public HawkularPipeline<S, Object> id() {
        return cast(super.id());
    }

    @Override
    public HawkularPipeline<S, Edge> idEdge(Graph graph) {
        return cast(super.idEdge(graph));
    }

    @Override
    public HawkularPipeline<S, Vertex> idVertex(Graph graph) {
        return cast(super.idVertex(graph));
    }

    @Override
    public HawkularPipeline<S, ?> ifThenElse(PipeFunction<E, Boolean> ifFunction, PipeFunction<E, ?> thenFunction,
                                             PipeFunction<E, ?> elseFunction) {
        return cast(super.ifThenElse(ifFunction, thenFunction, elseFunction));
    }

    @Override
    public HawkularPipeline<S, Vertex> in(int branchFactor, String... labels) {
        return cast(super.in(branchFactor, labels));
    }

    @Override
    public HawkularPipeline<S, Vertex> in(String... labels) {
        return cast(super.in(labels));
    }

    @Override
    public HawkularPipeline<S, Edge> inE(int branchFactor, String... labels) {
        return cast(super.inE(branchFactor, labels));
    }

    @Override
    public HawkularPipeline<S, Edge> inE(String... labels) {
        return cast(super.inE(labels));
    }

    @Override
    public HawkularPipeline<S, ? extends Element> interval(String key, Comparable startValue, Comparable endValue) {
        return cast(super.interval(key, startValue, endValue));
    }

    @Override
    public HawkularPipeline<S, Vertex> inV() {
        return cast(super.inV());
    }

    @Override
    public void iterate() {
        super.iterate();
    }

    @Override
    public HawkularPipeline<S, String> label() {
        return cast(super.label());
    }

    @Override
    public HawkularPipeline<S, Vertex> linkBoth(String label, String namedStep) {
        return cast(super.linkBoth(label, namedStep));
    }

    @Override
    public HawkularPipeline<S, Vertex> linkBoth(String label, Vertex other) {
        return cast(super.linkBoth(label, other));
    }

    @Override
    public HawkularPipeline<S, Vertex> linkIn(String label, String namedStep) {
        return cast(super.linkIn(label, namedStep));
    }

    @Override
    public HawkularPipeline<S, Vertex> linkIn(String label, Vertex other) {
        return cast(super.linkIn(label, other));
    }

    @Override
    public HawkularPipeline<S, Vertex> linkOut(String label, String namedStep) {
        return cast(super.linkOut(label, namedStep));
    }

    @Override
    public HawkularPipeline<S, Vertex> linkOut(String label, Vertex other) {
        return cast(super.linkOut(label, other));
    }

    @Override
    public HawkularPipeline<S, E> loop(String namedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction) {
        return cast(super.loop(namedStep, whileFunction));
    }

    @Override
    public HawkularPipeline<S, E> loop(String namedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction,
                                       PipeFunction<LoopPipe.LoopBundle<E>, Boolean> emitFunction) {
        return cast(super.loop(namedStep, whileFunction, emitFunction));
    }

    @Override
    @Deprecated
    public HawkularPipeline<S, E> loop(int numberedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction) {
        return cast(super.loop(numberedStep, whileFunction));
    }

    @Override
    @Deprecated
    public HawkularPipeline<S, E> loop(int numberedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction,
                                       PipeFunction<LoopPipe.LoopBundle<E>, Boolean> emitFunction) {
        return cast(super.loop(numberedStep, whileFunction, emitFunction));
    }

    @Override
    public HawkularPipeline<S, Map<String, Object>> map(String... keys) {
        return cast(super.map(keys));
    }

    @Override
    public HawkularPipeline<S, E> memoize(String namedStep) {
        return cast(super.memoize(namedStep));
    }

    @Override
    public HawkularPipeline<S, E> memoize(String namedStep, Map map) {
        return cast(super.memoize(namedStep, map));
    }

    @Override
    @Deprecated
    public HawkularPipeline<S, E> memoize(int numberedStep) {
        return cast(super.memoize(numberedStep));
    }

    @Override
    @Deprecated
    public HawkularPipeline<S, E> memoize(int numberedStep, Map map) {
        return cast(super.memoize(numberedStep, map));
    }

    @Override
    public HawkularPipeline<S, E> optimize(boolean optimize) {
        return cast(super.optimize(optimize));
    }

    @Override
    public HawkularPipeline<S, ?> optional(String namedStep) {
        return cast(super.optional(namedStep));
    }

    @Override
    @Deprecated
    public HawkularPipeline<S, ?> optional(int numberedStep) {
        return cast(super.optional(numberedStep));
    }

    @Override
    public HawkularPipeline<S, E> or(Pipe<E, ?>... pipes) {
        return cast(super.or(pipes));
    }

    @Override
    public HawkularPipeline<S, E> order() {
        return cast(super.order());
    }

    @Override
    public HawkularPipeline<S, E> order(PipeFunction<Pair<E, E>, Integer> compareFunction) {
        return cast(super.order(compareFunction));
    }

    @Override
    public HawkularPipeline<S, E> order(TransformPipe.Order order) {
        return cast(super.order(order));
    }

    @Override
    public HawkularPipeline<S, E> order(Tokens.T order) {
        return cast(super.order(order));
    }

    @Override
    public HawkularPipeline<S, ?> orderMap(PipeFunction<Pair<Map.Entry, Map.Entry>, Integer> compareFunction) {
        return cast(super.orderMap(compareFunction));
    }

    @Override
    public HawkularPipeline<S, ?> orderMap(TransformPipe.Order order) {
        return cast(super.orderMap(order));
    }

    @Override
    public HawkularPipeline<S, ?> orderMap(Tokens.T order) {
        return cast(super.orderMap(order));
    }

    @Override
    public HawkularPipeline<S, Vertex> out(int branchFactor, String... labels) {
        return cast(super.out(branchFactor, labels));
    }

    @Override
    public HawkularPipeline<S, Vertex> out(String... labels) {
        return cast(super.out(labels));
    }

    @Override
    public HawkularPipeline<S, Edge> outE(int branchFactor, String... labels) {
        return cast(super.outE(branchFactor, labels));
    }

    @Override
    public HawkularPipeline<S, Edge> outE(String... labels) {
        return cast(super.outE(labels));
    }

    @Override
    public HawkularPipeline<S, Vertex> outV() {
        return cast(super.outV());
    }

    @Override
    public HawkularPipeline<S, List> path(PipeFunction... pathFunctions) {
        return cast(super.path(pathFunctions));
    }

    @Override
    public HawkularPipeline<S, Object> property(String key) {
        return cast(super.property(key));
    }

    @Override
    public HawkularPipeline<S, E> random(Double bias) {
        return cast(super.random(bias));
    }

    @Override
    public HawkularPipeline<S, E> range(int low, int high) {
        return cast(super.range(low, high));
    }

    @Override
    public void remove() {
        super.remove();
    }

    @Override
    public HawkularPipeline<S, E> retain(Collection<E> collection) {
        return cast(super.retain(collection));
    }

    @Override
    public HawkularPipeline<S, E> retain(String... namedSteps) {
        return cast(super.retain(namedSteps));
    }

    @Override
    public HawkularPipeline<S, ?> scatter() {
        return cast(super.scatter());
    }

    @Override
    public HawkularPipeline<S, Row> select() {
        return cast(super.select());
    }

    @Override
    public HawkularPipeline<S, Row> select(PipeFunction... columnFunctions) {
        return cast(super.select(columnFunctions));
    }

    @Override
    public HawkularPipeline<S, Row> select(Collection<String> stepNames, PipeFunction... columnFunctions) {
        return cast(super.select(stepNames, columnFunctions));
    }

    @Override
    public HawkularPipeline<S, List> shuffle() {
        return cast(super.shuffle());
    }

    @Override
    public HawkularPipeline<S, E> sideEffect(PipeFunction<E, ?> sideEffectFunction) {
        return cast(super.sideEffect(sideEffectFunction));
    }

    @Override
    public HawkularPipeline<S, E> simplePath() {
        return cast(super.simplePath());
    }

    @Override
    public HawkularPipeline<S, S> start(S object) {
        return cast(super.start(object));
    }

    @Override
    public HawkularPipeline<S, ?> step(PipeFunction function) {
        return cast(super.step(function));
    }

    @Override
    public <T> HawkularPipeline<S, T> step(Pipe<E, T> pipe) {
        return cast(super.step(pipe));
    }

    @Override
    public HawkularPipeline<S, E> store() {
        return cast(super.store());
    }

    @Override
    public HawkularPipeline<S, E> store(Collection storage, PipeFunction<E, ?> storageFunction) {
        return cast(super.store(storage, storageFunction));
    }

    @Override
    public HawkularPipeline<S, E> store(Collection<E> storage) {
        return cast(super.store(storage));
    }

    @Override
    public HawkularPipeline<S, E> store(PipeFunction<E, ?> storageFunction) {
        return cast(super.store(storageFunction));
    }

    @Override
    public HawkularPipeline<S, E> table() {
        return cast(super.table());
    }

    @Override
    public HawkularPipeline<S, E> table(PipeFunction... columnFunctions) {
        return cast(super.table(columnFunctions));
    }

    @Override
    public HawkularPipeline<S, E> table(Table table) {
        return cast(super.table(table));
    }

    @Override
    public HawkularPipeline<S, E> table(Table table, PipeFunction... columnFunctions) {
        return cast(super.table(table, columnFunctions));
    }

    @Override
    public HawkularPipeline<S, E> table(Table table, Collection<String> stepNames, PipeFunction... columnFunctions) {
        return cast(super.table(table, stepNames, columnFunctions));
    }

    @Override
    public <T> HawkularPipeline<S, T> transform(PipeFunction<E, T> function) {
        return cast(super.transform(function));
    }

    @Override
    public HawkularPipeline<S, E> tree(PipeFunction... branchFunctions) {
        return cast(super.tree(branchFunctions));
    }

    @Override
    public HawkularPipeline<S, E> tree(Tree tree, PipeFunction... branchFunctions) {
        return cast(super.tree(tree, branchFunctions));
    }

    @Override
    public HawkularPipeline<S, Vertex> V() {
        return cast(super.V());
    }

    @Override
    public HawkularPipeline<S, Vertex> V(String key, Object value) {
        return cast(super.V(key, value));
    }

    private <I, O> HawkularPipeline<I, O> cast(GremlinPipeline<I, O> thiz) {
        return (HawkularPipeline<I, O>) thiz;
    }
}
