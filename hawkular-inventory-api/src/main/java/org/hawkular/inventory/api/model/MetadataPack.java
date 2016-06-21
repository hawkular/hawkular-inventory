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
package org.hawkular.inventory.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;

/**
 * A metadata pack incorporates a bunch of resource types and metric types. It computes a hash of its "contents" so that
 * merely by examining the hash, one can make sure that certain set of resource types and metric types is present in
 * the form one expects.
 *
 * @author Lukas Krejci
 * @since 0.7.0
 */
@ApiModel(description = "A metadata pack can incorporate global resource and metric types making them read-only.",
        parent = Entity.class)
public final class MetadataPack extends Entity<MetadataPack.Blueprint, MetadataPack.Update> {

    public static final SegmentType SEGMENT_TYPE = SegmentType.mp;

    public static boolean canIncorporate(CanonicalPath entityPath) {
        SegmentType entityType = entityPath.getSegment().getElementType();
        SegmentType parentType = entityPath.up().getSegment().getElementType();

        return SegmentType.t.equals(parentType)
                && (SegmentType.rt.equals(entityType) || SegmentType.mt.equals(entityType));
    }

    private MetadataPack() {
    }

    public MetadataPack(CanonicalPath path) {
        this(path, null);
    }

    public MetadataPack(CanonicalPath path, Map<String, Object> properties) {
        this(null, path, properties);
    }

    public MetadataPack(String name, CanonicalPath path) {
        this(name, path, null);
    }

    public MetadataPack(String name, CanonicalPath path, Map<String, Object> properties) {
        super(name, path, properties);
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitMetadataPack(this, parameter);
    }

    @Override
    public Updater<Update, MetadataPack> update() {
        return new Updater<>((u) -> new MetadataPack(getPath(), u.getProperties()));
    }

    /**
     * This class can be used to completely describe the structure of the metadata pack offline, without needing
     * to "touch" inventory. This is particularly useful with
     * {@link IdentityHash#of(Members)} method that can be used to compute a hash of
     * a metadata pack that might not yet exist without needing to consult inventory.
     * <p>
     * Note that this structure uses entities but the content hash computation only needs the ID of the entities
     * from the paths. As such, the IDs of parent entities of resource types and metric types are irrelevant.
     */
    public static final class Members {
        private final List<ResourceType.Blueprint> resourceTypes;
        private final List<MetricType.Blueprint> metricTypes;
        private final IdentityHashMap<ResourceType.Blueprint, List<OperationType.Blueprint>> operations;
        private final IdentityHashMap<OperationType.Blueprint, DataEntity.Blueprint<?>> returnTypes;
        private final IdentityHashMap<OperationType.Blueprint, DataEntity.Blueprint<?>> parameterTypes;
        private final IdentityHashMap<ResourceType.Blueprint, DataEntity.Blueprint<?>> configurationSchemas;
        private final IdentityHashMap<ResourceType.Blueprint, DataEntity.Blueprint<?>> connectionConfigurationSchemas;

        public static Builder builder() {
            return new Builder();
        }

        private Members(Set<ResourceType.Blueprint> resourceTypes, Set<MetricType.Blueprint> metricTypes,
                        IdentityHashMap<ResourceType.Blueprint, Set<OperationType.Blueprint>> operations,
                        IdentityHashMap<OperationType.Blueprint, DataEntity.Blueprint<?>> returnTypes,
                        IdentityHashMap<OperationType.Blueprint, DataEntity.Blueprint<?>> parameterTypes,
                        IdentityHashMap<ResourceType.Blueprint, DataEntity.Blueprint<?>> configurationSchemas,
                        IdentityHashMap<ResourceType.Blueprint, DataEntity.Blueprint<?>>
                                  connectionConfigurationSchemas) {
            this.resourceTypes = new ArrayList<>(resourceTypes);

            Comparator<Entity.Blueprint> idComp = (a, b) -> a.getId().compareTo(b.getId());

            Collections.sort(this.resourceTypes, idComp);

            this.metricTypes = new ArrayList<>(metricTypes);
            Collections.sort(this.metricTypes, idComp);

            this.operations = new IdentityHashMap<>();
            operations.forEach((k, v) -> {
                List<OperationType.Blueprint> ops = new ArrayList<>(v);
                Collections.sort(ops, idComp);
                this.operations.put(k, ops);
            });

            this.returnTypes = returnTypes;
            this.parameterTypes = parameterTypes;
            this.configurationSchemas = configurationSchemas;
            this.connectionConfigurationSchemas = connectionConfigurationSchemas;
        }

        public List<ResourceType.Blueprint> getResourceTypes() {
            return resourceTypes;
        }

        public List<MetricType.Blueprint> getMetricTypes() {
            return metricTypes;
        }

        public List<OperationType.Blueprint> getOperationTypes(ResourceType.Blueprint resourceType) {
            return operations.getOrDefault(resourceType, Collections.emptyList());
        }

        public DataEntity.Blueprint<?> getReturnType(OperationType.Blueprint operationType) {
            return thatOrEmpty(returnTypes.get(operationType), DataRole.OperationType.returnType);
        }

        public DataEntity.Blueprint<?> getParameterTypes(OperationType.Blueprint operationType) {
            return thatOrEmpty(parameterTypes.get(operationType), DataRole.OperationType.parameterTypes);
        }

        public DataEntity.Blueprint<?> getConfigurationSchema(ResourceType.Blueprint rt) {
            return thatOrEmpty(configurationSchemas.get(rt), DataRole.ResourceType.configurationSchema);
        }

        public DataEntity.Blueprint<?> getConnectionConfigurationSchema(ResourceType.Blueprint rt) {
            return thatOrEmpty(connectionConfigurationSchemas.get(rt),
                    DataRole.ResourceType.connectionConfigurationSchema);
        }

        private DataEntity.Blueprint<?> thatOrEmpty(DataEntity.Blueprint<?> b, DataRole role) {
            if (b == null) {
                b = DataEntity.Blueprint.builder().withRole(role).build();
            }

            return b;
        }

        public static final class Builder {
            private final Set<ResourceType.Blueprint> resourceTypes = new HashSet<>();
            private final Set<MetricType.Blueprint> metricTypes = new HashSet<>();
            private final IdentityHashMap<ResourceType.Blueprint, Set<OperationType.Blueprint>>
                    resourceTypeOperationTypes = new IdentityHashMap<>();
            private final IdentityHashMap<OperationType.Blueprint, DataEntity.Blueprint<?>> operationTypeReturnType =
                    new IdentityHashMap<>();
            private final IdentityHashMap<OperationType.Blueprint, DataEntity.Blueprint<?>>
                    operationTypeParameterTypes = new IdentityHashMap<>();
            private final IdentityHashMap<ResourceType.Blueprint, DataEntity.Blueprint<?>>
                    resourceTypeConfigurationSchemas = new IdentityHashMap<>();
            private final IdentityHashMap<ResourceType.Blueprint, DataEntity.Blueprint<?>>
                    resourceTypeConnectionConfigurationSchemas = new IdentityHashMap<>();

            public ResourceTypeBuilder with(ResourceType.Blueprint rt) {
                resourceTypes.add(rt);
                return new ResourceTypeBuilder(rt);
            }

            public Builder with(MetricType.Blueprint mt) {
                metricTypes.add(mt);
                return this;
            }

            public Members build() {
                return new Members(resourceTypes, metricTypes, resourceTypeOperationTypes, operationTypeReturnType,
                        operationTypeParameterTypes, resourceTypeConfigurationSchemas,
                        resourceTypeConnectionConfigurationSchemas);
            }

            public final class ResourceTypeBuilder {
                private final ResourceType.Blueprint rt;

                private ResourceTypeBuilder(ResourceType.Blueprint rt) {
                    this.rt = rt;
                }

                public OperationTypeBuilder with(OperationType.Blueprint ot) {
                    Set<OperationType.Blueprint> ots = resourceTypeOperationTypes.get(rt);
                    if (ots == null) {
                        ots = new HashSet<>();
                        resourceTypeOperationTypes.put(rt, ots);
                    }

                    ots.add(ot);

                    return new OperationTypeBuilder(ot);
                }

                public ResourceTypeBuilder with(DataEntity.Blueprint<DataRole.ResourceType> data) {
                    switch (data.getRole()) {
                        case configurationSchema:
                            resourceTypeConfigurationSchemas.put(rt, data);
                            break;
                        case connectionConfigurationSchema:
                            resourceTypeConnectionConfigurationSchemas.put(rt, data);
                            break;
                    }

                    return this;
                }

                public Builder done() {
                    return Builder.this;
                }

                public final class OperationTypeBuilder {
                    private final OperationType.Blueprint ot;

                    private OperationTypeBuilder(OperationType.Blueprint ot) {
                        this.ot = ot;
                    }

                    public OperationTypeBuilder with(DataEntity.Blueprint<DataRole.OperationType> data) {
                        switch (data.getRole()) {
                            case returnType:
                                operationTypeReturnType.put(ot, data);
                                break;
                            case parameterTypes:
                                operationTypeParameterTypes.put(ot, data);
                                break;
                        }

                        return this;
                    }

                    public ResourceTypeBuilder done() {
                        return ResourceTypeBuilder.this;
                    }
                }
            }
        }
    }

    @ApiModel("MetadataPackBlueprint")
    public static final class Blueprint extends AbstractElement.Blueprint {

        private final Set<CanonicalPath> members;
        private final String name;

        public static Builder builder() {
            return new Builder();
        }

        private Blueprint() {
            super(null);
            members = Collections.emptySet();
            name = null;
        }

        public Blueprint(String name, Set<CanonicalPath> members, Map<String, Object> properties) {
            super(properties);

            this.name = name;

            members.forEach((p) -> {
                if (!canIncorporate(p)) {
                    throw new IllegalArgumentException("Entity on path '" + p + "' cannot be part of a metadata pack.");
                }
            });

            this.members = Collections.unmodifiableSet(new HashSet<>(members));
        }

        public String getName() {
            return name;
        }

        public Set<CanonicalPath> getMembers() {
            return members;
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitMetadataPack(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {
            private final Set<CanonicalPath> members = new HashSet<>();
            private String name;

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withMember(CanonicalPath path) {
                if (!canIncorporate(path)) {
                    throw new IllegalArgumentException(
                            "A metadata pack cannot incorporate entity on the path: " + path);
                }

                members.add(path);
                return this;
            }

            public Builder withMembers(Iterable<CanonicalPath> paths) {
                paths.forEach(this::withMember);
                return this;
            }

            public Builder withMembers(CanonicalPath... paths) {
                return withMembers(Arrays.asList(paths));
            }

            @Override
            public Blueprint build() {
                return new Blueprint(name, members, properties);
            }
        }
    }

    @ApiModel("MetadataPackUpdate")
    public static final class Update extends Entity.Update {

        public static Builder builder() {
            return new Builder();
        }

        public Update(Map<String, Object> properties) {
            super(null, properties);
        }

        public Update(String name, Map<String, Object> properties) {
            super(name, properties);
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitMetadataPack(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(properties);
            }
        }
    }
}
