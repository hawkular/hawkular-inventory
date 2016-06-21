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

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResolvableToSingleWithRelationships;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.DataEntity;
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
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.16.0
 */
public final class Utils {
    private Utils() {

    }

    public static SegmentType getSegmentTypeFromSimpleName(String simpleName) {
        String name = simpleName;

        //this is the exception that we use for readability reasons...
        if ("data".equals(simpleName)) {
            return SegmentType.d;
        }

        //fast track
        SegmentType st = SegmentType.fastValueOf(name);
        if (st != null) {
            return st;
        }

        //try with simple name, ignore the first letter in lower case
        if (Character.isLowerCase(name.charAt(0))) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        for (SegmentType seg : SegmentType.values()) {
            if (seg.getSimpleName().equals(name)) {
                return seg;
            }
        }

        //try with the whole name lowercase
        for (SegmentType seg : SegmentType.values()) {
            if (seg.getSimpleName().toLowerCase().equals(name)) {
                return seg;
            }
        }

        throw new IllegalArgumentException("Could not find the entity type corresponding to '" + simpleName + "'.");
    }

    @SuppressWarnings("unchecked")
    public static AbstractElement<?, ?> createUnder(Inventory inventory, CanonicalPath parent, SegmentType childType,
                                                    Object childBlueprint) {
        ResolvableToSingle<?, ?> access = inventory.inspect(parent, ResolvableToSingle.class);

        switch (childType) {
            case d:
                if (access instanceof Data.Container) {
                    return ((Data.Container<Data.ReadWrite<?>>) access).data()
                            .create((DataEntity.Blueprint) childBlueprint).entity();
                }
                break;
            case e:
                if (access instanceof Environments.Container) {
                    return ((Environments.Container<Environments.ReadWrite>) access).environments()
                            .create((Environment.Blueprint) childBlueprint).entity();
                }
                break;
            case f:
                if (access instanceof Feeds.Container) {
                    return ((Feeds.Container<Feeds.ReadWrite>) access).feeds()
                            .create((Feed.Blueprint) childBlueprint).entity();
                }
                break;
            case m:
                if (access instanceof Metrics.Container) {
                    return ((Metrics.Container<Metrics.ReadWrite>) access).metrics()
                            .create((Metric.Blueprint) childBlueprint).entity();
                }
                break;
            case mp:
                if (access instanceof Tenants.Single) {
                    return ((Tenants.Single) access).metadataPacks()
                            .create((MetadataPack.Blueprint) childBlueprint).entity();
                }
                break;
            case mt:
                if (access instanceof MetricTypes.Container) {
                    return ((MetricTypes.Container<MetricTypes.ReadWrite>) access).metricTypes()
                            .create((MetricType.Blueprint) childBlueprint).entity();
                }
                break;
            case ot:
                if (access instanceof OperationTypes.Container) {
                    return ((OperationTypes.Container<OperationTypes.ReadWrite>) access).operationTypes()
                            .create((OperationType.Blueprint) childBlueprint).entity();
                }
                break;
            case r:
                if (access instanceof Resources.Container) {
                    return ((Resources.Container<Resources.ReadWrite>) access).resources()
                            .create((Resource.Blueprint) childBlueprint).entity();
                }
                break;
            case rl:
                if (access instanceof ResolvableToSingleWithRelationships) {
                    return createRelationship((ResolvableToSingleWithRelationships<?, ?>) access, childBlueprint);
                }
                break;
            case rt:
                if (access instanceof ResourceTypes.Container) {
                    return ((ResourceTypes.Container<ResourceTypes.ReadWrite>) access).resourceTypes()
                            .create((ResourceType.Blueprint) childBlueprint).entity();
                }
                break;
        }

        throw new IllegalArgumentException("Cannot create a child of type '" + childType + "' under parent '"
                + parent + "'.");
    }

    private static Relationship createRelationship(ResolvableToSingleWithRelationships<?, ?> access,
                                                   Object blueprint) {

        Relationship.Blueprint bl = (Relationship.Blueprint) blueprint;
        return access.relationships(bl.getDirection()).linkWith(bl.getName(), bl.getOtherEnd(), bl.getProperties())
                .entity();
    }

    public static Path getCanonicalLinkPath(AbstractElement<?, ?> res) {
        return res instanceof Relationship
                ? res.getPath().toRelativePath() //remove the leading /
                : res.getPath().toRelativePath().slide(1, 0); //remove the tenant id from the path
    }
}
