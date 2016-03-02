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
package org.hawkular.inventory.impl.tinkerpop.sql.impl;

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.tinkerpop.blueprints.CloseableIterable;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
class StatementIterable<T> implements CloseableIterable<T> {
    private final ElementGenerator<? extends T> generator;
    private final SqlGraph graph;
    private final PreparedStatement st;
    private final long artificialLimit;

    private StatementIterable() {
        generator = null;
        graph = null;
        st = null;
        artificialLimit = -1;
    }

    StatementIterable(ElementGenerator<? extends T> generator, SqlGraph graph, PreparedStatement st) {
        this(generator, graph, st, -1);
    }

    StatementIterable(ElementGenerator<? extends T> generator, SqlGraph graph, PreparedStatement st,
                      long artificialLimit) {
        this.generator = generator;
        this.graph = graph;
        this.st = st;
        this.artificialLimit = artificialLimit;
    }

    public static <T> StatementIterable<T> empty() {
        return new StatementIterable<T>() {
            @Override
            public void close() {
            }

            @Override
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    @Override
                    public boolean hasNext() {
                        return false;
                    }

                    @Override
                    public T next() {
                        throw new NoSuchElementException();
                    }

                    @Override
                    public void remove() {
                        throw new IllegalStateException();
                    }
                };
            }
        };
    }

    @Override
    public Iterator<T> iterator() {
        try {
            return new ResultSetIterator(st.executeQuery());
        } catch (SQLException e) {
            throw new SqlGraphException(e);
        }
    }

    @Override
    public void close() {
        //we cache the statements, so no closing
//        try {
//            st.close();
//        } catch (SQLException e) {
//            throw new SqlGraphException(e);
//        }
    }

    protected void finalize () throws Throwable {
        close();
        super.finalize();
    }

    class ResultSetIterator implements Iterator<T>, Closeable {
        final ResultSet rs;
        T next;
        long cnt;

        ResultSetIterator(ResultSet rs) {
            Log.LOG.debugf("New resultset iterator for %s", rs);
            this.rs = rs;
        }

        @Override
        public boolean hasNext() {
            if (isPastLimit()) {
                return false;
            }

            advance();
            return next != null;
        }

        @Override
        public T next() {
            if (isPastLimit()) {
                throw new NoSuchElementException();
            }

            try {
                advance();
                return next;
            } finally {
                next = null;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance() {
            if (next == null) {
                try {
                    if (rs.isClosed()) {
                        return;
                    }

                    if (rs.next()) {
                        next = generator.generate(graph, rs);
                        cnt++;
                    } else {
                        close();
                    }
                } catch (SQLException e) {
                    throw new SqlGraphException(e);
                }
            }
        }

        private boolean isPastLimit() {
            if (artificialLimit >= 0 && cnt >= artificialLimit) {
                close();
                return true;
            }

            return false;
        }

        @Override
        public void close() {
            try {
                rs.close();
                Log.LOG.debugf("Closing result set %s", rs);
            } catch (SQLException e) {
                throw new SqlGraphException("Could not close the result set.", e);
            }
        }
    }
}
