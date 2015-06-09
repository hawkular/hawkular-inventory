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

import org.hawkular.accounts.api.OperationService;
import org.hawkular.accounts.api.PermissionChecker;
import org.hawkular.accounts.api.model.Operation;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.cdi.ObservableAutoTenant;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CDI bean that provides inventory-focused abstractions over Hawkular accounts.
 * It defines all the operations available in inventory and implements permission checking methods.
 *
 * @author Lukas Krejci
 * @since 0.0.2
 */
@Singleton
public class Security {

    private final Map<Class<?>, Map<OperationType, Operation>> operationsByType =
            new HashMap<>();

    @Inject
    private PermissionChecker permissions;

    @Inject
    private OperationService operations;

    @Inject
    @ObservableAutoTenant
    private Inventory.Mixin.AutoTenantAndObservable inventory;

    @javax.annotation.Resource
    private UserTransaction transaction;

    public static String getStableId(AbstractElement<?, ?> element) {
        if (element instanceof Relationship) {
            return element.getId();
        } else {
            return ((Entity<?, ?>) element).accept(new ElementVisitor.Simple<String, Void>() {
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
                    if (resource.getFeedId() == null) {
                        return getStableId(Resource.class, resource.getTenantId(), resource.getEnvironmentId(),
                                resource.getId());
                    } else {
                        return getStableId(Resource.class, resource.getTenantId(), resource.getEnvironmentId(),
                                resource.getFeedId(), resource.getId());
                    }
                }

                @Override
                public String visitResourceType(ResourceType type, Void parameter) {
                    return getStableId(ResourceType.class, type.getTenantId(), type.getId());
                }
            }, null);
        }
    }

    public static String getStableId(Class<? extends AbstractElement<?, ?>> type, String... ids) {
        if (Tenant.class.isAssignableFrom(type)) {
            return join("tenant", ids[0]);
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
            return "relationships/" + ids[0];
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

            String retString = bld.toString();
            if (retString.length() > 250) {
                // the length is too long, let's create a 'uuid hash' of the url and prefix it with the tenant uuid
                StringBuilder bld2 = new StringBuilder(strings[0]);
                bld2.append('/').append(UUID.nameUUIDFromBytes(retString.getBytes()));
                return bld2.toString();
            }
            return retString;
        }
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
        return safePermissionCheck(entityType, last(entityPath), update(entityType),
                getStableId(entityType, entityPath));
    }

    private Operation delete(Class<?> entityType) {
        return getOperation(entityType, OperationType.DELETE);
    }

    public boolean canDelete(Class<? extends Entity<?, ?>> entityType, String... entityPath) {
        return safePermissionCheck(entityType, last(entityPath), delete(entityType),
                getStableId(entityType, entityPath));
    }

    private Operation associate() {
        return operationsByType.get(Relationship.class).get(OperationType.ASSOCIATE);
    }

    public boolean canAssociateFrom(Class<? extends Entity<?, ?>> entityType, String... entityPath) {
        return safePermissionCheck(entityType, last(entityPath), associate(), getStableId(entityType, entityPath));
    }

    private Operation copy() {
        return operationsByType.get(Environment.class).get(OperationType.COPY);
    }

    public boolean canCopyEnvironment(String... environmentPath) {
        return safePermissionCheck(Environment.class, last(environmentPath), copy(),
                getStableId(Environment.class, environmentPath));
    }

    private Operation getOperation(Class<?> cls, OperationType operationType) {
        Map<OperationType, Operation> ops = operationsByType.get(cls);
        if (ops == null) {
            throw new IllegalArgumentException("There is no " + operationType + " operation for elements of type " +
                    cls);
        }

        return ops.get(operationType);
    }


    private boolean safePermissionCheck(Class<?> entityType, String entityId, Operation operation, String stableId) {
        try {
            if (Tenant.class.equals(entityType)) {
                //make sure the tenant exists prior to checking perms on it
                if (!inventory.tenants().get(entityId).exists()) {
                    inventory.tenants().create(Tenant.Blueprint.builder().withId(entityId).build());
                }
            }
            return permissions.isAllowedTo(operation, stableId);
        } catch (Exception e) {
            RestApiLogger.LOGGER.securityCheckFailed(stableId, e);
            return false;
        }
    }

    private static String last(String... elements) {
        return elements[elements.length - 1];
    }

    @PostConstruct
    public void initOperationsMap() throws SystemException, NotSupportedException, HeuristicRollbackException,
            HeuristicMixedException, RollbackException {

        // Monitor – a read-only role. Cannot modify any resource.
        // Operator – Monitor permissions, plus can modify runtime state, but cannot modify anything that ends up in the
        //            persistent configuration. Could, for example, restart a server.
        // Maintainer – Operator permissions, plus can modify the persistent configuration.
        // Deployer – like a Maintainer, but with permission to modify persistent configuration constrained to resources
        //            that are considered to be "application resources". A deployment is an application resource. The
        //            messaging server is not. Items like datasources and JMS destinations are not considered to be
        //            application resources by default, but this is configurable.
        //
        // Three roles are granted permissions for security sensitive items:
        //
        // SuperUser – has all permissions. Equivalent to a JBoss AS 7 administrator.
        // Administrator – has all permissions except cannot read or write resources related to the administrative audit
        //                 logging system.
        // Auditor – can read anything. Can only modify the resources related to the administrative audit logging
        //           system.

        transaction.begin();

        try {
            operations.setup("update-tenant").add("SuperUser").persist();
            operations.setup("delete-tenant").add("SuperUser").persist();

            operations.setup("create-environment").add("Administrator").persist();
            operations.setup("update-environment").add("Administrator").persist();
            operations.setup("delete-environment").add("Administrator").persist();
            operations.setup("copy-environment").add("Administrator").persist();

            operations.setup("create-resourceType").add("Administrator").persist();
            operations.setup("update-resourceType").add("Administrator").persist();
            operations.setup("delete-resourceType").add("Administrator").persist();

            operations.setup("create-metricType").add("Administrator").persist();
            operations.setup("update-metricType").add("Administrator").persist();
            operations.setup("delete-metricType").add("Administrator").persist();

            operations.setup("create-feed").add("Administrator").persist();
            operations.setup("update-feed").add("Administrator").persist();
            operations.setup("delete-feed").add("Administrator").persist();

            operations.setup("create-resource").add("Maintainer").persist();
            operations.setup("update-resource").add("Maintainer").persist();
            operations.setup("delete-resource").add("Maintainer").persist();

            operations.setup("create-metric").add("Maintainer").persist();
            operations.setup("update-metric").add("Maintainer").persist();
            operations.setup("delete-metric").add("Maintainer").persist();

            operations.setup("associate").add("Operator").persist();

            transaction.commit();
        } catch (Throwable t) {
            transaction.rollback();
            throw t;
        }

        Operation updateTenantOperation = operations.getByName("update-tenant");
        Operation deleteTenantOperation = operations.getByName("delete-tenant");

        Operation createEnvironmentOperation = operations.getByName("create-environment");
        Operation updateEnvironmentOperation = operations.getByName("update-environment");
        Operation deleteEnvironmentOperation = operations.getByName("delete-environment");
        Operation copyEnvironmentOperation = operations.getByName("copy-environment");

        Operation createResourceTypeOperation = operations.getByName("create-resourceType");
        Operation updateResourceTypeOperation = operations.getByName("update-resourceType");
        Operation deleteResourceTypeOperation = operations.getByName("delete-resourceType");

        Operation createMetricTypeOperation = operations.getByName("create-metricType");
        Operation updateMetricTypeOperation = operations.getByName("update-metricType");
        Operation deleteMetricTypeOperation = operations.getByName("delete-metricType");

        Operation createFeedOperation = operations.getByName("create-feed");
        Operation updateFeedOperation = operations.getByName("update-feed");
        Operation deleteFeedOperation = operations.getByName("delete-feed");

        Operation createResourceOperation = operations.getByName("create-resource");
        Operation updateResourceOperation = operations.getByName("update-resource");
        Operation deleteResourceOperation = operations.getByName("delete-resource");

        Operation createMetricOperation = operations.getByName("create-metric");
        Operation updateMetricOperation = operations.getByName("update-metric");
        Operation deleteMetricOperation = operations.getByName("delete-metric");

        Operation associate = operations.getByName("associate");

        operationsByType.put(Tenant.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.UPDATE, updateTenantOperation);
            put(OperationType.DELETE, deleteTenantOperation);
        }});

        operationsByType.put(Environment.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.CREATE, createEnvironmentOperation);
            put(OperationType.UPDATE, updateEnvironmentOperation);
            put(OperationType.DELETE, deleteEnvironmentOperation);
            put(OperationType.COPY, copyEnvironmentOperation);
        }});

        operationsByType.put(ResourceType.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.CREATE, createResourceTypeOperation);
            put(OperationType.UPDATE, updateResourceTypeOperation);
            put(OperationType.DELETE, deleteResourceTypeOperation);
        }});

        operationsByType.put(MetricType.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.CREATE, createMetricTypeOperation);
            put(OperationType.UPDATE, updateMetricTypeOperation);
            put(OperationType.DELETE, deleteMetricTypeOperation);
        }});

        operationsByType.put(Feed.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.CREATE, createFeedOperation);
            put(OperationType.UPDATE, updateFeedOperation);
            put(OperationType.DELETE, deleteFeedOperation);
        }});

        operationsByType.put(Resource.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.CREATE, createResourceOperation);
            put(OperationType.UPDATE, updateResourceOperation);
            put(OperationType.DELETE, deleteResourceOperation);
        }});

        operationsByType.put(Metric.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.CREATE, createMetricOperation);
            put(OperationType.UPDATE, updateMetricOperation);
            put(OperationType.DELETE, deleteMetricOperation);
        }});

        operationsByType.put(Relationship.class, new EnumMap<OperationType, Operation>(OperationType.class) {{
            put(OperationType.ASSOCIATE, associate);
        }});
    }

    private enum OperationType {
        CREATE, UPDATE, DELETE, COPY, ASSOCIATE
    }

    public final class CreatePermissionCheckerFinisher {

        private final Class<?> createdType;

        private CreatePermissionCheckerFinisher(Class<?> createdType) {
            this.createdType = createdType;
        }

        boolean under(Class<? extends Entity<?, ?>> parentType, String... parentPath) {
            String entityId = getStableId(parentType, parentPath);
            return safePermissionCheck(createdType, last(parentPath), create(createdType), entityId);
        }
    }
}
