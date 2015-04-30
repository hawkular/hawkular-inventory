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
package org.hawkular.inventory.rest;

import org.hawkular.accounts.api.NamedOperation;
import org.hawkular.accounts.api.PermissionChecker;
import org.hawkular.accounts.api.PersonaService;
import org.hawkular.accounts.api.ResourceService;
import org.hawkular.accounts.api.model.Operation;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.EntityVisitor;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

import javax.inject.Inject;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * CDI bean that provides inventory-focused abstractions over Hawkular accounts.
 * It defines all the operations available in inventory and implements permission checking methods.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class Security {

    private final Map<Class<?>, Map<OperationType, Operation>> operationsByType =
            new HashMap<>();
    @Inject @ForRest
    Inventory inventory;

    @Inject
    PermissionChecker permissions;

    @Inject
    ResourceService storage;

    @Inject
    PersonaService personas;

    @Inject
    @NamedOperation("read-tenant")
    private Operation readTenantOperation;

    @Inject
    @NamedOperation("read-environment")
    private Operation readEnvironmentOperation;

    @Inject
    @NamedOperation("read-metricType")
    private Operation readMetricTypeOperation;

    @Inject
    @NamedOperation("read-resourceType")
    private Operation readResourceTypeOperation;

    @Inject
    @NamedOperation("read-feed")
    private Operation readFeedOperation;

    @Inject
    @NamedOperation("read-resource")
    private Operation readResourceOperation;

    @Inject
    @NamedOperation("read-metric")
    private Operation readMetricOperation;

    @Inject
    @NamedOperation("create-tenant")
    private Operation createTenantOperation;

    @Inject
    @NamedOperation("create-environment")
    private Operation createEnvironmentOperation;

    @Inject
    @NamedOperation("create-metricType")
    private Operation createMetricTypeOperation;

    @Inject
    @NamedOperation("create-resourceType")
    private Operation createResourceTypeOperation;

    @Inject
    @NamedOperation("create-feed")
    private Operation createFeedOperation;

    @Inject
    @NamedOperation("create-resource")
    private Operation createResourceOperation;

    @Inject
    @NamedOperation("create-metric")
    private Operation createMetricOperation;

    @Inject
    @NamedOperation("update-tenant")
    private Operation updateTenantOperation;

    @Inject
    @NamedOperation("update-environment")
    private Operation updateEnvironmentOperation;

    @Inject
    @NamedOperation("update-metricType")
    private Operation updateMetricTypeOperation;

    @Inject
    @NamedOperation("update-resourceType")
    private Operation updateResourceTypeOperation;

    @Inject
    @NamedOperation("update-feed")
    private Operation updateFeedOperation;

    @Inject
    @NamedOperation("update-resource")
    private Operation updateResourceOperation;

    @Inject
    @NamedOperation("update-metric")
    private Operation updateMetricOperation;

    @Inject
    @NamedOperation("delete-tenant")
    private Operation deleteTenantOperation;

    @Inject
    @NamedOperation("delete-environment")
    private Operation deleteEnvironmentOperation;

    @Inject
    @NamedOperation("delete-metricType")
    private Operation deleteMetricTypeOperation;

    @Inject
    @NamedOperation("delete-resourceType")
    private Operation deleteResourceTypeOperation;

    @Inject
    @NamedOperation("delete-feed")
    private Operation deleteFeedOperation;

    @Inject
    @NamedOperation("delete-resource")
    private Operation deleteResourceOperation;

    @Inject
    @NamedOperation("delete-metric")
    private Operation deleteMetricOperation;

    @Inject
    @NamedOperation("copy-environment")
    private Operation copyEnvironmentOperation;

    @Inject
    @NamedOperation("associate")
    private Operation associateOperation;

    {
        operationsByType.put(Tenant.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.READ, readTenantOperation);
            put(OperationType.CREATE, createTenantOperation);
            put(OperationType.UPDATE, updateTenantOperation);
            put(OperationType.DELETE, deleteTenantOperation);
        }});

        operationsByType.put(Environment.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.READ, readEnvironmentOperation);
            put(OperationType.CREATE, createEnvironmentOperation);
            put(OperationType.UPDATE, updateEnvironmentOperation);
            put(OperationType.DELETE, deleteEnvironmentOperation);
            put(OperationType.COPY, copyEnvironmentOperation);
        }});

        operationsByType.put(ResourceType.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.READ, readResourceTypeOperation);
            put(OperationType.CREATE, createResourceTypeOperation);
            put(OperationType.UPDATE, updateResourceTypeOperation);
            put(OperationType.DELETE, deleteResourceTypeOperation);
        }});

        operationsByType.put(MetricType.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.READ, readMetricTypeOperation);
            put(OperationType.CREATE, createMetricTypeOperation);
            put(OperationType.UPDATE, updateMetricTypeOperation);
            put(OperationType.DELETE, deleteMetricTypeOperation);
        }});

        operationsByType.put(Feed.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.READ, readFeedOperation);
            put(OperationType.CREATE, createFeedOperation);
            put(OperationType.UPDATE, updateFeedOperation);
            put(OperationType.DELETE, deleteFeedOperation);
        }});

        operationsByType.put(Resource.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.READ, readResourceOperation);
            put(OperationType.CREATE, createResourceOperation);
            put(OperationType.UPDATE, updateResourceOperation);
            put(OperationType.DELETE, deleteResourceOperation);
        }});

        operationsByType.put(Metric.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.READ, readMetricOperation);
            put(OperationType.CREATE, createMetricOperation);
            put(OperationType.UPDATE, updateMetricOperation);
            put(OperationType.DELETE, deleteMetricOperation);
        }});
    }

    public static String getStableId(AbstractElement<?, ?> element) {
        if (element instanceof Relationship) {
            return element.getId();
        } else {
            return ((Entity<?, ?>) element).accept(new EntityVisitor<String, Void>() {
                @Override
                public String visitTenant(Tenant tenant, Void parameter) {
                    return getStableId(Tenant.class, tenant.getId());
                }

                @Override
                public String visitEnvironment(Environment environment, Void parameter) {
                    return getStableId(Environment.class, environment.getTenantId(), environment.getId());
                }

                @Override
                public String visitFeed(Feed feed, Void parameter) {
                    return getStableId(Feed.class, feed.getTenantId(), feed.getEnvironmentId(), feed.getId());
                }

                @Override
                public String visitMetric(Metric metric, Void parameter) {
                    if (metric.getFeedId() == null) {
                        return getStableId(Metric.class, metric.getTenantId(), metric.getEnvironmentId(),
                                metric.getId());
                    } else {
                        return getStableId(Metric.class, metric.getTenantId(), metric.getEnvironmentId(),
                                metric.getFeedId(), metric.getId());

                    }
                }

                @Override
                public String visitMetricType(MetricType metricType, Void parameter) {
                    return getStableId(MetricType.class, metricType.getTenantId(), metricType.getId());
                }

                @Override
                public String visitResource(Resource resource, Void parameter) {
                    return join(resource.getTenantId(), resource.getEnvironmentId(), "resources", resource.getId());
                }

                @Override
                public String visitResourceType(ResourceType type, Void parameter) {
                    return join(type.getTenantId(), "resourceTypes", type.getId());
                }
            }, null);
        }
    }

    public static String getStableId(Class<? extends AbstractElement<?, ?>> type, String... ids) {
        if (Tenant.class.isAssignableFrom(type)) {
            return join("tenants", ids[0]);
        } else if (Environment.class.isAssignableFrom(type)) {
            return join(ids[0], "environments", ids[1]);
        } else if (ResourceType.class.isAssignableFrom(type)) {
            return join(ids[0], "resourceTypes", ids[1]);
        } else if (MetricType.class.isAssignableFrom(type)) {
            return join(ids[0], "metricTypes", ids[1]);
        } else if (Feed.class.isAssignableFrom(type)) {
            return join(ids[0], ids[1], "feeds", ids[2]);
        } else if (Resource.class.isAssignableFrom(type)) {
            if (ids.length == 3) {
                return join(ids[0], ids[1], "resources", ids[2]);
            } else {
                return join(ids[0], ids[1], ids[2], "resources", ids[3]);
            }
        } else if (Metric.class.isAssignableFrom(type)) {
            if (ids.length == 3) {
                return join(ids[0], ids[1], "metrics", ids[2]);
            } else {
                return join(ids[0], ids[1], ids[2], "metrics", ids[3]);
            }
        } else if (Relationship.class.isAssignableFrom(type)) {
            return join("relationships", ids[0]);
        } else {
            throw new IllegalArgumentException("Unknown entity type: " + type);
        }
    }

    private static String join(String... strings) {
        if (strings.length == 0) {
            return null;
        } else if (strings.length == 1) {
            return strings[0];
        } else {
            StringBuilder bld = new StringBuilder(strings[0]);
            for (int i = 1; i < strings.length; ++i) {
                bld.append('/').append(strings[i]);
            }

            return bld.toString();
        }
    }

    private Operation read(Class<?> entityType) {
        return getOperation(entityType, OperationType.READ);
    }

    public boolean canRead(Class<? extends Entity<?, ?>> entityType, String... entityPath) {
        return permissions.isAllowedTo(read(entityType), getStableId(entityType, entityPath));
    }

    public boolean canRead(AbstractElement<?, ?> element) {
        return permissions.isAllowedTo(read(element.getClass()), getStableId(element));
    }

    private Operation create(Class<?> entityType) {
        return getOperation(entityType, OperationType.CREATE);
    }

    public CreatePermissionCheckerFinisher canCreate(Class<?> entityType) {
        return new CreatePermissionCheckerFinisher(entityType);
    }

    private Operation update(Class<?> entityType) {
        return getOperation(entityType, OperationType.UPDATE);
    }

    public boolean canUpdate(Class<? extends Entity<?, ?>> entityType, String... entityPath) {
        return permissions.isAllowedTo(update(entityType), getStableId(entityType, entityPath));
    }

    private Operation delete(Class<?> entityType) {
        return getOperation(entityType, OperationType.DELETE);
    }

    public boolean canDelete(Class<? extends Entity<?, ?>> entityType, String... entityPath) {
        return permissions.isAllowedTo(delete(entityType), getStableId(entityType, entityPath));
    }

    private Operation associate() {
        return associateOperation;
    }

    public boolean canAssociateFrom(Class<? extends Entity<?, ?>> entityType, String... entityPath) {
        return permissions.isAllowedTo(associate(), getStableId(entityType, entityPath));
    }

    private Operation copy() {
        return copyEnvironmentOperation;
    }

    public boolean canCopyEnvironment(String... environmentPath) {
        return permissions.isAllowedTo(copy(), getStableId(Environment.class, environmentPath));
    }

    private Operation getOperation(Class<?> cls, OperationType operationType) {
        Map<OperationType, Operation> ops = operationsByType.get(cls);
        if (ops == null) {
            throw new IllegalArgumentException("There is no " + operationType + " operation for elements of type " +
                    cls);
        }

        return ops.get(operationType);
    }

    private enum OperationType {
        READ, CREATE, UPDATE, DELETE, COPY, ASSOCIATE
    }

    public final class CreatePermissionCheckerFinisher {
        private final Class<?> createdType;

        private CreatePermissionCheckerFinisher(Class<?> createdType) {
            this.createdType = createdType;
        }

        boolean under(Class<? extends Entity<?, ?>> parentType, String... parentPath) {
            return permissions.isAllowedTo(create(createdType), parentType == null ? "/" :
                    getStableId(parentType, parentPath));
        }
    }
}
