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

import org.hawkular.rx.cassandra.driver.ResultSetToRowsTransformer;
import org.hawkular.rx.cassandra.driver.RxSession;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

import rx.Observable;
import rx.Scheduler;

/**
 * For debugging purposes.
 *
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class SynchronousRxSessionImpl implements RxSession {
    private final Session session;

    public SynchronousRxSessionImpl(Session session) {
        this.session = session;
    }

    @Override
    public String getLoggedKeyspace() {
        return session.getLoggedKeyspace();
    }

    @Override
    public RxSession init() {
        session.init();
        return this;
    }

    @Override
    public Observable<ResultSet> execute(String query) {
        ResultSet rs = session.execute(query);
        return Observable.just(rs);
    }

    @Override
    public Observable<Row> executeAndFetch(String query) {
        return execute(query).compose(new ResultSetToRowsTransformer());
    }

    @Override
    public Observable<ResultSet> execute(String query, Scheduler scheduler) {
        ResultSet rs = session.execute(query);
        return Observable.just(rs).observeOn(scheduler);
    }

    @Override
    public Observable<Row> executeAndFetch(String query, Scheduler scheduler) {
        return execute(query, scheduler).compose(new ResultSetToRowsTransformer(scheduler));
    }

    @Override
    public Observable<ResultSet> execute(String query, Object... values) {
        ResultSet rs = session.execute(query, values);
        return Observable.just(rs);
    }

    @Override
    public Observable<Row> executeAndFetch(String query, Object... values) {
        return execute(query, values).compose(new ResultSetToRowsTransformer());
    }

    @Override
    public Observable<ResultSet> execute(String query, Scheduler scheduler, Object... values) {
        ResultSet rs = session.execute(query, values, scheduler);
        return Observable.just(rs);
    }

    @Override
    public Observable<Row> executeAndFetch(String query, Scheduler scheduler, Object... values) {
        return execute(query, scheduler, values).compose(new ResultSetToRowsTransformer(scheduler));
    }

    @Override
    public Observable<ResultSet> execute(Statement statement) {
        ResultSet rs = session.execute(statement);
        return Observable.just(rs);
    }

    @Override
    public Observable<Row> executeAndFetch(Statement statement) {
        return execute(statement).compose(new ResultSetToRowsTransformer());
    }

    @Override
    public Observable<ResultSet> execute(Statement statement, Scheduler scheduler) {
        ResultSet rs = session.execute(statement);
        return Observable.just(rs);
    }

    @Override
    public Observable<Row> executeAndFetch(Statement statement, Scheduler scheduler) {
        return execute(statement, scheduler).compose(new ResultSetToRowsTransformer(scheduler));
    }

    @Override
    public Observable<PreparedStatement> prepare(String query) {
        PreparedStatement ps = session.prepare(query);
        return Observable.just(ps);
    }

    @Override
    public Observable<PreparedStatement> prepare(String query, Scheduler scheduler) {
        PreparedStatement rs = session.prepare(query);
        return Observable.just(rs);
    }

    @Override
    public Observable<PreparedStatement> prepare(RegularStatement statement) {
        PreparedStatement rs = session.prepare(statement);
        return Observable.just(rs);
    }

    @Override
    public Observable<PreparedStatement> prepare(RegularStatement statement, Scheduler scheduler) {
        PreparedStatement rs = session.prepare(statement);
        return Observable.just(rs);
    }

    @Override
    public void close() {
        session.close();
    }

    @Override
    public boolean isClosed() {
        return session.isClosed();
    }

    @Override
    public Cluster getCluster() {
        return session.getCluster();
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public Session.State getState() {
        return session.getState();
    }
}
