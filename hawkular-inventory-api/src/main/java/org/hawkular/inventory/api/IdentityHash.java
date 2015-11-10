/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.api;

import static org.hawkular.inventory.api.OperationTypes.DataRole.parameterTypes;
import static org.hawkular.inventory.api.OperationTypes.DataRole.returnType;
import static org.hawkular.inventory.api.ResourceTypes.DataRole.configurationSchema;
import static org.hawkular.inventory.api.ResourceTypes.DataRole.connectionConfigurationSchema;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementBlueprintVisitor;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;

/**
 * Produces an identity hash of entities. Identity hash is a hash that uniquely identifies an entity
 * and is produced using its user-defined id and structure. This hash is used to match a client-side state of an
 * entity (MetadataPack, ResourceType, MetricType) with the severside state of it.
 * <p>
 * The identity hash is defined only for the following types of entities:
 * {@link org.hawkular.inventory.api.model.ResourceType}, {@link org.hawkular.inventory.api.model.MetricType} and
 * {@link org.hawkular.inventory.api.model.OperationType}.
 * <p>
 * The identity hash is an SHA1 hash of a string representation of the entity (in UTF-8 encoding). The string
 * representation is produced as follows:
 * <ol>
 * <li>MetricType: id + type + unit
 * <li>OperationType: id + minimized(returnTypeJSON) + minimized(parameterTypesJSON)
 * <li>ResourceType: id + minimized(configurationSchemaJSON) + minimized(connectionConfigurationSchemaJSON)
 * + operationTypeString*<br/>
 * the operation types are sorted alphabetically by their ids
 * </ol>
 * where {@code minimized()} means that the JSON is stripped of any superfluous whitespace and {@code *} means
 * repetition for necessary number of times.
 *
 * @author Lukas Krejci
 * @since 0.7.0
 */
public final class IdentityHash {

    private IdentityHash() {

    }

    public static String of(MetadataPack.Members metadata) {
        StringBuilder bld = new StringBuilder();

        for (MetricType.Blueprint mt : metadata.getMetricTypes()) {
            appendStringRepresentation(mt, metadata, bld);
        }

        for (ResourceType.Blueprint rt : metadata.getResourceTypes()) {
            appendStringRepresentation(rt, metadata, bld);
        }

        return computeDigest(bld);
    }

    public static String of(Entity<?, ?> entity, Inventory inventory) {
        StringBuilder bld = new StringBuilder();

        appendStringRepresentation(entity, inventory, bld);

        if (bld.length() == 0) {
            return null;
        }

        return computeDigest(bld);
    }

    public static String of(Iterable<? extends Entity<?, ?>> entities, Inventory inventory) {
        return of(entities.iterator(), inventory);
    }

    public static String of(Iterator<? extends Entity<?, ?>> entities, Inventory inventory) {
        SortedSet<Entity<?, ?>> sortedEntities = new TreeSet<>((a, b) -> {
            if (a == null) return b == null ? 0 : -1;
            if (b == null) return 1;

            if (!a.getClass().equals(b.getClass())) {
                return a.getClass().getName().compareTo(b.getClass().getName());
            } else {
                return a.getId().compareTo(b.getId());
            }
        });
        entities.forEachRemaining(sortedEntities::add);

        StringBuilder bld = new StringBuilder();

        sortedEntities.forEach((e) -> appendStringRepresentation(e, inventory, bld));

        return computeDigest(bld);
    }

    private static void appendStringRepresentation(Entity<?, ?> entity, Inventory inventory, StringBuilder bld) {
        entity.accept(new ElementVisitor.Simple<Void, Void>(null) {

            @Override public Void visitData(DataEntity data, Void parameter) {
                try {
                    data.getValue().writeJSON(bld);
                } catch (IOException e) {
                    throw new AssertionError("Exception while writing to StringBuilder. This should never happen.", e);
                }
                return null;
            }

            @Override
            public Void visitMetricType(MetricType mt, Void parameter) {
                bld.append(mt.getId()).append(mt.getType().name()).append(mt.getUnit().name());
                return null;
            }

            @Override
            public Void visitOperationType(OperationType ot, Void parameter) {
                DataEntity ret = fetchOrDefault(inventory.inspect(ot).data().get(returnType));
                DataEntity params = fetchOrDefault(inventory.inspect(ot).data().get(parameterTypes));

                bld.append(ot.getId());
                ret.accept(this, null);
                params.accept(this, null);

                return null;
            }

            @Override
            public Void visitResourceType(ResourceType rt, Void parameter) {
                Set<OperationType> ots = inventory.inspect(rt).operationTypes().getAll().entities();
                DataEntity config = fetchOrDefault(inventory.inspect(rt).data().get(configurationSchema));
                DataEntity conn = fetchOrDefault(inventory.inspect(rt).data().get(connectionConfigurationSchema));

                bld.append(rt.getId());
                config.accept(this, null);
                conn.accept(this, null);

                ots.stream()
                        .sorted((a, b) -> a.getId().compareTo(b.getId()))
                        .forEach((ot) -> ot.accept(this, null));

                return null;
            }

            private DataEntity fetchOrDefault(Data.Single accessor) {
                try {
                    return accessor.entity();
                } catch (EntityNotFoundException e) {
                    //this doesn't actually matter too much, we only need the return type to be non-null and have
                    //the undefined value.
                    return new DataEntity(CanonicalPath.of().tenant("dummy").resourceType("dummy").get(),
                            configurationSchema, StructuredData.get().undefined());
                }
            }
        }, null);
    }

    private static void appendStringRepresentation(Entity.Blueprint entity, MetadataPack.Members members,
                                                   StringBuilder bld) {

        entity.accept(new ElementBlueprintVisitor.Simple<Void, Void>() {
            @Override
            public Void visitData(DataEntity.Blueprint<?> data, Void parameter) {
                try {
                    data.getValue().writeJSON(bld);
                } catch (IOException e) {
                    throw new AssertionError("Exception while writing to a StringBuilder. This should never happen.",
                            e);
                }
                return null;
            }

            @Override
            public Void visitMetricType(MetricType.Blueprint mt, Void parameter) {
                bld.append(mt.getId()).append(mt.getType().name()).append(mt.getUnit().name());
                return null;
            }

            @Override
            public Void visitOperationType(OperationType.Blueprint operationType, Void parameter) {
                DataEntity.Blueprint<?> returnType = members.getReturnType(operationType);
                DataEntity.Blueprint<?> parameterTypes = members.getParameterTypes(operationType);

                bld.append(operationType.getId());
                returnType.accept(this, null);
                parameterTypes.accept(this, null);
                return null;
            }

            @Override
            public Void visitResourceType(ResourceType.Blueprint type, Void parameter) {
                DataEntity.Blueprint<?> configSchema = members.getConfigurationSchema(type);
                DataEntity.Blueprint<?> connSchema = members.getConnectionConfigurationSchema(type);
                List<OperationType.Blueprint> ots = members.getOperationTypes(type);

                bld.append(type.getId());
                configSchema.accept(this, null);
                connSchema.accept(this, null);

                ots.forEach((ot) -> ot.accept(this, null));

                return null;
            }
        }, null);
    }

    private static String computeDigest(StringBuilder bld) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(bld.toString().getBytes(Charset.forName("UTF-8")));

            bld.delete(0, bld.length());
            for (byte b : digest) {
                bld.append(Integer.toHexString(Byte.toUnsignedInt(b)));
            }

            return bld.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not produce and SHA-1 hash.", e);
        }
    }
}
