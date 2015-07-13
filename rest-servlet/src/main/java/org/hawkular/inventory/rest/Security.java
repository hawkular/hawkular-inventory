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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.hawkular.accounts.api.OperationService;
import org.hawkular.accounts.api.PermissionChecker;
import org.hawkular.accounts.api.model.Operation;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;

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
    @AutoTenant
    private Inventory inventory;

    @javax.annotation.Resource
    private UserTransaction transaction;

    public static String getStableId(CanonicalPath path) {
        return shortenIfNeeded(path);
    }

    public static String getStableId(AbstractElement<?, ?> element) {
        return getStableId(element.getPath());
    }

    public static boolean isValidRestPath(String restPath) {
        if (restPath == null || restPath.trim().isEmpty()) {
            return false;
        }
        String[] chunks = restPath.split("/");
        if (chunks == null || chunks.length < 2) {
            return false;
        }
        if (chunks.length == 2 && ("tenants".equals(chunks[0]) || "relationships".equals(chunks[0]))
                && chunks[1].length() > 0) {
            return true;
        }
        if (chunks.length == 3 && chunks[0].length() > 0 && chunks[2].length() > 0) {
            return "environments".equals(chunks[1]) || "resourceTypes".equals(chunks[1])
                    || "metricTypes".equals(chunks[1]);
        }
        if (chunks.length == 4 && chunks[0].length() > 0 && chunks[1].length() > 0 && chunks[3].length() > 0) {
            return "resources".equals(chunks[2]) || "metrics".equals(chunks[2]);
        }
        if (chunks.length == 5 && chunks[0].length() > 0 && chunks[1].length() > 0 && chunks[2].length() > 0
                && chunks[4].length() > 0) {
            return "resources".equals(chunks[3]) || "metrics".equals(chunks[3]);
        }
        return false;
    }

    public static CanonicalPath toCanonicalPath(String restPath) {
        String[] chunks = restPath.split("/");
        CanonicalPath.Extender path = CanonicalPath.empty();
        if (chunks.length == 2) {
            if ("tenants".equals(chunks[0])) {
                path.extend(Tenant.class, chunks[1]);
            } else if ("relationships".equals(chunks[0])) {
                path.extend(Relationship.class, chunks[1]);
            }
        } else if (chunks.length == 3) {
            if ("environments".equals(chunks[1])) {
                path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[2]);
            } else if ("resourceTypes".equals(chunks[1])) {
                path.extend(Tenant.class, chunks[0]).extend(ResourceType.class, chunks[2]);
            } else if ("metricTypes".equals(chunks[1])) {
                path.extend(Tenant.class, chunks[0]).extend(MetricType.class, chunks[2]);
            }
        } else if (chunks.length == 4 && "resources".equals(chunks[2])) {
            path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[1]).extend(Resource.class,
                    chunks[3]);
        } else if (chunks.length == 4 && "metrics".equals(chunks[2])) {
            path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[1]).extend(Metric.class,
                    chunks[3]);
        } else if (chunks.length == 5 && "resources".equals(chunks[3])) {
            path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[1]).extend(Feed.class, chunks[2])
                    .extend(Resource.class, chunks[4]);
        } else if (chunks.length == 5 && "metrics".equals(chunks[3])) {
            path.extend(Tenant.class, chunks[0]).extend(Environment.class, chunks[1]).extend(Feed.class, chunks[2])
                    .extend(Metric.class, chunks[4]);
        }
        return path.get();
    }

    public static boolean isTenantEscapeAttempt(CanonicalPath origin, Path extension) {
        if (extension instanceof CanonicalPath) {
            return !((CanonicalPath) extension).ids().getTenantId().equals(origin.ids().getTenantId());
        } else {
            CanonicalPath target = ((RelativePath) extension).applyTo(origin);
            return !target.ids().getTenantId().equals(origin.ids().getTenantId());
        }
    }

    private static String shortenIfNeeded(CanonicalPath path) {
        String id = path.toString();

        if (id.length() > 250) {
            return UUID.nameUUIDFromBytes(id.getBytes()).toString();
        } else {
            return id;
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

    public boolean canUpdate(CanonicalPath path) {
        return safePermissionCheck(path, update(path.getSegment().getElementType()));
    }

    private Operation delete(Class<?> entityType) {
        return getOperation(entityType, OperationType.DELETE);
    }

    public boolean canDelete(CanonicalPath path) {
        return safePermissionCheck(path, delete(path.getSegment().getElementType()));
    }

    private Operation associate() {
        return operationsByType.get(Relationship.class).get(OperationType.ASSOCIATE);
    }

    public boolean canAssociateFrom(CanonicalPath path) {
        return safePermissionCheck(path, associate());
    }

    private Operation copy() {
        return operationsByType.get(Environment.class).get(OperationType.COPY);
    }

    public boolean canCopyEnvironment(CanonicalPath path) {
        return safePermissionCheck(path, copy());
    }

    private Operation getOperation(Class<?> cls, OperationType operationType) {
        Map<OperationType, Operation> ops = operationsByType.get(cls);
        if (ops == null) {
            throw new IllegalArgumentException("There is no " + operationType + " operation for elements of type " +
                    cls);
        }

        return ops.get(operationType);
    }

    private boolean safePermissionCheck(CanonicalPath path, Operation operation) {
        return safePermissionCheck(path.getSegment().getElementType(), path.getSegment().getElementId(),
                operation, getStableId(path));
    }

    private boolean safePermissionCheck(Class<?> entityType, String entityId, Operation operation, String stableId) {
        try {
            if (Tenant.class.equals(entityType)) {
                //make sure the tenant exists prior to checking perms on it
                if (!inventory.tenants().get(entityId).exists()) {
                    inventory.tenants().create(Tenant.Blueprint.builder().withId(entityId).build());
                }
            }
            if (RestApiLogger.LOGGER.isDebugEnabled()) {
                RestApiLogger.LOGGER.debug("Operation: " + operation + ", stableId: " + stableId);
            }
            return permissions.isAllowedTo(operation, stableId);
        } catch (Exception e) {
            RestApiLogger.LOGGER.securityCheckFailed(stableId, e);
            return false;
        }
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

        boolean under(CanonicalPath path) {
            String entityId = getStableId(path);
            return safePermissionCheck(createdType, path.getSegment().getElementId(), create(createdType), entityId);
        }
    }
}
