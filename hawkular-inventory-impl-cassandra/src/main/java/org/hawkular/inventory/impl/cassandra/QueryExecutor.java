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
package org.hawkular.inventory.impl.cassandra;

import static java.util.stream.Collectors.toList;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.impl.cassandra.Statements.CP;
import static org.hawkular.inventory.impl.cassandra.Statements.ID;
import static org.hawkular.inventory.impl.cassandra.Statements.SOURCE_CP;
import static org.hawkular.inventory.impl.cassandra.Statements.SOURCE_ID;
import static org.hawkular.inventory.impl.cassandra.Statements.SOURCE_TYPE;
import static org.hawkular.inventory.impl.cassandra.Statements.TARGET_CP;
import static org.hawkular.inventory.impl.cassandra.Statements.TARGET_ID;
import static org.hawkular.inventory.impl.cassandra.Statements.TARGET_TYPE;
import static org.hawkular.inventory.impl.cassandra.Statements.TYPE;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.hawkular.inventory.api.PathFragment;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.QueryFragment;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Marker;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.jboss.logging.Logger;

import com.datastax.driver.core.Row;

import rx.Observable;
import rx.functions.Func2;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class QueryExecutor {
    private static final Logger DBG = Logger.getLogger(QueryExecutor.class);

    private final Statements statements;

    QueryExecutor(Statements statements) {
        this.statements = statements;
    }

    Observable<Row> execute(Query query) {
        DBG.debugf("Execute of %s", query);

        Observable<Row> data;
        TraversalState traversalState = new TraversalState();
        if (query.getFragments().length == 0) {
            DBG.debugf("Empty query, returning empty observable");

            return Observable.empty();
        } else {
            DBG.debugf("Starting the query %s with the first filter %s", query, query.getFragments()[0].getFilter());

            data = start(query.getFragments()[0].getFilter(), traversalState);
        }

        return traverse(data, query, 1, traversalState);
    }

    Observable<Row> traverse(Row startingPoint, Query query) {
        DBG.debugf("Executing traverse %s from %s", query, startingPoint);

        Observable<Row> res = Observable.just(startingPoint);

        TraversalState state = new TraversalState();

        state.inEdges = startingPoint.getColumnDefinitions().contains(SOURCE_CP);

        return traverse(res, query, 0, state);
    }

    private Observable<Row> processSubTrees(Observable<Row> data, Query query, TraversalState state) {
        if (!query.getSubTrees().isEmpty()) {
            return data.flatMap(r -> {
                DBG.debugf("IN FLIGHT: Appending subtrees to partial query results of query %s", query);

                Observable<Row> res = Observable.empty();
                for (Query q : query.getSubTrees()) {
                    DBG.debugf("IN FLIGHT: Merging subquery %s to partial query results of query %s", q, query);

                    res = res.mergeWith(traverse(Observable.just(r), q, 0, state.clone()));
                }

                return res;
            });
        } else {
            DBG.debugf("No subqueries found in %s. Returing the observable as is.", query);
            return data;
        }
    }

    private Observable<Row> traverse(Observable<Row> data, Query query, int startWithFragmentIndex,
                                     TraversalState state) {
        QueryFragment[] qfs = query.getFragments();

        DBG.debugf("Traversing query fragments starting at %d of query %s", startWithFragmentIndex, query);

        for (int i = startWithFragmentIndex; i < qfs.length; ++i) {
            //TODO this is wrong - most probably we will be able to join multiple following filters into a single
            //statement
            QueryFragment qf = qfs[i];
            if (qf instanceof PathFragment) {
                data = progress(qf.getFilter(), data, state);
            } else {
                data = filter(qf.getFilter(), data, state);
            }
        }

        DBG.debugf("Applying subqueries to %s", query);

        return processSubTrees(data, query, state);
    }

    private Observable<Row> start(Filter initialFilter, TraversalState state) {
        return execStatements(initialFilter, null, state);
    }

    private Observable<Row> progress(Filter progressFilter, Observable<Row> intermediateResults, TraversalState state) {
        return execStatements(progressFilter, intermediateResults, state);
    }

    private Observable<Row> filter(Filter filter, Observable<Row> intermediateResults, TraversalState state) {
        TraversalState incomingState = state.clone(); //capture the state as we obtained it...

        return intermediateResults.flatMap(r -> {
            DBG.debugf("IN FLIGHT: filter: applying filter %s on row %s by facilitating progress()", filter, r);
            Observable<Row> targets = progress(filter, Observable.just(r), incomingState);
            //TODO hmm... couldn't this be done using a query?
            return targets.take(1).map(any -> r);
        });
    }

    private Observable<Row> execStatements(Filter filter, Observable<Row> bounds, TraversalState state) {
        if (filter instanceof With.CanonicalPaths) {
            return execCps((With.CanonicalPaths) filter, bounds, state);
        } else if (filter instanceof With.Ids) {
            return execIds((With.Ids) filter, bounds, state);
        } else if (filter instanceof With.Types) {
            return execTypes((With.Types) filter, bounds, state);
        } else if (filter instanceof Related) {
            return execRelated((Related) filter, bounds, state);
        } else if (filter instanceof With.RelativePaths) {
            return execRelativePaths((With.RelativePaths) filter, bounds, state);
        } else if (filter instanceof Marker) {
            return execMarker((Marker) filter, bounds, state);
        } else {
            throw new IllegalArgumentException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private Observable<Row> execCps(With.CanonicalPaths filter, Observable<Row> bounds,
                                    TraversalState state) {
        DBG.debugf("Creating statement for CP filter %s", filter);

        String prop = state.propertyNameBasedOnState(TYPE);

        return doFilter(
                Stream.of(filter.getPaths()).map(CanonicalPath::toString).collect(toList()),
                bounds,
                state,
                statements::findEntityByCanonicalPath,
                statements::findEntityByCanonicalPaths,
                r -> r.getString(prop),
                null
        );
    }

    private Observable<Row> execIds(With.Ids filter, Observable<Row> bounds, TraversalState state) {
        DBG.debugf("Creating statement for ID filter %s", filter);
        String prop = state.propertyNameBasedOnState(TYPE);

        return doFilter(
                Stream.of(filter.getIds()).collect(toList()),
                bounds,
                state,
                statements::findEntityCpsById,
                statements::findEntityCpsByIds,
                r -> r.getString(prop),
                (rows, constraints) -> {
                    List<String> cps = rows.stream().map(r -> r.getString(CP)).collect(toList());
                    return statements.findEntityByCanonicalPaths(cps);
                }
        );
    }

    private Observable<Row> execTypes(With.Types filter, Observable<Row> bounds, TraversalState state) {
        DBG.debugf("Creating statement for type filter %s", filter);
        String prop = state.propertyNameBasedOnState(TYPE);

        return doFilter(
                Stream.of(filter.getSegmentTypes()).collect(toList()),
                bounds,
                state,
                statements::findEntityCpsByType,
                statements::findEntityCpsByTypes,
                r -> SegmentType.values()[r.getInt(prop)],
                (rows, constraints) -> {
                    List<String> cps = rows.stream().map(r -> r.getString(CP)).collect(toList());
                    return statements.findEntityByCanonicalPaths(cps);
                }
        );
    }

    private Observable<Row> execRelated(Related filter, Observable<Row> bounds, TraversalState state) {
        Observable<Row> ret = goBackFromEdges(bounds, state);

        String tmp1 = null;
        String tmp2 = null;
        if (filter.getEntityPath() != null) {
            tmp1 = filter.getEntityPath().toString();
            tmp2 = state.chooseBasedOnDirection(CP, TARGET_CP, SOURCE_CP);
        }
        String otherEndEntityFilterProp = tmp1;
        String otherEndEntityFilterValue = tmp2;

        boolean applied = false;
        switch (filter.getEntityRole()) {
            case TARGET:
                if (null != filter.getRelationshipName()) {
                    state.inEdges = true;
                    state.comingFrom = Relationships.Direction.incoming;
                    ret = ret.flatMap(r -> Observable.just(r.getString(CP))).toList()
                            .flatMap(cps -> statements.findRelationshipInsByTargetCpsAndName(cps,
                                    filter.getRelationshipName()));
                    applied = true;
                }
                if (null != filter.getRelationshipId()) {
                    state.inEdges = true;
                    state.comingFrom = Relationships.Direction.incoming;
                    // TODO test
                    String cp = CanonicalPath.of().relationship(filter.getRelationshipId()).get().toString();
                    //not too efficient, but this is rarely used...
                    if (applied) {
                        ret = ret.filter(r -> r.getString(CP).equals(cp));
                    } else {
                        ret = ret.flatMap(r -> Observable.just(r.getString(CP))).toList()
                                .flatMap(statements::findRelationshipInsByTargetCps)
                                .filter(r -> r.getString(CP).equals(cp));
                    }
                }
                break;
            case SOURCE:
                if (null != filter.getRelationshipName()) {
                    state.inEdges = true;
                    state.comingFrom = Relationships.Direction.outgoing;
                    ret = ret.flatMap(r -> Observable.just(r.getString(CP))).toList()
                            .flatMap(cps -> statements.findRelationshipOutsBySourceCpsAndName(
                                    cps, filter.getRelationshipName()));
                    applied = true;
                }
                if (null != filter.getRelationshipId()) {
                    state.inEdges = true;
                    state.comingFrom = Relationships.Direction.outgoing;
                    // TODO test
                    String cp = CanonicalPath.of().relationship(filter.getRelationshipId()).get().toString();
                    //not too efficient, but this is rarely used...
                    if (applied) {
                        ret = ret.filter(r -> r.getString(CP).equals(cp));
                    } else {
                        ret = ret.flatMap(r -> Observable.just(r.getString(CP))).toList()
                                .flatMap(statements::findRelationshipOutsBySourceCps)
                                .filter(r -> r.getString(CP).equals(cp));
                    }
                }
                break;
            case ANY:
                // TODO properties-on-edges optimization not implemented for direction "both"
                if (null != filter.getRelationshipName()) {
                    state.inEdges = true;
                    state.comingFrom = Relationships.Direction.both;
                    ret = ret.flatMap(r -> Observable.just(r.getString(CP))).toList()
                            .flatMap(cps ->
                                    statements.findRelationshipOutsBySourceCpsAndName(cps, filter.getRelationshipName())
                                            .mergeWith(
                                                    statements.findRelationshipOutsBySourceCpsAndName(cps,
                                                            filter.getRelationshipName())));
                    applied = true;
                }
                if (null != filter.getRelationshipId()) {
                    state.inEdges = true;
                    state.comingFrom = Relationships.Direction.both;
                    // TODO test
                    String cp = CanonicalPath.of().relationship(filter.getRelationshipId()).get().toString();
                    if (applied) {
                        ret = ret.filter(r -> r.getString(CP).equals(cp));
                    } else {
                        ret = ret.flatMap(r -> Observable.just(r.getString(CP))).toList()
                                .flatMap(cps ->
                                        statements.findRelationshipOutsBySourceCpsAndName(cps,
                                                filter.getRelationshipName())
                                                .mergeWith(statements.findRelationshipOutsBySourceCpsAndName(cps,
                                                        filter.getRelationshipName())))
                                .filter(r -> r.getString(CP).equals(cp));
                    }
                }
        }

        if (otherEndEntityFilterProp != null) {
            ret = ret.filter(r -> r.getString(otherEndEntityFilterProp).equals(otherEndEntityFilterValue));
        }

        return ret;
    }

    private Observable<Row> execMarker(Marker filter, Observable<Row> bounds, TraversalState state) {
        state.markedPositions.put(filter.getLabel(), bounds.replay());
        return bounds;
    }

    private Observable<Row> execRelativePaths(With.RelativePaths filter, Observable<Row> bounds, TraversalState state) {
        bounds = goBackFromEdges(bounds, state);

        TraversalState currentState = state.clone();

        return bounds.toList().flatMap(rows -> {
            Observable<Row> ret = Observable.empty();

            Function<TraversalState, Function<Row, String>> getCp = (ts) -> {
                if (ts.comingFrom == null) {
                    return r -> r.getString(CP);
                } else if (ts.comingFrom == Relationships.Direction.incoming) {
                    return r -> r.getString(SOURCE_CP);
                } else if (ts.comingFrom == Relationships.Direction.outgoing) {
                    return r -> r.getString(TARGET_CP);
                } else {
                    throw new IllegalStateException("Cannot handle 'both' direction during relative path traversal.");
                }
            };

            for (RelativePath path : filter.getPaths()) {
                Observable<Row> pathRes = Observable.from(rows);

                for (Path.Segment seg : path.getPath()) {
                    Function<Row, String> cp = getCp.apply(currentState);
                    switch (seg.getElementType()) {
                        case up:
                            pathRes = pathRes.map(cp::apply).toList().flatMap(cps ->
                                    statements.findRelationshipInsByTargetCpsAndName(cps, contains.name()));
                            currentState.inEdges = true;
                            currentState.comingFrom = Relationships.Direction.incoming;
                            break;
                        default:
                            pathRes = pathRes.map(cp::apply).toList().flatMap(cps ->
                                    statements.findRelationshipOutsBySourceCpsAndName(cps, contains.name()));
                            currentState.inEdges = true;
                            currentState.comingFrom = Relationships.Direction.outgoing;
                    }
                }

                if (filter.getMarkerLabel() != null) {
                    Observable<Row> toMatch = currentState.markedPositions.get(filter.getMarkerLabel());
                    if (toMatch == null) {
                        throw new IllegalArgumentException(
                                "Query doesn't contain the marked position: " + filter.getMarkerLabel());
                    }

                    List<String> matchingCps = toMatch.map(r -> r.getString(CP)).toList().toBlocking().first();

                    Function<Row, String> cp = getCp.apply(currentState);

                    pathRes = pathRes.filter(r -> matchingCps.contains(cp.apply(r)));
                }

                ret = ret.mergeWith(goBackFromEdges(pathRes, currentState));
            }

            return ret;
        });
    }

    private <T> Observable<Row> doFilter(List<T> constraints, Observable<Row> bounds, TraversalState state,
                                         Function<T, Observable<Row>> primaryFetchBySingleConstraint,
                                         Function<List<T>, Observable<Row>> primaryFetchByMultipleConstraints,
                                         Function<Row, T> constraintMatchProducer,
                                         Func2<List<Row>, List<T>, Observable<Row>> resultModulator) {
        Observable<Row> ret;
        if (bounds == null) {
            if (state.inEdges) {
                throw new IllegalArgumentException("Cannot look for entities by constraints while" +
                        " traversing edges.");
            } else {
                if (constraints.size() == 1) {
                    ret = primaryFetchBySingleConstraint.apply(constraints.get(0));
                } else {
                    ret = primaryFetchByMultipleConstraints.apply(constraints);
                }

                if (resultModulator != null) {
                    ret = ret.toList().flatMap(rows -> resultModulator.call(rows, constraints));
                }
            }
        } else {
            ret = bounds.filter(r -> constraints.contains(constraintMatchProducer.apply(r)));
        }

        return goBackFromEdges(ret, state);
    }

    private Observable<Row> goBackFromEdges(Observable<Row> results, TraversalState state) {
        if (!state.inEdges || state.comingFrom == null) {
            return results;
        }

        Relationships.Direction comingFrom = state.comingFrom;

        state.inEdges = false;
        state.comingFrom = null;

        return results
                .flatMap(r -> {
                    switch (comingFrom) {
                        case incoming:
                            return Observable.just(r.getString(TARGET_CP));
                        case outgoing:
                            return Observable.just(r.getString(SOURCE_CP));
                        case both:
                            return Observable.just(r.getString(SOURCE_CP), r.getString(TARGET_CP));
                        default:
                            throw new IllegalStateException("Unsupported direction: " + comingFrom);
                    }
                })
                .toList()
                .flatMap(statements::findEntityByCanonicalPaths);
    }

    private static class TraversalState implements Cloneable {
        boolean inEdges;
        Relationships.Direction comingFrom;
        boolean explicitChange;
        final Map<String, Observable<Row>> markedPositions = new ConcurrentHashMap<>();

        @Override public TraversalState clone() {
            try {
                //shallow-copying the markedPositions is actually what we want
                return (TraversalState) super.clone();
            } catch (CloneNotSupportedException e) {
                //doesn't happen
                throw new AssertionError("Clone not supported on a cloneable class. What?", e);
            }
        }

        String propertyNameBasedOnState(String prop) {
            if (!inEdges) {
                return prop;
            }
            switch (prop) {
                case CP:
                    return chooseBasedOnDirection(CP, TARGET_CP, SOURCE_CP);
                case ID:
                    return chooseBasedOnDirection(ID, TARGET_ID, SOURCE_ID);
                case TYPE:
                    return chooseBasedOnDirection(TYPE, TARGET_TYPE, SOURCE_TYPE);
                default:
                    return prop;
            }
        }

        private String chooseBasedOnDirection(String defaultvalue, String inValue, String outValue) {
            if (comingFrom == null) {
                return defaultvalue;
            }

            switch (comingFrom) {
                case incoming:
                    return outValue;
                case outgoing:
                    return inValue;
                default:
                    throw new IllegalStateException("Properties-on-edges optimization cannot be applied when " +
                            "following both directions. This is probably a bug in the query translation.");
            }
        }
    }
}
