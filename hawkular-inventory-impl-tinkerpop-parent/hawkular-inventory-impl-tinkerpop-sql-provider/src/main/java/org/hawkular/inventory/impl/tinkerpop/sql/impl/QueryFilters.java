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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.tinkerpop.blueprints.Compare;
import com.tinkerpop.blueprints.Contains;
import com.tinkerpop.blueprints.Predicate;
import com.tinkerpop.blueprints.Query;

/**
 * @author Lukas Krejci
 * @since 0.13.0
 */
final class QueryFilters {

    private final Map<String, List<OperatorAndValue>> filters = new HashMap<>();

    public Map<String, List<OperatorAndValue>> getFilters() {
        return filters;
    }

    public void has(String key) {
        addFilter(filters, key, new OperatorAndValue(CustomPredicates.EXISTS, null));
    }

    public void hasNot(String key) {
        addFilter(filters, key, new OperatorAndValue(CustomPredicates.DOES_NOT_EXIST, null));
    }

    public void has(String key, Object value) {
        has(key, Compare.EQUAL, value);
    }

    public void hasNot(String key, Object value) {
        has(key, Compare.NOT_EQUAL, value);
    }

    public void has(String key, Predicate predicate, Object value) {
        if (predicate == null) {
            throw new NullPointerException("null predicate");
        }

        if (isPredicateSupported(predicate)) {
            if (value == null) {
                predicate = adaptToNull(predicate);
            }
            addFilter(filters, key, new OperatorAndValue(predicate, value));
        } else {
            throw new IllegalArgumentException("predicate not supported: " + predicate);
        }
    }

    public <T extends Comparable<T>> void has(String key, T value, Query.Compare compare) {
        has(key, compare, value);
    }

    public <T extends Comparable<?>> void interval(String key, T startValue, T endValue) {
        if (startValue instanceof Number) {
            addFilter(filters, key,
                new OperatorAndValue(CustomPredicates.INTERVAL, new Interval(startValue, endValue)));
        } else {
            throw new IllegalArgumentException("intervals supported only on numeric values");
        }
    }

    private Predicate adaptToNull(Predicate pred) {
        if (pred == Query.Compare.NOT_EQUAL || pred == Compare.NOT_EQUAL) {
            return CustomPredicates.EXISTS;
        } else if (pred == Query.Compare.EQUAL || pred == Compare.EQUAL) {
            return CustomPredicates.DOES_NOT_EXIST;
        } else {
            return pred;
        }
    }

    SqlAndParams generateStatement(String select, String mainTable, String propsTable, String uniquePropsTable,
                                   String propsTableFK, List<String> specialProps, String mainTableWhereClause)
            throws SQLException {

        StringBuilder bld = new StringBuilder(select);

        bld.append(" FROM ").append(mainTable);

        List<Object> params = new ArrayList<>();

        boolean whereClausePresent = false;

        if (mainTableWhereClause != null) {
            bld.append(" WHERE ").append(mainTableWhereClause);
            if (!filters.isEmpty()) {
                bld.append(" AND ");
            }
            whereClausePresent = true;
        }

        if (!filters.isEmpty()) {
            if (!whereClausePresent) {
                bld.append(" WHERE ");
            }

            applyFilters(mainTable, propsTable, uniquePropsTable, propsTableFK, specialProps, bld, params);
        }

        return new SqlAndParams(bld, params);
    }

    private void applyFilters(String mainTable, String propsTable, String uPropsTable, String propsTableFK,
                              List<String> specialProps, StringBuilder bld, List<Object> params) {
        Iterator<Map.Entry<String, List<OperatorAndValue>>> it = filters.entrySet().iterator();

        if (it.hasNext()) {
            Map.Entry<String, List<OperatorAndValue>> e = it.next();
            appendFilters(mainTable, propsTable, uPropsTable, propsTableFK, bld, params, e.getKey(), e.getValue(),
                    specialProps);
        }

        while (it.hasNext()) {
            bld.append(" AND ");
            Map.Entry<String, List<OperatorAndValue>> e = it.next();
            appendFilters(mainTable, propsTable, uPropsTable, propsTableFK, bld, params, e.getKey(), e.getValue(),
                    specialProps);
        }
    }

    private void addFilter(Map<String, List<OperatorAndValue>> filters, String property, OperatorAndValue opValue) {
        List<OperatorAndValue> propFilters = filters.get(property);
        if (propFilters == null) {
            propFilters = new ArrayList<>();
            filters.put(property, propFilters);
        }

        propFilters.add(opValue);
    }

    private boolean isPredicateSupported(Predicate p) {
        return p instanceof CustomPredicates || p instanceof Query.Compare || p instanceof Compare
            || p instanceof Contains;
    }

    private void appendFilters(String mainTable, String propsTable, String uPropsTable, String propsTableFK,
                               StringBuilder bld, List<Object> params, String name,
                               List<QueryFilters.OperatorAndValue> opValues, List<String> namesOnMainTable) {

        Iterator<QueryFilters.OperatorAndValue> it = opValues.iterator();

        boolean isOnMainTable = namesOnMainTable.contains(name);

        if (it.hasNext()) {
            appendFilter(mainTable, propsTable, uPropsTable, propsTableFK, bld, params, name, it.next(), isOnMainTable);
        }

        while (it.hasNext()) {
            bld.append(" AND ");
            appendFilter(mainTable, propsTable, uPropsTable, propsTableFK, bld, params, name, it.next(),
                    isOnMainTable);
        }
    }

    private void appendFilter(String mainTable, String propsTable, String uPropsTable, String propsTableFK,
                              StringBuilder bld, List<Object> params, String name,
                              QueryFilters.OperatorAndValue opValue, boolean isOnMainTable) {
        Predicate operator = opValue.operator;
        Object value = opValue.object;
        ValueType valueType = ValueType.of(value, false);

        bld.append("(");

        if (operator instanceof QueryFilters.CustomPredicates) {
            switch ((QueryFilters.CustomPredicates) operator) {
            case EXISTS:
                // if the param is on the main table, it always exists so we don't need to test for that
                if (isOnMainTable) {
                    bld.append("1 = 1"); //this is just to produce valid SQL
                } else {
                    bld.append("(");
                    propertyMatchPrologue(true, bld, mainTable, propsTable, propsTableFK)
                        .append(propsTable).append(".name = ?)) OR (");
                    propertyMatchPrologue(true, bld, mainTable, uPropsTable, propsTableFK)
                            .append(uPropsTable).append(".name = ?))");
                    params.add(name);
                    params.add(name);
                }
                break;
            case DOES_NOT_EXIST:
                if (isOnMainTable) {
                    //the property is on the main table (i.e. it's id or label (in case of an edge) and is always
                    //present
                    bld.append("1 = 0");
                } else {
                    bld.append("(");
                    propertyMatchPrologue(false, bld, mainTable, propsTable, propsTableFK)
                        .append(propsTable).append(".name = ?)) AND (");
                    propertyMatchPrologue(false, bld, mainTable, uPropsTable, propsTableFK)
                            .append(uPropsTable).append(".name = ?))");
                    params.add(name);
                    params.add(name);
                }
                break;
            case INTERVAL:
                if (isOnMainTable) {
                    mainPropertyComparison(">=", bld, mainTable, name);
                    bld.append(" AND ");
                    mainPropertyComparison("<", bld, mainTable, name);
                    params.add(((QueryFilters.Interval) value).from);
                    params.add(((QueryFilters.Interval) value).to);
                } else {
                    bld.append("(");
                    propertyMatchPrologue(true, bld, mainTable, propsTable, propsTableFK)
                        .append(propsTable).append(".name = ? AND ")
                        .append(propsTable).append(".numeric_value >= ? AND ")
                        .append(propsTable).append(".numeric_value < ?)) OR (");
                    propertyMatchPrologue(true, bld, mainTable, uPropsTable, propsTableFK)
                            .append(uPropsTable).append(".name = ? AND ")
                            .append(uPropsTable).append(".numeric_value >= ? AND ")
                            .append(uPropsTable).append(".numeric_value < ?))");
                    params.add(name);
                    params.add(((QueryFilters.Interval) value).from);
                    params.add(((QueryFilters.Interval) value).to);
                    params.add(name);
                    params.add(((QueryFilters.Interval) value).from);
                    params.add(((QueryFilters.Interval) value).to);
                }
                break;
            }
        } else if (operator instanceof Query.Compare || operator instanceof Compare) {
            if (operator instanceof Query.Compare) {
                operator = toCompare((Query.Compare) operator);
            }

            String op = null;
            switch ((Compare) operator) {
            case EQUAL:
                op = "=";
                break;
            case GREATER_THAN:
                op = ">";
                break;
            case GREATER_THAN_EQUAL:
                op = ">=";
                break;
            case LESS_THAN:
                op = "<";
                break;
            case LESS_THAN_EQUAL:
                op = "<=";
                break;
            case NOT_EQUAL:
                op = "<>";
                break;
            }

            if (isOnMainTable) {
                mainPropertyComparison(op, bld, mainTable, name);
                params.add(value);
            } else {
                params.add(name);
                params.add(value);
                params.add(name);
                params.add(value);

                bld.append("(");
                propertyComparison(op, valueType, bld, mainTable, propsTable, propsTableFK);
                bld.append(") OR (");
                propertyComparison(op, valueType, bld, mainTable, uPropsTable, propsTableFK);
                bld.append(")");
            }
        } else if (operator instanceof Contains) {
            Iterable<?> col = (Iterable<?>) value;

            StringBuilder collection = null;

            List<Object> paramsToAdd = new ArrayList<>();
            Iterator<?> it = col.iterator();
            if (it.hasNext()) {
                collection = new StringBuilder("(");
                collection.append("?");
                if (!isOnMainTable) {
                    paramsToAdd.add(name);
                }
                paramsToAdd.add(it.next());
            }

            if (collection != null) {
                while (it.hasNext()) {
                    collection.append(", ?");
                    paramsToAdd.add(it.next());
                }

                collection.append(")");

                if (!isOnMainTable) {
                    collection.append(")");
                }

                //trailing space important so that we don't have to special-case the replace below
                String op = operator == Contains.IN ? "IN " : "NOT IN ";
                if (isOnMainTable) {
                    mainPropertyComparison(op, bld, mainTable, name);
                    bld.replace(bld.length() - 2, bld.length(), collection.toString());
                    params.addAll(paramsToAdd);
                } else {
                    bld.append("(");
                    propertyComparison(op, valueType, bld, mainTable, propsTable, propsTableFK);
                    bld.replace(bld.length() - 2, bld.length(), collection.toString());
                    bld.append(") OR (");
                    propertyComparison(op, valueType, bld, mainTable, uPropsTable, propsTableFK);
                    bld.replace(bld.length() - 2, bld.length(), collection.toString());
                    bld.append(")");
                    params.addAll(paramsToAdd);
                    params.addAll(paramsToAdd);
                }
            }
        }

        bld.append(")");
    }

    private StringBuilder propertyMatchPrologue(boolean match, StringBuilder bld, String mainTable, String propsTable,
                                                String propsTableFK) {
        bld.append("1 ").append(match ? "IN" : "NOT IN").append(" (SELECT 1 FROM ").append(propsTable).append(" WHERE ")
            .append(propsTable).append(".").append(propsTableFK).append(" = ")
            .append(mainTable).append(".id AND ");
        return bld;
    }

    private void propertyComparison(String operator, ValueType valueType, StringBuilder bld, String mainTable,
                                    String propsTable, String propsTableFK) {
        propertyMatchPrologue(true, bld, mainTable, propsTable, propsTableFK)
            .append(propsTable).append(".name = ? AND ")
            .append(propsTable).append(valueType.isNumeric() ? ".numeric_value" : ".string_value")
            .append(" ").append(operator).append(" ?)");
    }

    private void mainPropertyComparison(String operator, StringBuilder bld, String mainTable, String name) {
        bld.append(mainTable).append(".").append(name).append(" ").append(operator).append(" ?");
    }

    private Compare toCompare(Query.Compare c) {
        switch (c) {
            case EQUAL: return Compare.EQUAL;
            case GREATER_THAN: return Compare.GREATER_THAN;
            case GREATER_THAN_EQUAL: return Compare.GREATER_THAN_EQUAL;
            case LESS_THAN: return Compare.LESS_THAN;
            case LESS_THAN_EQUAL: return Compare.LESS_THAN_EQUAL;
            case NOT_EQUAL: return Compare.NOT_EQUAL;
            default:
                throw new IllegalStateException("incomplete mapping of Compare and Query.Compare");
        }
    }

    enum CustomPredicates implements Predicate {
        EXISTS,
        DOES_NOT_EXIST,
        INTERVAL {
            @Override
            @SuppressWarnings("unchecked")
            public boolean evaluate(Object first, Object second) {
                Interval interval = (Interval) second;
                return interval.from.compareTo(first) <= 0 && interval.to.compareTo(first) > 0;
            }
        };

        @Override
        public boolean evaluate(Object first, Object second) {
            return false;
        }
    }

    static class Interval {
        final Comparable from;
        final Comparable to;

        private Interval(Comparable from, Comparable to) {
            this.from = from;
            this.to = to;
        }
    }

    static class OperatorAndValue {
        final Predicate operator;
        final Object object;

        OperatorAndValue(Predicate operator, Object object) {
            this.operator = operator;
            this.object = object;
        }
    }

    static class SqlAndParams {
        final StringBuilder sql;
        final List<Object> params;

        SqlAndParams(StringBuilder sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}
