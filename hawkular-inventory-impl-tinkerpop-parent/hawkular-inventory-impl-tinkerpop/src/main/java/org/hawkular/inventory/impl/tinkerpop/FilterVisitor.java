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

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.impl.tinkerpop.HawkularTraversal.hwk__;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.InternalEdge.__inState;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.InternalEdge.__withIdentityHash;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__cp;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__eid;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__sourceCp;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__sourceEid;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__sourceType;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__targetCp;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__targetEid;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__targetType;
import static org.hawkular.inventory.impl.tinkerpop.spi.Constants.Property.__type;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.T;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Marker;
import org.hawkular.inventory.api.filters.RecurseFilter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.SwitchElementType;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.base.spi.Discriminator;
import org.hawkular.inventory.base.spi.NoopFilter;
import org.hawkular.inventory.impl.tinkerpop.spi.Constants;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @author Jirka Kremser
 * @since 0.0.1
 */
class FilterVisitor {

    private static final AtomicLong CNT = new AtomicLong();

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, Related related,
                      QueryTranslationState state) {
        if (state.isInEdges()) {
            //jump to vertices
            switch (state.getComingFrom()) {
                case OUT:
                    query.inV();
                    break;
                case IN:
                    query.outV();
                    break;
                case BOTH:
                    query.bothV();
            }
        }

        boolean applied = false;
        switch (related.getEntityRole()) {
            case TARGET:
                if (null != related.getRelationshipName()) {
                    state.setInEdges(true);
                    state.setComingFrom(Direction.IN);
                    query.inE(related.getRelationshipName()).restrictTo(discriminator);
                    applied = true;
                }
                if (null != related.getRelationshipId()) {
                    // TODO test
                    if (applied) {
                        query.has(__eid.name(), related.getRelationshipId());
                    } else {
                        query.inE().has(__eid.name(), related.getRelationshipId());
                    }
                    query.restrictTo(discriminator);
                }
                break;
            case SOURCE:
                if (null != related.getRelationshipName()) {
                    state.setInEdges(true);
                    state.setComingFrom(Direction.OUT);
                    query.outE(related.getRelationshipName()).restrictTo(discriminator);
                    applied = true;
                }
                if (null != related.getRelationshipId()) {
                    // TODO test
                    if (applied) {
                        query.has(__eid.name(), related.getRelationshipId());
                    } else {
                        query.outE().has(__eid.name(), related.getRelationshipId());
                    }
                    query.restrictTo(discriminator);
                }
                break;
            case ANY:
                // TODO properties-on-edges optimization not implemented for direction "both"
                if (null != related.getRelationshipName()) {
                    query.bothE(related.getRelationshipName()).restrictTo(discriminator).bothV();
                }
                if (null != related.getRelationshipId()) {
                    // TODO test
                    query.bothE().restrictTo(discriminator).has(__eid.name(), related.getRelationshipId()).bothV();
                }
        }

        if (related.getEntityPath() != null) {
            String prop = chooseBasedOnDirection(Constants.Property.__cp, Constants.Property.__targetCp, Constants
                    .Property.__sourceCp, TinkerpopBackend.asDirection(related.getEntityRole())).name();
            query.has(prop, related.getEntityPath().toString());
        }
    }

    @SuppressWarnings("unchecked")
    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, With.Ids ids,
                      QueryTranslationState state) {
        String prop = propertyNameBasedOnState(__eid, state);

        if (ids.getIds().length == 1) {
            query.has(prop, ids.getIds()[0]);
            return;
        }

        query.has(prop, P.within(ids.getIds()));

        goBackFromEdges(query, state);
        query.existsAt(discriminator);
    }

    @SuppressWarnings("unchecked")
    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, With.Types types,
                      QueryTranslationState state) {
            String prop = propertyNameBasedOnState(__type, state);

        if (types.getTypes().length == 1) {
            Constants.Type type = Constants.Type.of(types.getTypes()[0]);
            query.has(prop, type.name());
            goBackFromEdges(query, state);
            query.existsAt(discriminator);
            return;
        }

        String[] typeNames = Stream.of(types.getTypes()).map(t -> Constants.Type.of(t).name())
                .toArray(String[]::new);
        query.has(prop, P.within(typeNames));

        goBackFromEdges(query, state);
        query.existsAt(discriminator);
    }

    @SuppressWarnings("unchecked")
    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, With.Names names,
                      QueryTranslationState state) {
        goBackFromEdges(query, state);
        query.existsAt(discriminator);

        String prop = Constants.Property.name.name();

        HawkularTraversal<?, ?> nameCheck = hwk__().outE(__inState.name()).restrictTo(discriminator).inV();

        if (names.getNames().length == 1) {
            nameCheck.has(prop, names.getNames()[0]);
        } else {
            nameCheck.has(prop, P.within(names.getNames()));
        }

        query.where(nameCheck);
    }

    @SuppressWarnings("unchecked")
    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, RelationWith.Ids ids,
                      QueryTranslationState state) {
        query.restrictTo(discriminator);

        if (ids.getIds().length == 1) {
            query.has(__eid.name(), ids.getIds()[0]);
        } else {
            query.has(__eid.name(), P.within(ids.getIds()));
        }
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query,
                      RelationWith.PropertyValues properties, QueryTranslationState state) {
        query.restrictTo(discriminator);
        applyPropertyFilter(discriminator, query, state, properties.getProperty(), properties.getValues());
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, RelationWith.SourceOfType types,
                      QueryTranslationState state) {
        visit(discriminator, query, types, true, state);
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, RelationWith.TargetOfType types,
                      QueryTranslationState state) {
        visit(discriminator, query, types, false, state);
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query,
                      RelationWith.SourceOrTargetOfType types,
                      QueryTranslationState state) {
        visit(discriminator, query, types, null, state);
    }

    @SuppressWarnings("unchecked")
    private void visit(Discriminator discriminator, HawkularTraversal<?, ?> query,
                       RelationWith.SourceOrTargetOfType types, Boolean source, QueryTranslationState state) {

        HawkularTraversal<?, ?> origQuery = query;
        origQuery.restrictTo(discriminator);

        String prop;
        if (source == null) {
            query = hwk__().bothV();
            prop = __type.name();
        } else if (source) {
            prop = __sourceType.name();
        } else {
            prop = __targetType.name();
        }

        // look ahead if the type of the incidence vertex is of the desired type(s)
        if (types.getTypes().length == 1) {
            Constants.Type type = Constants.Type.of(types.getTypes()[0]);
            query.has(prop, type.name());
        } else {
            String[] typeNames = Stream.of(types.getTypes()).map(t -> Constants.Type.of(t).name())
                    .toArray(String[]::new);
            query.has(prop, P.within(typeNames));
        }

        if (source == null) {
            origQuery.where(query);
        }
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, SwitchElementType filter, QueryTranslationState state) {
        final boolean jumpFromEdge = filter.isFromEdge();
        switch (filter.getDirection()) {
            case incoming:
                if (jumpFromEdge) {
                    state.setInEdges(false);
                    state.setComingFrom(null);
                    query.outV();
                } else {
                    state.setInEdges(true);
                    state.setComingFrom(Direction.IN);
                    query.inE().restrictTo(discriminator);
                }
                break;
            case outgoing:
                if (jumpFromEdge) {
                    state.setInEdges(false);
                    state.setComingFrom(null);
                    query.inV();
                } else {
                    state.setInEdges(true);
                    state.setComingFrom(Direction.OUT);
                    query.outE().restrictTo(discriminator);
                }
                break;
            case both:
                if (jumpFromEdge) {
                    state.setInEdges(false);
                    state.setComingFrom(null);
                    query.bothV();
                } else {
                    state.setInEdges(true);
                    state.setComingFrom(Direction.BOTH);
                    query.bothE().restrictTo(discriminator);
                }
                break;
        }
        state.setExplicitChange(true);
    }

    public void visit(HawkularTraversal<?, ?> query, NoopFilter filter, QueryTranslationState state) {
        //nothing to do
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, With.PropertyValues filter,
                      QueryTranslationState state) {
        //if the property is mirrored, we check for its value directly at the edge and only move to the target
        //vertex afterwards. If it is not mirrored, we first move to the target vertex and then filter by the property
        boolean propertyMirrored = Constants.Property.isMirroredInEdges(filter.getName());
        if (!propertyMirrored) {
            goBackFromEdges(query, state);
            query.existsAt(discriminator);
        }

        applyPropertyFilter(discriminator, query, state, filter.getName(), filter.getValues());

        if (propertyMirrored) {
            goBackFromEdges(query, state);
            query.existsAt(discriminator);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPropertyFilter(Discriminator discriminator, HawkularTraversal<?, ?> query,
                                     QueryTranslationState state, String propertyName, Object... values) {
        String mappedName = Constants.Property.mapUserDefined(propertyName);

        boolean checkStateVertex = !state.isInEdges() && !Constants.Type.getIdentityVertexProperties().contains(mappedName);

        HawkularTraversal<?, ?> check = query;

        if (checkStateVertex) {
            check = hwk__().outE(__inState.name()).restrictTo(discriminator).inV();
        }

        boolean checkLabel = state.isInEdges() && "label".equals(mappedName);

        if (values.length == 0) {
            if (!checkLabel) {
                check.has(mappedName);
            }
        } else if (values.length == 1) {
            if (checkLabel) {
                check.hasLabel(values[0]);
            } else {
                check.has(mappedName, values[0]);
            }
        } else {
            if (checkLabel) {
                check.hasLabel(P.within(values));
            } else {
                check.has(mappedName, P.within(values));
            }
        }

        if (checkStateVertex) {
            query.where(check);
        }
    }

    @SuppressWarnings("unchecked")
    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, With.CanonicalPaths filter,
                      QueryTranslationState state) {
        String prop = chooseBasedOnDirection(__cp, __targetCp, __sourceCp, state.getComingFrom()).name();

        if (filter.getPaths().length == 1) {
            //this only works if we are on vertices, so check for that
            if (prop.equals(__cp.name())) {
                query.has(T.label, Constants.Type.of(filter.getPaths()[0].getSegment().getElementType())
                        .identityVertexLabel());
            }

            query.has(prop, filter.getPaths()[0].toString());
        } else {
            if (prop.equals(__cp.name())) {
                String[] labels = Stream.of(filter.getPaths())
                        .map(p -> Constants.Type.of(p.getSegment().getElementType()).identityVertexLabel())
                        .toArray(String[]::new);

                query.has(T.label, P.within(labels));
            }

            String[] paths = Stream.of(filter.getPaths()).map(Object::toString).toArray(String[]::new);

            query.has(prop, P.within(paths));
        }

        goBackFromEdges(query, state);
        query.existsAt(discriminator);
    }

    @SuppressWarnings("unchecked")
    public <E> void visit(Discriminator discriminator, HawkularTraversal<?, E> query, With.RelativePaths filter,
                          QueryTranslationState state) {
        goBackFromEdges(query, state);
        query.existsAt(discriminator);

        String originLabel = filter.getMarkerLabel();

        if (filter.getPaths().length == 1) {
            if (originLabel != null) {
                //progress our main query down to the candidates from which we will select the results
                apply(filter.getPaths()[0].getSegment(), query);
                query.existsAt(discriminator);
                String candidateLabel = nextRandomLabel();
                query.as(candidateLabel);


                //create the traversal to a relative path going from the provided marked origin
                HawkularTraversal<?, ?> relativePath = hwk__().as(originLabel);
                convertToPipeline(discriminator, filter.getPaths()[0], relativePath);
                //using the same label makes sure the candidate in query matches the one navigated to by the relative
                //path
                relativePath.as(candidateLabel);

                //extend the query with our match and select the matching candidates
                query.match(relativePath).select(candidateLabel);
            } else {
                convertToPipeline(discriminator, filter.getPaths()[0], query);
            }
        } else {
            if (originLabel != null) {
                String candidateLabel = nextRandomLabel();

                Traversal[] candidates = new GraphTraversal<?, ?>[filter.getPaths().length];
                Arrays.setAll(candidates, i -> {
                    GraphTraversal<?, ?> n = __.start();
                    apply(filter.getPaths()[i].getSegment(), n);
                    return n;
                });

                HawkularTraversal<?, ?>[] relativePaths = new HawkularTraversal<?, ?>[filter.getPaths().length];
                Arrays.setAll(relativePaths, i -> {
                    HawkularTraversal<?, ?> rp = hwk__().as(originLabel);
                    convertToPipeline(discriminator, filter.getPaths()[i], rp);
                    rp.as(candidateLabel);
                    return rp;
                });

                query.union(candidates).as(candidateLabel).match(relativePaths).select(candidateLabel);
            } else {
                HawkularTraversal[] relativePaths = new HawkularTraversal[filter.getPaths().length];
                Arrays.setAll(relativePaths, i -> {
                    HawkularTraversal<?, ?> rp = hwk__();
                    convertToPipeline(discriminator, filter.getPaths()[i], rp);
                    return rp;
                });

                query.union(relativePaths);
            }
        }
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, Marker filter,
                      QueryTranslationState state) {
        goBackFromEdges(query, state);
        query.existsAt(discriminator);
        query.as(filter.getLabel());
    }

    @SuppressWarnings("unchecked")
    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, With.DataAt dataPos,
                      QueryTranslationState state) {
        goBackFromEdges(query, state);
        query.existsAt(discriminator);
        query.outE(hasData.name()).restrictTo(discriminator).inV();

        for (Path.Segment seg : dataPos.getDataPath().getPath()) {
            if (SegmentType.up.equals(seg.getElementType())) {
                query.in(contains.name());
            } else {
                query.out(contains.name());
            }

            query.has(__type.name(), Constants.Type.structuredData.name());

            // map members have both index and key (so that the order of the elements is preserved)
            // list members have only the index

            Integer index = toInteger(seg.getElementId());

            if (index == null) {
                query.has(Constants.Property.__structuredDataKey.name(), seg.getElementId());
            } else {
                //well, the map could have a numeric key, so we cannot say it has to be a list index here.
                GraphTraversal<?, ?>[] indexOrKey = new GraphTraversal<?, ?>[2];
                indexOrKey[0] = __.has(Constants.Property.__structuredDataIndex.name(), index)
                        .hasNot(Constants.Property.__structuredDataKey.name());
                indexOrKey[1] = __.has(Constants.Property.__structuredDataKey.name(),
                        seg.getElementId());

                query.or((Traversal[]) indexOrKey);
            }
        }
    }

    public void visit(HawkularTraversal<?, ?> query, With.DataValued dataValue, QueryTranslationState state) {
        goBackFromEdges(query, state);
        Object val = dataValue.getValue();

        query.has(Constants.Property.__type.name(), Constants.Type.structuredData.name());

        if (val == null) {
            query.has(Constants.Property.__structuredDataType.name(), StructuredData.Type.undefined.name());
        } else {
            if (Long.class == val.getClass()) {
                query.has(Constants.Property.__structuredDataValue_i.name(), val);
            } else if (Boolean.class == val.getClass()) {
                query.has(Constants.Property.__structuredDataValue_b.name(), val);
            } else if (Double.class == val.getClass()) {
                query.has(Constants.Property.__structuredDataValue_f.name(), val);
            } else {
                //fallback everything else to string
                query.has(Constants.Property.__structuredDataValue_s.name(), val.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void visit(HawkularTraversal<?, ?> query, With.DataOfTypes dataTypes, QueryTranslationState state) {
        goBackFromEdges(query, state);
        if (dataTypes.getTypes().length == 1) {
            query.has(Constants.Property.__structuredDataType.name(), dataTypes.getTypes()[0].name());
        } else {
            String[] types = Stream.of(dataTypes.getTypes()).map(StructuredData.Type::name).toArray(String[]::new);
            query.has(Constants.Property.__structuredDataType.name(), P.within(types));
        }
    }

    @SuppressWarnings("unchecked")
    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query, RecurseFilter recurseFilter, QueryTranslationState state) {
        goBackFromEdges(query, state);
        query.existsAt(discriminator);

        HawkularTraversal<?, ?> descend = hwk__();

        if (recurseFilter.getLoopChains().length == 1) {
            QueryTranslationState descendState = state.clone();

            for (Filter f : recurseFilter.getLoopChains()[0]) {
                FilterApplicator<?> applicator = FilterApplicator.of(f);
                applicator.applyTo(discriminator, descend, descendState);
            }

            goBackFromEdges(descend, descendState);
        } else {
            HawkularTraversal[] pipes = new HawkularTraversal[recurseFilter.getLoopChains().length];
            for (int i = 0; i < recurseFilter.getLoopChains().length; ++i) {
                pipes[i] = hwk__();
                QueryTranslationState innerState = state.clone();
                for (Filter f : recurseFilter.getLoopChains()[i]) {
                    FilterApplicator<?> applicator = FilterApplicator.of(f);
                    applicator.applyTo(discriminator, pipes[i], innerState);
                }
                FilterApplicator.finishPipeline(pipes[i], innerState, state);
            }
            descend.union(pipes).dedup();
        }

        query.repeat((Traversal) descend).emit();
    }

    public void visit(Discriminator discriminator, HawkularTraversal<?, ?> query,
                      @SuppressWarnings("UnusedParameters") With.SameIdentityHash filter,
                      QueryTranslationState state) {
        goBackFromEdges(query, state);
        query.out(__withIdentityHash.name()).in(__withIdentityHash.name());
        query.existsAt(discriminator);
    }

    private void convertToPipeline(Discriminator discriminator, RelativePath path, HawkularTraversal<?, ?> pipeline) {
        for (Path.Segment s : path.getPath()) {
            if (SegmentType.up.equals(s.getElementType())) {
                pipeline.inE(contains.name()).restrictTo(discriminator).outV();
                pipeline.existsAt(discriminator);
            } else {
                Constants.Type targetType = Constants.Type.of(s.getElementType());
                pipeline.outE(contains.name()).restrictTo(discriminator)
                        .has(__targetType.name(), targetType.name())
                        .has(__targetEid.name(), s.getElementId()).inV()
                        .hasLabel(targetType.identityVertexLabel());
                pipeline.existsAt(discriminator);
            }
        }
    }

    private void apply(Path.Segment segment, GraphTraversal<?, ?> pipeline) {
        pipeline.has(__type.name(), Constants.Type.of(Entity.typeFromSegmentType(segment.getElementType())).name());
        pipeline.has(__eid.name(), segment.getElementId());
    }

    /**
     * A very simplistic conversion of string to positive integer in only decimal radix.
     *
     * <p>This is used to figure out whether a segment id represents an index or a key.
     *
     * @param str the string potentially representing a number
     * @return the parsed number or null if the string is not a supported number
     */
    private static Integer toInteger(String str) {
        char[] chars = str.toCharArray();

        int result = 0;

        int multiplier = 1;
        for (int i = chars.length - 1; i >= 0; --i, multiplier *= 10) {
            char c = chars[i];
            if ('0' <= c && c <= '9') {
                result += (c - '0') * multiplier;
            } else {
                return null;
            }
        }

        return result;
    }

    private static String propertyNameBasedOnState(Constants.Property prop, QueryTranslationState state) {
        if (!state.isInEdges()) {
            return prop.name();
        }
        switch (prop) {
            case __cp:
                return chooseBasedOnDirection(__cp, __targetCp, __sourceCp, state.getComingFrom()).name();
            case __eid:
                return chooseBasedOnDirection(__eid, __targetEid, __sourceEid, state.getComingFrom()).name();
            case __type:
                return chooseBasedOnDirection(T.label.getAccessor(), __targetType.name(), __sourceType.name(),
                        state.getComingFrom());
            default:
                return prop.name();
        }
    }

    private static <T> T chooseBasedOnDirection(T defaultvalue, T inValue, T outValue, Direction direction) {
        if (direction == null) {
            return defaultvalue;
        }

        switch (direction) {
            case IN:
                return outValue;
            case OUT:
                return inValue;
            default:
                throw new IllegalStateException("Properties-on-edges optimization cannot be applied when " +
                        "following both directions. This is probably a bug in the query translation.");
        }
    }

    private static void goBackFromEdges(GraphTraversal<?, ?> query, QueryTranslationState state) {
        if (state.isInEdges()) {
            switch (state.getComingFrom()) {
                case IN:
                    query.outV();
                    break;
                case OUT:
                    query.inV();
            }
            state.setInEdges(false);
            state.setComingFrom(null);
        }
    }

    private static String nextRandomLabel() {
        return "label-" + CNT.getAndIncrement();
    }
}
