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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class QueryExecutor {

    private final RxSession session;
    private final Statements statements;

    QueryExecutor(RxSession session, Statements statements) {
        this.session = session;
        this.statements = statements;
    }

    public Observable<Row> execute(Query query) {
        Observable<Row> data;
        if (query.getFragments().length == 0) {
            return Observable.empty();
        } else {
            data = start(query.getFragments()[0].getFilter());
        }

        for (int i = 1; i < query.getFragments().length; ++i) {
            //TODO this is wrong - most probably we will be able to join multiple following filters into a single
            //statement
            QueryFragment qf = query.getFragments()[i];
            if (qf instanceof PathFragment) {
                data = progress(qf.getFilter(), data);
            } else {
                data = filter(qf.getFilter(), data);
            }
        }

        return processSubTrees(data, query);
    }

    public Observable<Row> traverse(Row startingPoint, Query query) {
        Observable<Row> res = Observable.just(startingPoint);
        return traverse(res, query);
    }

    private Observable<Row> processSubTrees(Observable<Row> data, Query query) {
        if (!query.getSubTrees().isEmpty()) {
            return data.flatMap(r -> {
                Observable<Row> res = Observable.empty();
                for (Query q : query.getSubTrees()) {
                    res = res.mergeWith(traverse(r, q));
                }

                return res;
            });
        } else {
            return data;
        }
    }

    private Observable<Row> traverse(Observable<Row> data, Query query) {
        for (QueryFragment qf : query.getFragments()) {
            //TODO this is wrong - most probably we will be able to join multiple following filters into a single
            //statement
            if (qf instanceof PathFragment) {
                data = progress(qf.getFilter(), data);
            } else {
                data = filter(qf.getFilter(), data);
            }
        }

        return processSubTrees(data, query);
    }

    private Observable<Row> start(Filter initialFilter) {
        return toStatement(initialFilter, null).flatMap(session::executeAndFetch);
    }

    private Observable<Row> progress(Filter progressFilter, Observable<Row> intermediateResults) {
        return intermediateResults
                .flatMap(r -> toStatement(progressFilter, r))
                .flatMap(session::executeAndFetch);
    }

    private Observable<Row> filter(Filter filter, Observable<Row> intermediateResults) {
        return intermediateResults.flatMap(r -> {
            String cp = r.getString(Statements.CP);
            Observable<Row> targets = progress(filter, Observable.just(r));
            //TODO hmm... couldn't this be done using a query?
            return targets.filter(t -> t.getString(Statements.CP).equals(cp));
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
                ret = statements.getFindEntityByCanonicalPaths().map(st -> st.bind(entityCps));
            }

            if (!relCps.isEmpty()) {
                Observable<BoundStatement> st =
                        statements.getFindRelationshipByCanonicalPaths().map(s -> s.bind(relCps));
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
            Observable<PreparedStatement> st = ourCp.getSegment().getElementType() == SegmentType.rl
                    ? statements.getFindRelationshipByCanonicalPaths()
                    : statements.getFindEntityByCanonicalPaths();

            return st.map(s -> {
                List<String> cps = Stream.of(filter.getPaths()).map(CanonicalPath::toString).collect(toList());
                return s.bind(cps);
            });
        }
    }
}
