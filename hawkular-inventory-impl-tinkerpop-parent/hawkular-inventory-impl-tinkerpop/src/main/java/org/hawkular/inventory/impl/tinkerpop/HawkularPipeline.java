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

/**
 * A slight extension of the Gremlin pipeline providing a couple of utility overloads of existing methods that accept
 * Hawkular specific arguments.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
class HawkularPipeline<S, E> /*extends GremlinPipeline<S, E> implements Cloneable */{
//
//    private int asLabelCount;
//    private final Deque<String> labelStack = new ArrayDeque<>(2);
//
//    private final Map<String, Long> counters = new HashMap<>();
//
//    public HawkularPipeline() {
//    }
//
//    public HawkularPipeline(Object starts) {
//        super(starts);
//    }
//
//    public HawkularPipeline(Object starts, boolean doQueryOptimization) {
//        super(starts, doQueryOptimization);
//    }
//
//    public String nextRandomLabel() {
//        //exceptionally random, right? ;)
//        return ")(*)#(*&$(" + asLabelCount++;
//    }
//
//    /**
//     * Together with {@link #recall()}, this is a simpler replacement of the {@link #as(String)} and
//     * {@link #back(String)} pair.
//     *
//     * <p>The call to this method will translate to an {@code as()} call with a "reasonably random" label.
//     * The subsequent call to {@link #remember()} will call a {@code back()} with that label.
//     *
//     * <p>The pipeline holds a stack of these labels so you can nest the {@code remember()} and {@code recall()} calls.
//     *
//     * @return the pipeline that "remembers" the current step
//     */
//    public HawkularPipeline<S, E> remember() {
//        String label = nextRandomLabel();
//        labelStack.push(label);
//        return as(label);
//    }
//
//    /**
//     * Recalls the last remembered step.
//     *
//     * See {@link #remember()} for a detailed description.
//     *
//     * @return the pipe line emitting the elements from the last remembered step
//     */
//    public HawkularPipeline<S, ?> recall() {
//        // Gremlin will barf on trying back() when no pipes were added since the last as().
//        // remember()+recall() shouldn't suffer from that condition - there might be situations
//        // during query generation where ensuring that might be more difficult than the simple
//        // check here.
//        String label = labelStack.pop();
//        List<Pipe> pipes = FluentUtility.removePreviousPipes(this, label);
//        if (this.pipes.isEmpty()) {
//            return this;
//        } else {
//            pipes.forEach(this::add);
//            return back(label);
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    public HawkularPipeline<S, Vertex> hasType(Constants.Type type) {
//        return (HawkularPipeline<S, Vertex>) has(Constants.Property.__type.name(), type.name());
//    }
//
//    public HawkularPipeline<S, ? extends Element> hasEid(String eid) {
//        return cast(has(Constants.Property.__eid.name(), eid));
//    }
//
//    public HawkularPipeline<S, ? extends Element> hasCanonicalPath(CanonicalPath path) {
//        return cast(has(Constants.Property.__cp.name(), path.toString()));
//    }
//
//    public HawkularPipeline<S, Vertex> out(Relationships.WellKnown... rel) {
//        String[] srels = new String[rel.length];
//        Arrays.setAll(srels, i -> rel[i].name());
//
//        return out(srels);
//    }
//
//    public HawkularPipeline<S, Vertex> in(Relationships.WellKnown... rel) {
//        String[] srels = new String[rel.length];
//        Arrays.setAll(srels, i -> rel[i].name());
//
//        return in(srels);
//    }
//
//    public HawkularPipeline<S, E> dropN(int n) {
//        add(new DropNPipe<>(n));
//        return this;
//    }
//
//    public HawkularPipeline<S, E> takeN(int n) {
//        add(new TakeNPipe<>(n, true));
//        return this;
//    }
//
//    public HawkularPipeline<S, ? extends Element> page(Pager pager) {
//        return cast(Element.class).page(pager, (e, p) -> {
//            String prop = Constants.Property.mapUserDefined(p);
//            return e.getProperty(prop);
//        });
//    }
//
//    public HawkularPipeline<S, E> page(Pager pager,
//            BiFunction<E, String, ? extends Comparable> propertyValueExtractor) {
//
//        List<Order> order = pager.getOrder();
//        if (!order.isEmpty()) {
//            //we have to have at least 1 order in the specific direction
//            boolean specific = false;
//            for (Order o : order) {
//                if (o.isSpecific()) {
//                    specific = true;
//                    break;
//                }
//            }
//            if (specific) {
//                //the order pipe holds on to the whole result set to be able to order, so we'd better do just
//                //1 order step.
//                this.order(p -> {
//                    int ret = 0;
//                    for (Order ord : order) {
//                        if (ord.isSpecific()) {
//                            Comparable a = propertyValueExtractor.apply(p.getA(), ord.getField());
//                            Comparable b = propertyValueExtractor.apply(p.getB(), ord.getField());
//                            ret = ord.isAscending() ? safeCompare(a, b) : safeCompare(b, a);
//                            if (ret != 0) {
//                                break;
//                            }
//                        }
//                    }
//                    return ret;
//                });
//            }
//        }
//
//        if (pager.isLimited()) {
//            if (pager.getStart() != 0) {
//                this.dropN(pager.getStart());
//            }
//            this.takeN(pager.getPageSize());
////            this.drainedRange(pager.getStart(), pager.getEnd() - 1);
//        }
//
//        return this;
//    }
//
//    private static <T extends Comparable<T>> int safeCompare(T a, T b) {
//        if (a == null) {
//            return b == null ? 0 : -1;
//        } else if (b == null) {
//            return 1;
//        } else {
//            return a.compareTo(b);
//        }
//    }
//
//    /**
//     * Counts the number of elements that passed through the pipeline at this position.
//     *
//     * @param name the name of the counter
//     * @return this pipeline
//     * @see #getCount(String)
//     */
//    public HawkularPipeline<S, E> counter(String name) {
//        this.sideEffect(e -> counters.put(name, counters.getOrDefault(name, 0L) + 1));
//        return this;
//    }
//
//    /**
//     * Returns the value of the counter previously "installed" by the {@link #counter(String)} method.
//     *
//     * @param counterName the name of the counter to get the count of
//     * @return the count or -1 if the counter with given name was not found
//     */
//    public long getCount(String counterName) {
//        return counters.getOrDefault(counterName, -1L);
//    }
//
//    /**
//     * @deprecated don't use this, use {@link #__()} to not cause java8 warnings
//     * @return this pipeline
//     */
//    @Override
//    @Deprecated
//    public HawkularPipeline<S, E> _() {
//        return cast(super._());
//    }
//
//    public HawkularPipeline<S, E> __() {
//        return cast(super._());
//    }
//
//    @Override
//    public <T> HawkularPipeline<S, T> add(Pipe<?, T> pipe) {
//        return cast(super.add(pipe));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> aggregate() {
//        return cast(super.aggregate());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> aggregate(Collection aggregate, PipeFunction<E, ?> aggregateFunction) {
//        return cast(super.aggregate(aggregate, aggregateFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> aggregate(Collection<E> aggregate) {
//        return cast(super.aggregate(aggregate));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> aggregate(PipeFunction<E, ?> aggregateFunction) {
//        return cast(super.aggregate(aggregateFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> and(Pipe<E, ?>... pipes) {
//        return cast(super.and(pipes));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> as(String name) {
//        return cast(super.as(name));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> back(String namedStep) {
//        return cast(super.back(namedStep));
//    }
//
//    @Override
//    @Deprecated
//    public HawkularPipeline<S, ?> back(int numberedStep) {
//        return cast(super.back(numberedStep));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> both(int branchFactor, String... labels) {
//        return cast(super.both(branchFactor, labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> both(String... labels) {
//        return cast(super.both(labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> bothE(int branchFactor, String... labels) {
//        return cast(super.bothE(branchFactor, labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> bothE(String... labels) {
//        return cast(super.bothE(labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> bothV() {
//        return cast(super.bothV());
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> cap() {
//        return cast(super.cap());
//    }
//
//    @Override
//    public <E1> HawkularPipeline<S, E1> cast(Class<E1> end) {
//        return cast(super.cast(end));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> copySplit(Pipe<E, ?>... pipes) {
//        return cast(super.copySplit(pipes));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> dedup() {
//        return cast(super.dedup());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> dedup(PipeFunction<E, ?> dedupFunction) {
//        return cast(super.dedup(dedupFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> E() {
//        return cast(super.E());
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> E(String key, Object value) {
//        return cast(super.E(key, value));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> enablePath() {
//        return cast(super.enablePath());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> except(Collection<E> collection) {
//        return cast(super.except(collection));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> except(String... namedSteps) {
//        return cast(super.except(namedSteps));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> exhaustMerge() {
//        return cast(super.exhaustMerge());
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> fairMerge() {
//        return cast(super.fairMerge());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> filter(PipeFunction<E, Boolean> filterFunction) {
//        return cast(super.filter(filterFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, List> gather() {
//        return cast(super.gather());
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> gather(PipeFunction<List, ?> function) {
//        return cast(super.gather(function));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupBy(PipeFunction keyFunction, PipeFunction valueFunction) {
//        return cast(super.groupBy(keyFunction, valueFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupBy(PipeFunction keyFunction, PipeFunction valueFunction,
//            PipeFunction reduceFunction) {
//        return cast(super.groupBy(keyFunction, valueFunction, reduceFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupBy(Map<?, List<?>> map, PipeFunction keyFunction, PipeFunction valueFunction) {
//        return cast(super.groupBy(map, keyFunction, valueFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupBy(Map reduceMap, PipeFunction keyFunction, PipeFunction valueFunction,
//            PipeFunction reduceFunction) {
//        return cast(super.groupBy(reduceMap, keyFunction, valueFunction, reduceFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupCount() {
//        return cast(super.groupCount());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupCount(PipeFunction keyFunction) {
//        return cast(super.groupCount(keyFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupCount(PipeFunction keyFunction,
//            PipeFunction<Pair<?, Number>, Number> valueFunction) {
//        return cast(super.groupCount(keyFunction, valueFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupCount(Map<?, Number> map) {
//        return cast(super.groupCount(map));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupCount(Map<?, Number> map, PipeFunction keyFunction) {
//        return cast(super.groupCount(map, keyFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> groupCount(Map<?, Number> map, PipeFunction keyFunction,
//            PipeFunction<Pair<?, Number>, Number> valueFunction) {
//        return cast(super.groupCount(map, keyFunction, valueFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, ? extends Element> has(String key) {
//        return cast(super.has(key));
//    }
//
//    @Override
//    public HawkularPipeline<S, ? extends Element> has(String key, Tokens.T compareToken, Object value) {
//        return cast(super.has(key, compareToken, value));
//    }
//
//    @Override
//    public HawkularPipeline<S, ? extends Element> has(String key, Predicate predicate, Object value) {
//        return cast(super.has(key, predicate, value));
//    }
//
//    @Override
//    public HawkularPipeline<S, ? extends Element> has(String key, Object value) {
//        return cast(super.has(key, value));
//    }
//
//    @Override
//    public HawkularPipeline<S, ? extends Element> hasNot(String key) {
//        return cast(super.hasNot(key));
//    }
//
//    @Override
//    public HawkularPipeline<S, ? extends Element> hasNot(String key, Object value) {
//        return cast(super.hasNot(key, value));
//    }
//
//    @Override
//    public HawkularPipeline<S, Object> id() {
//        return cast(super.id());
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> idEdge(Graph graph) {
//        return cast(super.idEdge(graph));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> idVertex(Graph graph) {
//        return cast(super.idVertex(graph));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> ifThenElse(PipeFunction<E, Boolean> ifFunction, PipeFunction<E, ?> thenFunction,
//            PipeFunction<E, ?> elseFunction) {
//        return cast(super.ifThenElse(ifFunction, thenFunction, elseFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> in(int branchFactor, String... labels) {
//        return cast(super.in(branchFactor, labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> in(String... labels) {
//        return cast(super.in(labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> inE(int branchFactor, String... labels) {
//        return cast(super.inE(branchFactor, labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> inE(String... labels) {
//        return cast(super.inE(labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, ? extends Element> interval(String key, Comparable startValue, Comparable endValue) {
//        return cast(super.interval(key, startValue, endValue));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> inV() {
//        return cast(super.inV());
//    }
//
//    @Override
//    public void iterate() {
//        super.iterate();
//    }
//
//    @Override
//    public HawkularPipeline<S, String> label() {
//        return cast(super.label());
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> linkBoth(String label, String namedStep) {
//        return cast(super.linkBoth(label, namedStep));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> linkBoth(String label, Vertex other) {
//        return cast(super.linkBoth(label, other));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> linkIn(String label, String namedStep) {
//        return cast(super.linkIn(label, namedStep));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> linkIn(String label, Vertex other) {
//        return cast(super.linkIn(label, other));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> linkOut(String label, String namedStep) {
//        return cast(super.linkOut(label, namedStep));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> linkOut(String label, Vertex other) {
//        return cast(super.linkOut(label, other));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> loop(String namedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction) {
//        return cast(super.loop(namedStep, whileFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> loop(String namedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction,
//            PipeFunction<LoopPipe.LoopBundle<E>, Boolean> emitFunction) {
//        return cast(super.loop(namedStep, whileFunction, emitFunction));
//    }
//
//    @Override
//    @Deprecated
//    public HawkularPipeline<S, E> loop(int numberedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction) {
//        return cast(super.loop(numberedStep, whileFunction));
//    }
//
//    @Override
//    @Deprecated
//    public HawkularPipeline<S, E> loop(int numberedStep, PipeFunction<LoopPipe.LoopBundle<E>, Boolean> whileFunction,
//            PipeFunction<LoopPipe.LoopBundle<E>, Boolean> emitFunction) {
//        return cast(super.loop(numberedStep, whileFunction, emitFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, Map<String, Object>> map(String... keys) {
//        return cast(super.map(keys));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> memoize(String namedStep) {
//        return cast(super.memoize(namedStep));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> memoize(String namedStep, Map map) {
//        return cast(super.memoize(namedStep, map));
//    }
//
//    @Override
//    @Deprecated
//    public HawkularPipeline<S, E> memoize(int numberedStep) {
//        return cast(super.memoize(numberedStep));
//    }
//
//    @Override
//    @Deprecated
//    public HawkularPipeline<S, E> memoize(int numberedStep, Map map) {
//        return cast(super.memoize(numberedStep, map));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> optimize(boolean optimize) {
//        return cast(super.optimize(optimize));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> optional(String namedStep) {
//        return cast(super.optional(namedStep));
//    }
//
//    @Override
//    @Deprecated
//    public HawkularPipeline<S, ?> optional(int numberedStep) {
//        return cast(super.optional(numberedStep));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> or(Pipe<E, ?>... pipes) {
//        return cast(super.or(pipes));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> order() {
//        return cast(super.order());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> order(PipeFunction<Pair<E, E>, Integer> compareFunction) {
//        return cast(super.order(compareFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> order(TransformPipe.Order order) {
//        return cast(super.order(order));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> order(Tokens.T order) {
//        return cast(super.order(order));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> orderMap(PipeFunction<Pair<Map.Entry, Map.Entry>, Integer> compareFunction) {
//        return cast(super.orderMap(compareFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> orderMap(TransformPipe.Order order) {
//        return cast(super.orderMap(order));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> orderMap(Tokens.T order) {
//        return cast(super.orderMap(order));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> out(int branchFactor, String... labels) {
//        return cast(super.out(branchFactor, labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> out(String... labels) {
//        return cast(super.out(labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> outE(int branchFactor, String... labels) {
//        return cast(super.outE(branchFactor, labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Edge> outE(String... labels) {
//        return cast(super.outE(labels));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> outV() {
//        return cast(super.outV());
//    }
//
//    @Override
//    public HawkularPipeline<S, List> path(PipeFunction... pathFunctions) {
//        return cast(super.path(pathFunctions));
//    }
//
//    @Override
//    public HawkularPipeline<S, Object> property(String key) {
//        return cast(super.property(key));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> random(Double bias) {
//        return cast(super.random(bias));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> range(int low, int high) {
//        return cast(super.range(low, high));
//    }
//
//    public HawkularPipeline<S, E> drainedRange(int low, int high) {
//        add(new DrainedRangeFilterPipe<>(low, high));
//        return this;
//    }
//
//    @Override
//    public void remove() {
//        super.remove();
//    }
//
//    @Override
//    public HawkularPipeline<S, E> retain(Collection<E> collection) {
//        return cast(super.retain(collection));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> retain(String... namedSteps) {
//        return cast(super.retain(namedSteps));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> scatter() {
//        return cast(super.scatter());
//    }
//
//    @Override
//    public HawkularPipeline<S, Row> select() {
//        return cast(super.select());
//    }
//
//    @Override
//    public HawkularPipeline<S, Row> select(PipeFunction... columnFunctions) {
//        return cast(super.select(columnFunctions));
//    }
//
//    @Override
//    public HawkularPipeline<S, Row> select(Collection<String> stepNames, PipeFunction... columnFunctions) {
//        return cast(super.select(stepNames, columnFunctions));
//    }
//
//    @Override
//    public HawkularPipeline<S, List> shuffle() {
//        return cast(super.shuffle());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> sideEffect(PipeFunction<E, ?> sideEffectFunction) {
//        return cast(super.sideEffect(sideEffectFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> simplePath() {
//        return cast(super.simplePath());
//    }
//
//    @Override
//    public HawkularPipeline<S, S> start(S object) {
//        return cast(super.start(object));
//    }
//
//    @Override
//    public HawkularPipeline<S, ?> step(PipeFunction function) {
//        return cast(super.step(function));
//    }
//
//    @Override
//    public <T> HawkularPipeline<S, T> step(Pipe<E, T> pipe) {
//        return cast(super.step(pipe));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> store() {
//        return cast(super.store());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> store(Collection storage, PipeFunction<E, ?> storageFunction) {
//        return cast(super.store(storage, storageFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> store(Collection<E> storage) {
//        return cast(super.store(storage));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> store(PipeFunction<E, ?> storageFunction) {
//        return cast(super.store(storageFunction));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> table() {
//        return cast(super.table());
//    }
//
//    @Override
//    public HawkularPipeline<S, E> table(PipeFunction... columnFunctions) {
//        return cast(super.table(columnFunctions));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> table(Table table) {
//        return cast(super.table(table));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> table(Table table, PipeFunction... columnFunctions) {
//        return cast(super.table(table, columnFunctions));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> table(Table table, Collection<String> stepNames, PipeFunction... columnFunctions) {
//        return cast(super.table(table, stepNames, columnFunctions));
//    }
//
//    @Override
//    public <T> HawkularPipeline<S, T> transform(PipeFunction<E, T> function) {
//        return cast(super.transform(function));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> tree(PipeFunction... branchFunctions) {
//        return cast(super.tree(branchFunctions));
//    }
//
//    @Override
//    public HawkularPipeline<S, E> tree(Tree tree, PipeFunction... branchFunctions) {
//        return cast(super.tree(tree, branchFunctions));
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> V() {
//        return cast(super.V());
//    }
//
//    @Override
//    public HawkularPipeline<S, Vertex> V(String key, Object value) {
//        return cast(super.V(key, value));
//    }
//
//    @Override
//    public String toString() {
//        StringBuilder bld = new StringBuilder();
//        pipes.forEach(p -> PipeVisitor.visit(p, new PipeVisitor<StringBuilder>() {
//            @Override public void defaultAction(Pipe<?, ?> pipe, StringBuilder p) {
//                if (!pipe.getClass().getName().endsWith("CopyExpandablePipe")) {
//                    p.append(".").append("<<").append(pipe).append(">>");
//                }
//            }
//
//            @Override public void visitStart(StartPipe<?> pipe, StringBuilder p) {
//                p.append(".start(").append((Object) getFieldValue("starts", pipe)).append(")");
//            }
//
//            @Override public void visitGraphQuery(GraphQueryPipe<?> pipe, StringBuilder p) {
//                Class<?> elementClass = getFieldValue("elementClass", pipe);
//                if (Vertex.class.equals(elementClass)) {
//                    p.append(".V()");
//                } else {
//                    p.append(".E()");
//                }
//
//                append(this.<List<QueryPipe.HasContainer>>getFieldValue("hasContainers", pipe), bld);
//            }
//
//            @Override public void visitVertexQuery(VertexQueryPipe<?> pipe, StringBuilder p) {
//                Direction direction = getFieldValue("direction", pipe);
//                String[] labels = getFieldValue("labels", pipe);
//                List<QueryPipe.HasContainer> hasContainers = getFieldValue("hasContainers", pipe);
//                Class<?> elementClass = getFieldValue("elementClass", pipe);
//
//                switch (direction) {
//                    case OUT:
//                        p.append(".out");
//                        break;
//                    case IN:
//                        p.append(".in");
//                        break;
//                    case BOTH:
//                        p.append(".both");
//                }
//
//                if (Edge.class.equals(elementClass)) {
//                    p.append("E");
//                }
//
//                p.append("(");
//                p.append("\"").append(labels.length > 0 ? labels[0] : "<none>").append("\"");
//                for (int i = 1; i < labels.length; ++i) {
//                    p.append(", \"").append(labels[i]).append("\"");
//                }
//
//                p.append(")");
//
//                append(hasContainers, p);
//            }
//
//            @Override public void visitInVertex(InVertexPipe pipe, StringBuilder p) {
//                p.append(".inV()");
//            }
//
//            @Override public void visitOutVertex(OutVertexPipe pipe, StringBuilder p) {
//                p.append(".outV()");
//            }
//
//            @Override public void visitIdentity(IdentityPipe pipe, StringBuilder p) {
//                p.append("._()");
//            }
//
//            @Override public void visitCopySplit(CopySplitPipe<?> pipe, StringBuilder p) {
//                p.append(".copySplit(");
//                List<Pipeline<?, ?>> pipes = getFieldValue("pipes", pipe);
//                Iterator<Pipeline<?, ?>> it = pipes.iterator();
//                if (it.hasNext()) {
//                    PipeVisitor.visit(it.next(), this, p);
//                }
//
//                while (it.hasNext()) {
//                    p.append(", ");
//                    PipeVisitor.visit(it.next(), this, p);
//                }
//
//                p.append(")");
//            }
//
//            @Override public void visitExhaustMerge(ExhaustMergePipe<?> pipe, StringBuilder p) {
//                p.append(".exhaustMerge()");
//            }
//
//            @Override public void visitFairMerge(FairMergePipe<?> pipe, StringBuilder p) {
//                p.append(".fairMerge()");
//            }
//
//            @SuppressWarnings("unchecked")
//            @Override public void visitPipeline(Pipeline pipeline, StringBuilder p) {
//                p.append(pipeline.getClass().getSimpleName());
//                pipeline.getPipes().forEach(pipe -> PipeVisitor.visit((Pipe<?, ?>) pipe, this, p));
//            }
//
//            @Override public void visitAs(AsPipe<?, ?> pipe, StringBuilder p) {
//                p.append(".as(\"").append(pipe.getName()).append("\")");
//                pipe.getPipes().forEach(cp -> PipeVisitor.visit(cp, this, p));
//            }
//
//            @Override public void visitLoop(LoopPipe<?> pipe, StringBuilder p) {
//                p.append(".loop(");
//                PipeVisitor.visit(pipe.getPipes().get(0), this, p);
//                p.append(")");
//            }
//
//            @Override public void visitBackFilter(BackFilterPipe<?> pipe, StringBuilder p) {
//                p.append("???BACK???");
//            }
//
//            private void append(List<QueryPipe.HasContainer> containers, StringBuilder bld) {
//                if (containers == null) {
//                    return;
//                }
//
//                containers.forEach(c -> append(c, bld));
//            }
//
//            private void append(QueryPipe.HasContainer container, StringBuilder bld) {
//                bld.append(".has(").append(container.key).append(", ").append(container.predicate)
//                        .append(", ").append(container.value).append(")");
//            }
//
//            private <T> T getFieldValue(String fieldName, Object object) {
//                return getFieldValue(fieldName, object, object.getClass());
//            }
//
//            @SuppressWarnings("unchecked")
//            private <T> T getFieldValue(String fieldName, Object object, Class<?> declaringClass) {
//                try {
//                    Field f = declaringClass.getDeclaredField(fieldName);
//                    f.setAccessible(true);
//                    try {
//                        return (T) f.get(object);
//                    } catch (IllegalAccessException e) {
//                        //doesn't happen
//                        throw new AssertionError();
//                    }
//                } catch (NoSuchFieldException e) {
//                    return getFieldValue(fieldName, object, declaringClass.getSuperclass());
//                }
//            }
//        }, bld));
//
//        return bld.toString();
//    }
//
//    private <I, O> HawkularPipeline<I, O> cast(GremlinPipeline<I, O> thiz) {
//        return (HawkularPipeline<I, O>) thiz;
//    }
//
//    private interface PipeVisitor<Param> {
//
//        static <Param> void visit(Pipe<?, ?> pipe, PipeVisitor<Param> visitor, Param p) {
//            if (pipe instanceof RangeFilterPipe) {
//                visitor.visitRangeFilter((RangeFilterPipe<?>) pipe, p);
//            } else if (pipe instanceof TakeNPipe) {
//                visitor.visitTakeN((TakeNPipe) pipe, p);
//            } else if (pipe instanceof OutPipe) {
//                visitor.visitOut((OutPipe) pipe, p);
//            } else if (pipe instanceof InPipe) {
//                visitor.visitIn((InPipe) pipe, p);
//            } else if (pipe instanceof BothPipe) {
//                visitor.visitBoth((BothPipe) pipe, p);
//            } else if (pipe instanceof AggregatePipe) {
//                visitor.visitAggregate((AggregatePipe) pipe, p);
//            } else if (pipe instanceof DrainedRangeFilterPipe) {
//                visitor.visitDrainedRangeFilter((DrainedRangeFilterPipe) pipe, p);
//            } else if (pipe instanceof PropertyPipe) {
//                visitor.visitProperty((PropertyPipe) pipe, p);
//            } else if (pipe instanceof ScatterPipe) {
//                visitor.visitScatter((ScatterPipe) pipe, p);
//            } else if (pipe instanceof TransformFunctionPipe) {
//                visitor.visitTransformFunction((TransformFunctionPipe) pipe, p);
//            } else if (pipe instanceof OrderMapPipe) {
//                visitor.visitOrderMap((OrderMapPipe) pipe, p);
//            } else if (pipe instanceof TablePipe) {
//                visitor.visitTable((TablePipe) pipe, p);
//            } else if (pipe instanceof RandomFilterPipe) {
//                visitor.visitRandomFilter((RandomFilterPipe) pipe, p);
//            } else if (pipe instanceof IdFilterPipe) {
//                visitor.visitIdFilter((IdFilterPipe) pipe, p);
//            } else if (pipe instanceof GroupCountFunctionPipe) {
//                visitor.visitGroupCountFunction((GroupCountFunctionPipe) pipe, p);
//            } else if (pipe instanceof ExceptFilterPipe) {
//                visitor.visitExceptFilter((ExceptFilterPipe) pipe, p);
//            } else if (pipe instanceof RetainFilterPipe) {
//                visitor.visitRetainFilter((RetainFilterPipe) pipe, p);
//            } else if (pipe instanceof IntervalFilterPipe) {
//                visitor.visitIntervalFilter((IntervalFilterPipe) pipe, p);
//            } else if (pipe instanceof OutVertexPipe) {
//                visitor.visitOutVertex((OutVertexPipe) pipe, p);
//            } else if (pipe instanceof InVertexPipe) {
//                visitor.visitInVertex((InVertexPipe) pipe, p);
//            } else if (pipe instanceof BothVerticesPipe) {
//                visitor.visitBothVertices((BothVerticesPipe) pipe, p);
//            } else if (pipe instanceof FilterFunctionPipe) {
//                visitor.visitFilterFunction((FilterFunctionPipe) pipe, p);
//            } else if (pipe instanceof PathPipe) {
//                visitor.visitPath((PathPipe) pipe, p);
//            } else if (pipe instanceof OrderPipe) {
//                visitor.visitOrder((OrderPipe) pipe, p);
//            } else if (pipe instanceof OutEdgesPipe) {
//                visitor.visitOutEdges((OutEdgesPipe) pipe, p);
//            } else if (pipe instanceof InEdgesPipe) {
//                visitor.visitInEdges((InEdgesPipe) pipe, p);
//            } else if (pipe instanceof BothEdgesPipe) {
//                visitor.visitBothEdges((BothEdgesPipe) pipe, p);
//            } else if (pipe instanceof StorePipe) {
//                visitor.visitStore((StorePipe) pipe, p);
//            } else if (pipe instanceof GatherPipe) {
//                visitor.visitGather((GatherPipe) pipe, p);
//            } else if (pipe instanceof StartPipe) {
//                visitor.visitStart((StartPipe) pipe, p);
//            } else if (pipe instanceof CyclicPathFilterPipe) {
//                visitor.visitCyclicPathFilter((CyclicPathFilterPipe) pipe, p);
//            } else if (pipe instanceof GroupCountPipe) {
//                visitor.visitGroupCount((GroupCountPipe) pipe, p);
//            } else if (pipe instanceof FunctionPipe) {
//                visitor.visitFunction((FunctionPipe) pipe, p);
//            } else if (pipe instanceof SelectPipe) {
//                visitor.visitSelect((SelectPipe) pipe, p);
//            } else if (pipe instanceof PropertyMapPipe) {
//                visitor.visitPropertyMap((PropertyMapPipe) pipe, p);
//            } else if (pipe instanceof ObjectFilterPipe) {
//                visitor.visitObjectFilter((ObjectFilterPipe) pipe, p);
//            } else if (pipe instanceof TreePipe) {
//                visitor.visitTree((TreePipe) pipe, p);
//            } else if (pipe instanceof GraphQueryPipe) {
//                visitor.visitGraphQuery((GraphQueryPipe) pipe, p);
//            } else if (pipe instanceof VertexQueryPipe) {
//                visitor.visitVertexQuery((VertexQueryPipe) pipe, p);
//            } else if (pipe instanceof ShufflePipe) {
//                visitor.visitShuffle((ShufflePipe) pipe, p);
//            } else if (pipe instanceof CountPipe) {
//                visitor.visitCount((CountPipe) pipe, p);
//            } else if (pipe instanceof MemoizePipe) {
//                visitor.visitMemoize((MemoizePipe) pipe, p);
//            } else if (pipe instanceof ExhaustMergePipe) {
//                visitor.visitExhaustMerge((ExhaustMergePipe) pipe, p);
//            } else if (pipe instanceof LoopPipe) {
//                visitor.visitLoop((LoopPipe) pipe, p);
//            } else if (pipe instanceof AndFilterPipe) {
//                visitor.visitAndFilter((AndFilterPipe) pipe, p);
//            } else if (pipe instanceof BackFilterPipe) {
//                visitor.visitBackFilter((BackFilterPipe) pipe, p);
//            } else if (pipe instanceof FutureFilterPipe) {
//                visitor.visitFutureFilter((FutureFilterPipe) pipe, p);
//            } else if (pipe instanceof OptionalPipe) {
//                visitor.visitOptional((OptionalPipe) pipe, p);
//            } else if (pipe instanceof FairMergePipe) {
//                visitor.visitFairMerge((FairMergePipe) pipe, p);
//            } else if (pipe instanceof CopySplitPipe) {
//                visitor.visitCopySplit((CopySplitPipe) pipe, p);
//            } else if (pipe instanceof OrFilterPipe) {
//                visitor.visitOrFilter((OrFilterPipe) pipe, p);
//            } else if (pipe instanceof AsPipe) {
//                visitor.visitAs((AsPipe) pipe, p);
//            } else if (pipe instanceof SideEffectCapPipe) {
//                visitor.visitSideEffectCap((SideEffectCapPipe) pipe, p);
//            } else if (pipe instanceof HasNextPipe) {
//                visitor.visitHasNext((HasNextPipe) pipe, p);
//            } else if (pipe instanceof DuplicateFilterPipe) {
//                visitor.visitDuplicateFilter((DuplicateFilterPipe) pipe, p);
//            } else if (pipe instanceof IdEdgePipe) {
//                visitor.visitIdEdge((IdEdgePipe) pipe, p);
//            } else if (pipe instanceof LabelFilterPipe) {
//                visitor.visitLabelFilter((LabelFilterPipe) pipe, p);
//            } else if (pipe instanceof IfThenElsePipe) {
//                visitor.visitIfTheElse((IfThenElsePipe) pipe, p);
//            } else if (pipe instanceof PropertyFilterPipe) {
//                visitor.visitPropertyFilter((PropertyFilterPipe) pipe, p);
//            } else if (pipe instanceof GroupByPipe) {
//                visitor.visitGroupBy((GroupByPipe) pipe, p);
//            } else if (pipe instanceof GatherFunctionPipe) {
//                visitor.visitGatherFunction((GatherFunctionPipe) pipe, p);
//            } else if (pipe instanceof LinkPipe) {
//                visitor.visitLink((LinkPipe) pipe, p);
//            } else if (pipe instanceof DropNPipe) {
//                visitor.visitDropN((DropNPipe) pipe, p);
//            } else if (pipe instanceof IdPipe) {
//                visitor.visitId((IdPipe) pipe, p);
//            } else if (pipe instanceof LabelPipe) {
//                visitor.visitLabel((LabelPipe) pipe, p);
//            } else if (pipe instanceof HasCountPipe) {
//                visitor.visitHasCount((HasCountPipe) pipe, p);
//            } else if (pipe instanceof IdentityPipe) {
//                visitor.visitIdentity((IdentityPipe) pipe, p);
//            } else if (pipe instanceof Pipeline) {
//                visitor.visitPipeline((Pipeline) pipe, p);
//            } else {
//                visitor.defaultAction(pipe, p);
//            }
//        }
//
//        default void defaultAction(Pipe<?, ?> pipe, Param p) {
//
//        }
//
//        default void visitRangeFilter(RangeFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitTakeN(TakeNPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitOut(OutPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitIn(InPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitBoth(BothPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitAggregate(AggregatePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitDrainedRangeFilter(DrainedRangeFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitProperty(PropertyPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitScatter(ScatterPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitTransformFunction(TransformFunctionPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitOrderMap(OrderMapPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitTable(TablePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitRandomFilter(RandomFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitIdFilter(IdFilterPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitGroupCountFunction(GroupCountFunctionPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitExceptFilter(ExceptFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitRetainFilter(RetainFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitIntervalFilter(IntervalFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitOutVertex(OutVertexPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitInVertex(InVertexPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitBothVertices(BothVerticesPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitFilterFunction(FilterFunctionPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitPath(PathPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitOrder(OrderPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitOutEdges(OutEdgesPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitInEdges(InEdgesPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitBothEdges(BothEdgesPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitStore(StorePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitGather(GatherPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitStart(StartPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitCyclicPathFilter(CyclicPathFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitGroupCount(GroupCountPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitFunction(FunctionPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitSelect(SelectPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitPropertyMap(PropertyMapPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitObjectFilter(ObjectFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitTree(TreePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitGraphQuery(GraphQueryPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitVertexQuery(VertexQueryPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitShuffle(ShufflePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitCount(CountPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitMemoize(MemoizePipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitExhaustMerge(ExhaustMergePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitLoop(LoopPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitAndFilter(AndFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitBackFilter(BackFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitFutureFilter(FutureFilterPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitOptional(OptionalPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitFairMerge(FairMergePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitCopySplit(CopySplitPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitOrFilter(OrFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitAs(AsPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitSideEffectCap(SideEffectCapPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitHasNext(HasNextPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitDuplicateFilter(DuplicateFilterPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitIdEdge(IdEdgePipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitLabelFilter(LabelFilterPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitIfTheElse(IfThenElsePipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitPropertyFilter(PropertyFilterPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitGroupBy(GroupByPipe<?, ?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitGatherFunction(GatherFunctionPipe<?, ?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitLink(LinkPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitDropN(DropNPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitId(IdPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitLabel(LabelPipe pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitHasCount(HasCountPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitIdentity(IdentityPipe<?> pipe, Param p) {
//            defaultAction(pipe, p);
//        }
//
//        default void visitPipeline(Pipeline pipeline, Param p) {
//            defaultAction(pipeline, p);
//        }
//    }
}
