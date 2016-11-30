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
 *
 */

package org.hawkular.inventory.impl.cassandra;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.Stream;

import org.hawkular.inventory.api.PathFragment;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.QueryFragment;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.hawkular.rx.cassandra.driver.RxSession;
import org.jboss.logging.Logger;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;

import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class QueryExecutor {
    private static final Logger DBG = Logger.getLogger(QueryExecutor.class);

    private final RxSession session;
    private final Statements statements;
    private final Scheduler scheduler = Schedulers.io();

    QueryExecutor(RxSession session, Statements statements) {
        this.session = session;
        this.statements = statements;
    }

    public Observable<Row> execute(Query query) {
        DBG.debugf("Execute of %s", query);

        Observable<Row> data;
        if (query.getFragments().length == 0) {
            DBG.debugf("Empty query, returning empty observable");

            return Observable.empty();
        } else {
            DBG.debugf("Starting the query %s with the first filter %s", query, query.getFragments()[0].getFilter());

            data = start(query.getFragments()[0].getFilter());
        }

        return traverse(data, query, 1);
    }

    public Observable<Row> traverse(Row startingPoint, Query query) {
        DBG.debugf("Executing traverse %s from %s", query, startingPoint);

        Observable<Row> res = Observable.just(startingPoint);
        return traverse(res, query, 0);
    }

    private Observable<Row> processSubTrees(Observable<Row> data, Query query) {
        if (!query.getSubTrees().isEmpty()) {
            return data.flatMap(r -> {
                DBG.debugf("IN FLIGHT: Appending subtrees to partial query results of query %s", query);

                Observable<Row> res = Observable.empty();
                for (Query q : query.getSubTrees()) {
                    DBG.debugf("IN FLIGHT: Merging subquery %s to partial query results of query %s", q, query);

                    res = res.mergeWith(traverse(r, q));
                }

                return res;
            });
        } else {
            DBG.debugf("No subqueries found in %s. Returing the observable as is.", query);
            return data;
        }
    }

    private Observable<Row> traverse(Observable<Row> data, Query query, int startWithFragmentIndex) {
        QueryFragment[] qfs = query.getFragments();

        DBG.debugf("Traversing query fragments starting at %d of query %s", startWithFragmentIndex, query);

        for (int i = startWithFragmentIndex; i < qfs.length; ++i) {
            //TODO this is wrong - most probably we will be able to join multiple following filters into a single
            //statement
            QueryFragment qf = qfs[i];
            if (qf instanceof PathFragment) {
                data = progress(qf.getFilter(), data);
            } else {
                data = filter(qf.getFilter(), data);
            }
        }

        DBG.debugf("Applying subqueries to %s", query);

        return processSubTrees(data, query);
    }

    private Observable<Row> start(Filter initialFilter) {
        return toStatement(initialFilter, null).flatMap(st -> {
            DBG.debugf("IN FLIGHT: start: executing filter %s as a new statement %s", initialFilter, st);
            return session.executeAndFetch(st, scheduler);
        });
    }

    private Observable<Row> progress(Filter progressFilter, Observable<Row> intermediateResults) {
        return intermediateResults
                .flatMap(r -> {
                    DBG.debugf("IN FLIGHT: progress: converting filter %s to a new statement with row %s", progressFilter, r);
                   return toStatement(progressFilter, r);
                })
                .flatMap(st -> {
                    DBG.debugf("IN FLIGHT: progress: Executing statement %s to get new intermediate results for progress using filter %s.", st, progressFilter);
                    return session.executeAndFetch(st, scheduler);
                });
    }

    private Observable<Row> filter(Filter filter, Observable<Row> intermediateResults) {
        return intermediateResults.flatMap(r -> {
            DBG.debugf("IN FLIGHT: filter: applying filter %s on row %s by facilitating progress()", filter, r);
            Observable<Row> targets = progress(filter, Observable.just(r));
            //TODO hmm... couldn't this be done using a query?
            return targets.take(1).map(any -> r);
        });
    }

    private Observable<? extends Statement> toStatement(Filter filter, Row bound) {
        if (filter instanceof With.CanonicalPaths) {
            return toStatement((With.CanonicalPaths) filter, bound);
        } else {
            throw new IllegalArgumentException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private Observable<? extends Statement> toStatement(With.CanonicalPaths filter, Row bound) {
        DBG.debugf("Creating statement for CP filter %s on row %s", filter, bound);
        if (bound == null) {
            //just fetch an entities with given canonical paths
            List<String> entityCps = Stream.of(filter.getPaths())
                    .filter(cp -> cp.getSegment().getElementType() != SegmentType.rl)
                    .map(CanonicalPath::toString)
                    .collect(toList());

            List<String> relCps = Stream.of(filter.getPaths())
                    .filter(cp -> cp.getSegment().getElementType() == SegmentType.rl)
                    .map(CanonicalPath::toString)
                    .collect(toList());

            Observable<BoundStatement> ret = null;

            if (!entityCps.isEmpty()) {
                ret = Observable.just(statements.getFindEntityByCanonicalPaths().bind(entityCps));
            }

            if (!relCps.isEmpty()) {
                Observable<BoundStatement> st =
                        Observable.just(statements.getFindRelationshipByCanonicalPaths().bind(relCps));
                if (ret == null) {
                    ret = st;
                } else {
                    ret = ret.mergeWith(st);
                }
            }
            return ret;
        } else {
            //make sure the entity has one of the provided canonical paths
            //inefficient, but hey - we're not exactly efficient anywhere around here...
            String ourCpStr = bound.getString(Statements.CP);
            CanonicalPath ourCp = CanonicalPath.fromString(ourCpStr);
            PreparedStatement st = ourCp.getSegment().getElementType() == SegmentType.rl
                    ? statements.getFindRelationshipByCanonicalPaths()
                    : statements.getFindEntityByCanonicalPaths();

            List<String> cps = Stream.of(filter.getPaths()).map(CanonicalPath::toString).collect(toList());
            return Observable.just(st.bind(cps));
        }
    }
}
