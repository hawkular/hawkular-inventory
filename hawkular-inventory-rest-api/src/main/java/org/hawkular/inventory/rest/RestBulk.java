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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.MetadataPacks;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.RelationAlreadyExistsException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResolvableToSingleWithRelationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.TransactionFrame;
import org.hawkular.inventory.api.WriteInterface;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementBlueprintVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.ElementTypeVisitor;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
@javax.ws.rs.Path("/bulk")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Api(value = "/bulk", description = "Endpoint for bulk operations on inventory entities", tags = "Bulk Create")
public class RestBulk extends RestBase {

    private static CanonicalPath canonicalize(String path, CanonicalPath rootPath) {
        Path p;
        if (path == null || path.isEmpty()) {
            p = rootPath;
        } else {
            p = Path.fromPartiallyUntypedString(path, rootPath, rootPath, SegmentType.ANY_ENTITY);
        }
        if (p.isRelative()) {
            p = p.toRelativePath().applyTo(rootPath);
        }
        return p.toCanonicalPath();
    }

    private static void putStatus(Map<ElementType, Map<CanonicalPath, Integer>> statuses, ElementType et,
                                  CanonicalPath cp, Integer status) {

        Map<CanonicalPath, Integer> typeStatuses = statuses.get(et);
        if (typeStatuses == null) {
            typeStatuses = new HashMap<>();
            statuses.put(et, typeStatuses);
        }
        if (!typeStatuses.containsKey(cp)) {
            typeStatuses.put(cp, status);
        }

        if (status >= 200 && status < 300) {
            RestApiLogger.LOGGER.debugf("REST BULK created: %s", cp);
        } else {
            RestApiLogger.LOGGER.debugf("REST BULK failed (%d): %s", status, cp);
        }
    }

    private static boolean hasBeenProcessed(Map<ElementType, Map<CanonicalPath, Integer>> statuses, ElementType et,
                                            CanonicalPath cp) {
        Map<CanonicalPath, Integer> typeStatuses = statuses.get(et);
        return (typeStatuses != null && typeStatuses.containsKey(cp));
    }

    private static String arrow(Relationship.Blueprint b) {
        switch (b.getDirection()) {
            case both:
                return "<-(" + b.getName() + ")->";
            case outgoing:
                return "-(" + b.getName() + ")->";
            case incoming:
                return "<-(" + b.getName() + ")-";
            default:
                throw new IllegalStateException("Unhandled direction type: " + b.getDirection());
        }
    }

    private static WriteInterface<?, ?, ?, ?> step(SegmentType elementClass, Class<?> nextType,
                                                   ResolvableToSingle<?, ?> single) {

        return ElementTypeVisitor.accept(elementClass,
                new ElementTypeVisitor.Simple<WriteInterface<?, ?, ?, ?>, Void>() {

                    @Override
                    protected WriteInterface<?, ?, ?, ?> defaultAction(SegmentType elementType, Void parameter) {
                        throw new IllegalArgumentException("Entity of type '" + nextType.getSimpleName() + "' cannot " +
                                "be created under an entity of type '" + elementClass.getSimpleName() + "'.");
                    }

                    @Override
                    public WriteInterface<?, ?, ?, ?> visitEnvironment(Void parameter) {
                        return ElementTypeVisitor.accept(AbstractElement.segmentTypeFromType(nextType),
                                new RejectingVisitor() {
                            @Override
                            public WriteInterface<?, ?, ?, ?> visitMetric(Void parameter) {
                                return ((Environments.Single) single).metrics();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitResource(Void parameter) {
                                return ((Environments.Single) single).resources();
                            }
                        }, null);
                    }

                    @Override
                    public WriteInterface<?, ?, ?, ?> visitFeed(Void parameter) {
                        return ElementTypeVisitor.accept(AbstractElement.segmentTypeFromType(nextType),
                                new RejectingVisitor() {
                            @Override
                            public WriteInterface<?, ?, ?, ?> visitMetric(Void parameter) {
                                return ((Feeds.Single) single).metrics();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitMetricType(Void parameter) {
                                return ((Feeds.Single) single).metricTypes();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitResource(Void parameter) {
                                return ((Feeds.Single) single).resources();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitResourceType(Void parameter) {
                                return ((Feeds.Single) single).resourceTypes();
                            }
                        }, null);
                    }

                    @Override
                    public WriteInterface<?, ?, ?, ?> visitOperationType(Void parameter) {
                        return ElementTypeVisitor.accept(AbstractElement.segmentTypeFromType(nextType),
                                new RejectingVisitor() {
                            @Override
                            public WriteInterface<?, ?, ?, ?> visitData(Void parameter) {
                                return ((OperationTypes.Single) single).data();
                            }
                        }, null);
                    }

                    @Override
                    public WriteInterface<?, ?, ?, ?> visitResource(Void parameter) {
                        return ElementTypeVisitor.accept(AbstractElement.segmentTypeFromType(nextType),
                                new RejectingVisitor() {
                            @Override
                            public WriteInterface<?, ?, ?, ?> visitData(Void parameter) {
                                return ((Resources.Single) single).data();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitResource(Void parameter) {
                                return ((Resources.Single) single).resources();
                            }
                        }, null);
                    }

                    @Override
                    public WriteInterface<?, ?, ?, ?> visitResourceType(Void parameter) {
                        return ElementTypeVisitor.accept(AbstractElement.segmentTypeFromType(nextType),
                                new RejectingVisitor() {
                            @Override
                            public WriteInterface<?, ?, ?, ?> visitData(Void parameter) {
                                return ((ResourceTypes.Single) single).data();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitOperationType(Void parameter) {
                                return ((ResourceTypes.Single) single).operationTypes();
                            }
                        }, null);
                    }

                    @Override
                    public WriteInterface<?, ?, ?, ?> visitTenant(Void parameter) {
                        return ElementTypeVisitor.accept(AbstractElement.segmentTypeFromType(nextType),
                                new RejectingVisitor() {
                            @Override public WriteInterface<?, ?, ?, ?> visitFeed(Void parameter) {
                                return ((Tenants.Single) single).feeds();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitEnvironment(Void parameter) {
                                return ((Tenants.Single) single).environments();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitMetricType(Void parameter) {
                                return ((Tenants.Single) single).metricTypes();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitResourceType(Void parameter) {
                                return ((Tenants.Single) single).resourceTypes();
                            }

                            @Override
                            public WriteInterface<?, ?, ?, ?> visitMetadataPack(Void parameter) {
                                return ((Tenants.Single) single).metadataPacks();
                            }
                        }, null);
                    }

                    class RejectingVisitor extends ElementTypeVisitor.Simple<WriteInterface<?, ?, ?, ?>, Void> {
                        @Override
                        protected WriteInterface<?, ?, ?, ?> defaultAction(SegmentType elementType, Void parameter) {
                            throw new IllegalArgumentException(
                                    "Entity of type '" + nextType.getSimpleName() + "' cannot " +
                                            "be created under an entity of type '" + elementClass.getSimpleName() +
                                            "'.");
                        }
                    }
                }, null);
    }

    private static <E extends AbstractElement<?, ?>> ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
    create (Blueprint b, WriteInterface<?, ?, ?, ?> wrt) {
        return b.accept(
                new ElementBlueprintVisitor.Simple<ResolvableToSingle<? extends AbstractElement<?, ?>, ?>, Void>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitData(DataEntity.Blueprint<?> data, Void parameter) {
                        return ((Data.ReadWrite) wrt).create(data);
                    }

                    @Override
                    public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitEnvironment(Environment.Blueprint environment, Void parameter) {
                        return ((Environments.ReadWrite) wrt).create(environment);
                    }

                    @Override
                    public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitFeed(Feed.Blueprint feed, Void parameter) {
                        return ((Feeds.ReadWrite) wrt).create(feed);
                    }

                    @Override public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitMetric(Metric.Blueprint metric, Void parameter) {
                        return ((Metrics.ReadWrite) wrt).create(metric);
                    }

                    @Override public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitMetricType(MetricType.Blueprint metricType, Void parameter) {
                        return ((MetricTypes.ReadWrite) wrt).create(metricType);
                    }

                    @Override
                    public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitOperationType(OperationType.Blueprint operationType, Void parameter) {
                        return ((OperationTypes.ReadWrite) wrt).create(operationType);
                    }

                    @Override
                    public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitResource(Resource.Blueprint resource, Void parameter) {
                        return ((Resources.ReadWrite) wrt).create(resource);
                    }

                    @Override
                    public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitResourceType(ResourceType.Blueprint type, Void parameter) {
                        return ((ResourceTypes.ReadWrite) wrt).create(type);
                    }

                    @Override
                    public ResolvableToSingle<? extends AbstractElement<?, ?>, ?>
                    visitMetadataPack(MetadataPack.Blueprint metadataPack, Void parameter) {
                        return ((MetadataPacks.ReadWrite) wrt).create(metadataPack);
                    }
                }, null);
    }

    @POST
    @javax.ws.rs.Path("/")
    @ApiOperation(value = "Bulk creation of new entities.",
            notes="The response body contains details about results of creation" +
            " of individual entities. The return value is a map where keys are types of entities created and values" +
            " are again maps where keys are the canonical paths of the entities to be created and values are HTTP" +
            " status codes - 201 OK, 400 if invalid path is supplied, 409 if the entity already exists on given path" +
            " or 500 in case of internal error.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Entities successfully created"),
    })
    public Response addEntities(@ApiParam("This is a map where keys are paths to the parents under which entities " +
            "should be created. The values are again maps where keys are one of [environment, resourceType, " +
            "metricType, operationType, feed, resource, metric, dataEntity, relationship] and values are arrays of " +
            "blueprints of entities of the corresponding types.") Map<String, Map<ElementType, List<Object>>> entities,
                                @Context UriInfo uriInfo) {

        CanonicalPath rootPath = CanonicalPath.of().tenant(getTenantId()).get();

        Map<ElementType, Map<CanonicalPath, Integer>> statuses = bulkCreate(entities, rootPath);

        return Response.status(CREATED).entity(statuses).build();
    }

    private Map<ElementType, Map<CanonicalPath, Integer>> bulkCreate(Map<String, Map<ElementType,
            List<Object>>> entities, CanonicalPath rootPath) {

        Map<ElementType, Map<CanonicalPath, Integer>> statuses = new HashMap<>();

        TransactionFrame transaction = inventory.newTransactionFrame();
        Inventory binv = transaction.boundInventory();

        IdExtractor idExtractor = new IdExtractor();

        try {
            for (Map.Entry<String, Map<ElementType, List<Object>>> e : entities.entrySet()) {
                Map<ElementType, List<Object>> allBlueprints = e.getValue();

                CanonicalPath parentPath = canonicalize(e.getKey(), rootPath);

                RestApiLogger.LOGGER.tracef("Bulk creating under %s", parentPath);

                @SuppressWarnings("unchecked")
                ResolvableToSingle<? extends AbstractElement<?, ?>, ?> single = binv.inspect(parentPath,
                        ResolvableToSingle.class);

                for (Map.Entry<ElementType, List<Object>> ee : allBlueprints.entrySet()) {
                    ElementType elementType = ee.getKey();
                    List<Object> rawBlueprints = ee.getValue();

                    List<Blueprint> blueprints = deserializeBlueprints(elementType, rawBlueprints);

                    if (elementType == ElementType.relationship) {
                        bulkCreateRelationships(statuses, parentPath,
                                (ResolvableToSingleWithRelationships<?, ?>) single, elementType, blueprints);
                    } else {
                        bulkCreateEntity(statuses, idExtractor, parentPath, single, elementType, blueprints);
                    }
                }

                RestApiLogger.LOGGER.tracef("Done bulk creating under %s", parentPath);
            }
            transaction.commit();
            return statuses;
        } catch (Throwable t) {
            //note that the security resources are not yet created (they only get created after a successful commit)
            //so no "leftovers" are left behind in case of transaction rollback.
            transaction.rollback();
            throw t;
        }
    }

    private List<Blueprint> deserializeBlueprints(ElementType elementType, List<Object> rawBlueprints) {
        return rawBlueprints.stream().map((o) -> {
            try {
                String js = getMapper().writeValueAsString(o);
                return (Blueprint) getMapper().reader(elementType.blueprintType).readValue(js);
            } catch (IOException e1) {
                throw new IllegalArgumentException("Failed to deserialize as " + elementType
                        .blueprintType + " the following data: " + o, e1);
            }
        }).collect(Collectors.toList());
    }

    private void bulkCreateEntity(Map<ElementType, Map<CanonicalPath, Integer>> statuses,
                                  IdExtractor idExtractor, CanonicalPath parentPath,
                                  ResolvableToSingle<? extends AbstractElement<?, ?>, ?> single,
                                  ElementType elementType, List<Blueprint> blueprints) {
        if (!parentPath.modified().canExtendTo(elementType.segmentType)) {
            RestApiLogger.LOGGER.debugf("Element type %s cannot be created under parent %s. Aborting bulk create.",
                    elementType.segmentType, parentPath);
            putStatus(statuses, elementType, parentPath, BAD_REQUEST.getStatusCode());
            return;
        }

        if (!canCreateUnderParent(elementType, parentPath, statuses)) {
            for (Blueprint b : blueprints) {
                String id = b.accept(idExtractor, null);
                putStatus(statuses, elementType, parentPath.extend(elementType.segmentType, id).get(),
                        FORBIDDEN.getStatusCode());
            }
            return;
        }

        for (Blueprint b : blueprints) {
            WriteInterface<?, ?, ?, ?> wrt =
                    step(parentPath.getSegment().getElementType(), elementType
                            .elementType, single);

            CanonicalPath provisionalChildPath = parentPath.extend(elementType.segmentType, b.accept(idExtractor, null))
                    .get();
            boolean hasBeenProcessed = hasBeenProcessed(statuses, elementType, provisionalChildPath);
            if (hasBeenProcessed) {
                RestApiLogger.LOGGER.tracef("Skipping creation of %s. It seems to have been processed already",
                        provisionalChildPath);
                // this entity has it's own record in the list with statuses so let's move to another one
                continue;
            }
            try {
                //this is cheap - the call to entity() right after create() doesn't fetch from the backend
                String childId = create(b, wrt).entity().getId();

                CanonicalPath childPath = parentPath.extend(elementType.segmentType, childId).get();

                RestApiLogger.LOGGER.tracef("Created %s", childPath);

                putStatus(statuses, elementType, childPath, CREATED.getStatusCode());
            } catch (EntityAlreadyExistsException ex) {
                RestApiLogger.LOGGER.tracef("Entity already exists during bulk create: " + provisionalChildPath);
                putStatus(statuses, elementType, provisionalChildPath, CONFLICT.getStatusCode());
            } catch (Exception ex) {
                RestApiLogger.LOGGER.failedToCreateBulkEntity(provisionalChildPath, ex);
                putStatus(statuses, elementType, provisionalChildPath, INTERNAL_SERVER_ERROR.getStatusCode());
            }
        }
    }

    private void bulkCreateRelationships(Map<ElementType, Map<CanonicalPath, Integer>> statuses,
                                         CanonicalPath parentPath, ResolvableToSingleWithRelationships<?, ?> single,
                                         ElementType elementType, List<Blueprint> blueprints) {
        if (!hasBeenCreatedInBulk(parentPath, statuses)) {
            if (!security.canAssociateFrom(parentPath)) {
                for (Blueprint b : blueprints) {
                    Relationship.Blueprint rb = (Relationship.Blueprint) b;
                    String id = parentPath.toString() + arrow(rb) + rb.getOtherEnd();
                    putStatus(statuses, elementType, parentPath.extend(elementType.segmentType, id).get(),
                            FORBIDDEN.getStatusCode());
                }
                return;
            }
        }

        for (Blueprint b : blueprints) {
            Relationship.Blueprint rb = (Relationship.Blueprint) b;

            String fakeId = parentPath.toString() + arrow(rb) + rb.getOtherEnd().toString();
            CanonicalPath cPath = CanonicalPath.of().relationship(fakeId).get();
            boolean hasBeenProcessed = hasBeenProcessed(statuses, elementType, cPath);
            if (hasBeenProcessed) {
                // this relationship has it's own record in the list with statuses so let's move to another one
                continue;
            }

            try {
                Relationships.Single rel = single.relationships(rb.getDirection())
                        .linkWith(rb.getName(), rb.getOtherEnd(), rb.getProperties());
                putStatus(statuses, elementType, cPath, CREATED.getStatusCode());
            } catch (EntityNotFoundException ex) {
                putStatus(statuses, elementType, cPath, NOT_FOUND.getStatusCode());
            } catch (RelationAlreadyExistsException ex) {
                putStatus(statuses, elementType, cPath, CONFLICT.getStatusCode());
            } catch (Exception ex) {
                putStatus(statuses, elementType, cPath, INTERNAL_SERVER_ERROR.getStatusCode());
            }
        }
    }

    private boolean canCreateUnderParent(ElementType elementType, CanonicalPath parentPath,
                                         Map<ElementType, Map<CanonicalPath, Integer>> statuses) {
        if (hasBeenCreatedInBulk(parentPath, statuses)) {
            //the parent has been created in the bulk request. I.e. we're still in a transaction that's creating the
            //entities and therefore the security resources have not been created for such elements yet. We assume that
            //if we were allowed to create the parent, we can also create its child.
            return true;
        }

        switch (elementType) {
            case dataEntity:
                return security.canUpdate(parentPath);
            case relationship:
                throw new IllegalArgumentException("Cannot create anything under a relationship.");
            default:
                return security.canCreate(elementType.elementType).under(parentPath);
        }
    }

    private boolean hasBeenCreatedInBulk(CanonicalPath elementPath, Map<ElementType, Map<CanonicalPath, Integer>>
            statuses) {

        Map<CanonicalPath, Integer> elementsOfType = statuses.get(
                ElementType.ofSegmentType(elementPath.getSegment().getElementType()));

        if (elementsOfType == null) {
            return false;
        }

        Integer status = elementsOfType.get(elementPath);

        if (status == null) {
            return false;
        }

        return status == 201 || status == 204;
    }

    public enum ElementType {
        environment(Environment.class, Environment.Blueprint.class, SegmentType.e),
        resourceType(ResourceType.class, ResourceType.Blueprint.class, SegmentType.rt),
        metricType(MetricType.class, MetricType.Blueprint.class, SegmentType.mt),
        operationType(OperationType.class, OperationType.Blueprint.class, SegmentType.ot),
        feed(Feed.class, Feed.Blueprint.class, SegmentType.f),
        metric(Metric.class, Metric.Blueprint.class, SegmentType.m),
        resource(Resource.class, Resource.Blueprint.class, SegmentType.r),
        dataEntity(DataEntity.class, DataEntity.Blueprint.class, SegmentType.d),
        metadataPack(MetadataPack.class, MetadataPack.Blueprint.class, SegmentType.mp),
        relationship(Relationship.class, Relationship.Blueprint.class, SegmentType.r);

        final Class<? extends AbstractElement<?, ?>> elementType;
        final Class<? extends Blueprint> blueprintType;
        final SegmentType segmentType;

        ElementType(Class<? extends AbstractElement<?, ?>> elementType, Class<? extends Blueprint> blueprintType,
                SegmentType segmentType) {
            this.elementType = elementType;
            this.blueprintType = blueprintType;
            this.segmentType = segmentType;
        }

        public static ElementType ofSegmentType(SegmentType type) {
            for (ElementType et : ElementType.values()) {
                if (et.segmentType.equals(type)) {
                    return et;
                }
            }
            return null;
        }

        public static ElementType ofBlueprintType(Class<?> type) {
            for (ElementType et : ElementType.values()) {
                if (et.blueprintType.equals(type)) {
                    return et;
                }
            }
            return null;
        }
    }

    public static class IdExtractor extends ElementBlueprintVisitor.Simple<String, Void> {
        @Override
        protected String defaultAction(Object blueprint, Void parameter) {
            return ((Entity.Blueprint) blueprint).getId();
        }

        @Override
        public String visitData(DataEntity.Blueprint<?> data, Void parameter) {
            return data.getRole().name();
        }

        @Override public String visitMetadataPack(MetadataPack.Blueprint metadataPack, Void parameter) {
            return "<metadata-pack>";
        }

        @Override
        public String visitRelationship(Relationship.Blueprint relationship, Void parameter) {
            return arrow(relationship) + relationship.getOtherEnd().toString();
        }
    }
}

