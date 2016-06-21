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
package org.hawkular.inventory.rest;

import static java.util.stream.Collectors.toList;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.RecurseFilter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.SwitchElementType;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.PathSegmentCodec;
import org.hawkular.inventory.paths.SegmentType;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.EntityTypeContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.FilterSpecContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.IdContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.NameContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.PathContinuationContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.PathEndContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.PathLikeContinuationContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.RecursiveContinuationContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.RecursiveFilterSpecContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.RelationshipAsFirstSegmentContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.RelationshipContinuationContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.RelationshipDirectionContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.RelationshipEndContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.RelationshipFilterSpecContext;
import org.hawkular.inventory.rest.HawkularInventoryGetUriParser.UriContext;

/**
 * @author Lukas Krejci
 * @since 0.16.0.Final
 */
public final class Traverser {
    private final int indexPrefixSize;
    private final Query.Builder queryPrefix;
    private final Function<String, CanonicalPath> cpParser;

    public Traverser(int indexPrefixSize, Query.Builder queryPrefix, Function<String, CanonicalPath> cpParser) {
        this.indexPrefixSize = indexPrefixSize;
        this.queryPrefix = queryPrefix;
        this.cpParser = cpParser;
    }

    public Query navigate(String traversal) {
        HawkularInventoryGetUriLexer lexer = new HawkularInventoryGetUriLexer(new ANTLRInputStream(traversal));
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        HawkularInventoryGetUriParser parser = new HawkularInventoryGetUriParser(tokens);
        parser.setErrorHandler(new BailErrorStrategy());

        Query.Builder copy = queryPrefix.build().asBuilder();

        ParseListener listener = new ParseListener(copy);

        try {
            UriContext ctx = parser.uri();

            ParseTreeWalker.DEFAULT.walk(listener, ctx);

            return listener.getParsedQuery();
        } catch (ParseCancellationException e) {
            Throwable error = e.getCause();
            if (error instanceof RecognitionException) {
                RecognitionException re = (RecognitionException) error;
                int errorIndex = indexPrefixSize + re.getOffendingToken().getTokenIndex();
                String errorToken = re.getOffendingToken().getText();
                String expectedAlternatives = re.getExpectedTokens() == null ? "none" : re.getExpectedTokens()
                        .toString(HawkularInventoryGetUriLexer.VOCABULARY);

                throw new IllegalArgumentException("Illegal inventory traversal URL. Token '" + errorToken
                        + "' on index " + errorIndex + " is not legal. Expected " + expectedAlternatives);
            } else {
                throw new IllegalArgumentException("Illegal inventory traversal URL. Error message: "
                        + e.getCause().getMessage(), e.getCause());
            }
        }
    }

    private class ParseListener extends HawkularInventoryGetUriBaseListener {
        private Query.Builder query;
        private Query.Builder recurseBuilder;

        private ParseListener(Query.Builder query) {
            this.query = query;
        }

        Query getParsedQuery() {
            if (recurseBuilder != null) {
                Filter[][] recurseCondition = Query.filters(recurseBuilder.build());
                query.filter().with(new RecurseFilter(recurseCondition));
            }
            return query.build();
        }

        @Override
        public void enterRelationshipAsFirstSegment(RelationshipAsFirstSegmentContext ctx) {
            //we override whatever implicit query we were given, because relationship ids are global
            query = Query.builder();

            IdContext id = ctx.id();
            RelationshipDirectionContext directionContext = ctx.relationshipDirection();
            List<RelationshipFilterSpecContext> filterCtxs = ctx.relationshipFilterSpec();
            RelationshipEndContext endCtx = ctx.relationshipEnd();

            processRelationship(true, RelationWith.id(id.getText()), directionContext, filterCtxs, endCtx);
        }

        @SuppressWarnings("Duplicates")
        @Override public void enterPathContinuation(PathContinuationContext ctx) {
            EntityTypeContext entityTypeCtx = ctx.entityType();
            IdContext idCtx = ctx.id();
            List<FilterSpecContext> filterSpecCtxs = ctx.filterSpec();
            PathLikeContinuationContext pathLikeCtx = ctx.pathLikeContinuation();

            boolean appendContains = pathLikeCtx != null && pathLikeCtx.pathContinuation() != null;
            processEntityOrFilter(entityTypeCtx, idCtx, filterSpecCtxs, appendContains);
        }

        @Override public void enterRecursiveContinuation(RecursiveContinuationContext ctx) {
            RecursiveFilterSpecContext overCtx = ctx.recursiveFilterSpec();

            String overRelationship = overCtx == null ? contains.name() : overCtx.value().getText();

            recurseBuilder = Query.builder().filter().with(Related.by(overRelationship));

            processEntityOrFilter(null, null, ctx.filterSpec(), false);

            Query q = recurseBuilder.build();
            recurseBuilder = null;

            getQueryBuilder().path().with(RecurseFilter.builder().addChain(Query.filters(q)[0]).build());
        }

        @Override
        public void enterRelationshipContinuation(RelationshipContinuationContext ctx) {
            NameContext nameCtx = ctx.name();
            RelationshipDirectionContext directionContext = ctx.relationshipDirection();
            List<RelationshipFilterSpecContext> filterCtxs = ctx.relationshipFilterSpec();
            RelationshipEndContext endCtx = ctx.relationshipEnd();

            processRelationship(false, RelationWith.name(nameCtx.getText()), directionContext, filterCtxs, endCtx);
        }

        @Override
        public void enterIdenticalContinuation(HawkularInventoryGetUriParser.IdenticalContinuationContext ctx) {
            new FilterApplicator.OnEntity().identical(query);
            processEntityOrFilter(null, null, Collections.emptyList(), false);
        }

        @Override public void enterPathEnd(PathEndContext ctx) {
            if ("relationships".equals(ctx.getChild(1).getText())) {
                RelationshipDirectionContext directionCtx = ctx.relationshipDirection();
                List<RelationshipFilterSpecContext> filterCtxs = ctx.relationshipFilterSpec();

                if (directionCtx != null && "in".equals(directionCtx.getText())) {
                    getQueryBuilder().path().with(SwitchElementType.incomingRelationships());
                } else {
                    getQueryBuilder().path().with(SwitchElementType.outgoingRelationships());
                }

                if (filterCtxs != null && !filterCtxs.isEmpty()) {
                    Map<String, List<ValueAndPos>> filters = extractFilters(filterCtxs,
                            RelationshipFilterSpecContext::relationshipFilterName,
                            RelationshipFilterSpecContext::value);

                    processFilters(filters, new FilterApplicator.OnRelationship());
                }
            } else if ("entities".equals(ctx.getChild(1).getText())) {
                List<FilterSpecContext> filterCtxs = ctx.filterSpec();
                processEntityOrFilter(null, null, filterCtxs, false);
            }
        }

        private void processEntityOrFilter(EntityTypeContext entityTypeCtx, IdContext idCtx,
                                           List<FilterSpecContext> filterSpecCtxs,
                                           boolean appendContains) {

            if (entityTypeCtx != null) {
                SegmentType segmentType = SegmentType.valueOf(entityTypeCtx.getText());
                //path is important because this can be a continuation of a canonical path...
                //this helps the query optimizer do its magic
                getQueryBuilder().path().with(type(segmentType), id(idCtx.getText()));
            }

            if (filterSpecCtxs != null && !filterSpecCtxs.isEmpty()) {
                Map<String, List<ValueAndPos>> filters = extractFilters(filterSpecCtxs, FilterSpecContext::filterName,
                        FilterSpecContext::value);

                processFilters(filters, new FilterApplicator.OnEntity());
            }

            if (appendContains) {
                getQueryBuilder().path().with(Related.by(contains));
            }
       }

        private void processRelationship(boolean isFirst, Filter idOrNameFilter,
                                         RelationshipDirectionContext directionContext,
                                         List<RelationshipFilterSpecContext> filterCtxs,
                                         RelationshipEndContext endCtx) {
            boolean outgoing;
            if (directionContext != null && "in".equals(directionContext.getText())) {
                if (!isFirst) {
                    getQueryBuilder().path().with(SwitchElementType.incomingRelationships());
                }
                outgoing = false;
            } else {
                if (!isFirst) {
                    getQueryBuilder().path().with(SwitchElementType.outgoingRelationships());
                }
                outgoing = true;
            }

            getQueryBuilder().path().with(idOrNameFilter);

            if (filterCtxs != null && !filterCtxs.isEmpty()) {
                Map<String, List<ValueAndPos>> filters = extractFilters(filterCtxs,
                        RelationshipFilterSpecContext::relationshipFilterName,
                        RelationshipFilterSpecContext::value);

                processFilters(filters, new FilterApplicator.OnRelationship());
            }

            if (endCtx == null || endCtx.getChild(1).getText().equals("entities")) {
                if (outgoing) {
                    getQueryBuilder().path().with(SwitchElementType.targetEntities());
                } else {
                    getQueryBuilder().path().with(SwitchElementType.sourceEntities());
                }
            }
        }

        /**
         * Assumes the query builder has been set up to either filter or path according to the current "situation"
         * in the query.
         *
         * @param filters the filters extracted from the URL at the current URL path segment
         * @param filterApplicator the applicator to use to apply the filters to the resulting inventory query
         */
        private void processFilters(Map<String, List<ValueAndPos>> filters,
                                    FilterApplicator filterApplicator) {
            //first handle propertyName/propertyValue pairs
            List<ValueAndPos> propertyNames = filters.get("propertyName");
            List<ValueAndPos> propertyValues = filters.get("propertyValue");
            if (propertyValues != null) {
                if (propertyNames == null ||  propertyNames.size() < propertyValues.size()) {
                    throw new IllegalArgumentException("Unmatched propertyValue filters. For each propertyValue " +
                            "filter there must be a matching propertyName filter.");
                }

                //user might use propertyName=a;propertyValue=b;propertyName=a;propertyValue=c which we understand as a
                // logical or.. to achieve that in our filters, we first need to "collapse" the values of a single
                // prop into a list
                Map<String, List<String>> nameAndValues = new LinkedHashMap<>(propertyNames.size());
                int len = propertyValues.size();

                for (int i = 0; i < len; ++i) {
                    String name = propertyNames.get(i).value;
                    String value = propertyValues.get(i).value;
                    List<String> values = nameAndValues.get(name);
                    if (values == null) {
                        values = new ArrayList<>(2);
                        nameAndValues.put(name, values);
                    }
                    values.add(value);
                }

                for (Map.Entry<String, List<String>> e : nameAndValues.entrySet()) {
                    filterApplicator.propertyValue(getQueryBuilder(), PathSegmentCodec.decode(e.getKey()),
                            e.getValue().stream().map(PathSegmentCodec::decode).toArray(String[]::new));
                }

                propertyNames.removeIf(p -> nameAndValues.keySet().contains(p.value));
            }

            if (propertyNames != null) {
                propertyNames.stream().map(p -> PathSegmentCodec.decode(p.value))
                        .forEach(p -> filterApplicator.propertyName(getQueryBuilder(), p));
            }

            filters.remove("propertyName");
            filters.remove("propertyValue");

            //now process the relatedBy+relatedTo pairs
            List<ValueAndPos> relatedBys = filters.getOrDefault("relatedBy", Collections.emptyList());
            List<ValueAndPos> relatedTos = filters.getOrDefault("relatedTo", Collections.emptyList());
            List<ValueAndPos> relatedWiths = filters.getOrDefault("relatedWith", Collections.emptyList());

            if (relatedBys.size() != relatedTos.size() + relatedWiths.size()) {
                throw new IllegalArgumentException("Each 'relatedBy' must correspond to 1 'relatedTo' or" +
                        " 'relatedWith'.");
            }

            for (int bys = 0, tos = 0, withs = 0; bys < relatedBys.size(); ++bys) {
                String relatedBy = relatedBys.get(bys).value;
                ValueAndPos relatedTo = tos < relatedTos.size() ? relatedTos.get(tos) : null;
                ValueAndPos relatedWith = withs < relatedWiths.size() ? relatedWiths.get(withs) : null;

                relatedBy = PathSegmentCodec.decode(relatedBy);

                if (relatedTo != null && (relatedWith == null || relatedTo.pos < relatedWith.pos)) {
                    String value = PathSegmentCodec.decode(relatedTo.value);
                    CanonicalPath target = cpParser.apply(value);
                    filterApplicator.relatedBy(getQueryBuilder(), target, relatedBy);
                    tos++;
                } else if (relatedWith != null) {
                    String value = PathSegmentCodec.decode(relatedWith.value);
                    CanonicalPath source = cpParser.apply(value);
                    filterApplicator.relatedWith(getQueryBuilder(), source, relatedBy);
                    withs++;
                }
            }

            for (Map.Entry<String, List<ValueAndPos>> e : filters.entrySet()) {
                String filterName = e.getKey();
                String[] filterValues = e.getValue().stream().map(p -> PathSegmentCodec.decode(p.value))
                        .toArray(String[]::new);

                switch (filterName) {
                    case "name":
                        filterApplicator.name(getQueryBuilder(), filterValues);
                        break;
                    case "sourceType":
                        Class<? extends Entity<?, ?>>[] types = getTypes(filterValues);
                        filterApplicator.sourceType(getQueryBuilder(), types);
                        break;
                    case "targetType":
                        types = getTypes(filterValues);
                        filterApplicator.targetType(getQueryBuilder(), types);
                        break;
                    case "id":
                        filterApplicator.id(getQueryBuilder(), filterValues);
                        break;
                    case "type":
                        types = getTypes(filterValues);
                        filterApplicator.type(getQueryBuilder(), types);
                        break;
                    case "cp":
                        CanonicalPath[] paths = Stream.of(filterValues).map(PathSegmentCodec::decode).map(cpParser)
                                .toArray(CanonicalPath[]::new);
                        filterApplicator.canonicalPath(getQueryBuilder(), paths);
                        break;
                    case "identical":
                        if (filterValues.length > 0) {
                            throw new IllegalArgumentException("The 'identical' filter doesn't accept any values.");
                        }
                        filterApplicator.identical(getQueryBuilder());
                        break;
                    case "definedBy":
                        CanonicalPath path = Stream.of(filterValues).map(PathSegmentCodec::decode).map(cpParser)
                                .findFirst().orElse(null);
                        filterApplicator.definedBy(getQueryBuilder(), path);
                        break;
                }
            }
        }

        private Query.Builder getQueryBuilder() {
            return recurseBuilder == null ? query : recurseBuilder;
        }
    }

    private <C extends RuleContext> Map<String, List<ValueAndPos>>
    extractFilters(List<C> filterContexts, Function<C, RuleContext> filterName, Function<C, RuleContext> filterValue) {

        Map<String, List<ValueAndPos>> ret = new LinkedHashMap<>();

        int pos = 0;
        for (C ctx : filterContexts) {
            RuleContext nameCtx = filterName.apply(ctx);
            RuleContext valueCtx = filterValue.apply(ctx);

            String name = nameCtx.getText();
            String value = valueCtx.getText();

            List<ValueAndPos> values = ret.get(name);
            if (values == null) {
                values = new ArrayList<>();
                ret.put(name, values);
            }

            values.add(new ValueAndPos(value, pos++));
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Entity<?, ?>>[] getTypes(String[] typeFilterValues) {
        List<Class<? extends Entity<?, ?>>> typeList = Stream.of(typeFilterValues).map(v -> {
            SegmentType t = Utils.getSegmentTypeFromSimpleName(v);
            return Entity.entityTypeFromSegmentType(t);
        }).collect(toList());

        return typeList.toArray(new Class[typeList.size()]);
    }

    private static final class ValueAndPos {
        final String value;
        final int pos;

        private ValueAndPos(String value, int pos) {
            this.value = value;
            this.pos = pos;
        }
    }

    private interface FilterApplicator {
        default void propertyName(Query.Builder query, String name) {}

        default void propertyValue(Query.Builder query, String propertyName, String[] propertyValues) {}

        default void name(Query.Builder query, String[] names) {}

        default void id(Query.Builder query, String[] id) {}

        default void type(Query.Builder query, Class<? extends Entity<?, ?>>[] types) {}

        default void sourceType(Query.Builder query, Class<? extends Entity<?, ?>>[] types) {}

        default void targetType(Query.Builder query, Class<? extends Entity<?, ?>>[] types) {}

        default void canonicalPath(Query.Builder query, CanonicalPath[] cps) {}

        default void identical(Query.Builder query) {}

        default void definedBy(Query.Builder query, CanonicalPath target) {}

        default void relatedBy(Query.Builder query, CanonicalPath target, String relationship) {}

        default void relatedWith(Query.Builder query, CanonicalPath source, String relationship) {}

        class OnEntity implements FilterApplicator {
            @Override public void canonicalPath(Query.Builder query, CanonicalPath[] cps) {
                query.path().with(With.paths(cps));
            }

            @Override public void id(Query.Builder query, String[] ids) {
                query.path().with(With.ids(ids));
            }

            @Override public void identical(Query.Builder query) {
                query.path().with(With.sameIdentityHash());
            }

            @Override public void name(Query.Builder query, String[] names) {
                query.filter().with(With.names(names));
            }

            @Override public void propertyName(Query.Builder query, String name) {
                query.filter().with(With.property(name));
            }

            @Override public void propertyValue(Query.Builder query, String propertyName, String[] propertyValues) {
                query.filter().with(With.propertyValues(propertyName, (Object[]) propertyValues));
            }

            @Override public void type(Query.Builder query, Class<? extends Entity<?, ?>>[] types) {
                //path, because this can be part of the canonical path progression
                query.path().with(With.types(types));
            }

            @Override public void definedBy(Query.Builder query, CanonicalPath target) {
                query.filter().with(Defined.by(target));
            }

            @Override public void relatedBy(Query.Builder query, CanonicalPath target, String relationship) {
                query.filter().with(Related.with(target, relationship));
            }

            @Override public void relatedWith(Query.Builder query, CanonicalPath source, String relationship) {
                query.filter().with(Related.asTargetWith(source, relationship));
            }
        }

        class OnRelationship implements FilterApplicator {
            @Override public void id(Query.Builder query, String[] ids) {
                query.path().with(RelationWith.ids(ids));
            }

            @Override public void name(Query.Builder query, String[] names) {
                query.path().with(RelationWith.names(names));
            }

            @Override public void propertyName(Query.Builder query, String name) {
                query.path().with(RelationWith.property(name));
            }

            @Override public void propertyValue(Query.Builder query, String propertyName, String[] propertyValues) {
                query.path().with(RelationWith.propertyValues(propertyName, (Object[]) propertyValues));
            }

            @Override public void sourceType(Query.Builder query, Class<? extends Entity<?, ?>>[] types) {
                query.path().with(RelationWith.sourcesOfTypes(types));
            }

            @Override public void targetType(Query.Builder query, Class<? extends Entity<?, ?>>[] types) {
                query.path().with(RelationWith.targetsOfTypes(types));
            }
        }
    }
}
