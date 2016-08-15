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
package org.hawkular.inventory.base;

import static java.util.Collections.singletonList;

import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Log;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Synced;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementBlueprintVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.IdentityHashable;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.SyncHash;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.ElementTypeVisitor;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
abstract class SingleSyncedFetcher<BE, E extends Entity<B, U> & IdentityHashable, B extends Entity.Blueprint,
        U extends Entity.Update>
        extends SingleEntityFetcher<BE, E, U>
        implements Synced.SingleWithRelationships<E, B, U> {

    SingleSyncedFetcher(TraversalContext<BE, E> context) {
        super(context);
    }

    @Override public void synchronize(InventoryStructure<B> newStructure) {
        inTx(tx -> {
            BE root = tx.querySingle(context.select().get());

            if (root == null) {
                throw new IllegalArgumentException("The root element must exist before its inventory structure can be" +
                        " synchronized.");
            }

            CanonicalPath rootPath = tx.extractCanonicalPath(root);

            //getting the whole tree is not the most efficient thing but it makes things so much simpler ;)
            SyncHash.Tree currentTree = treeHash(tx);
            SyncHash.Tree newTree = SyncHash.treeOf(newStructure, tx.extractCanonicalPath(root));

            syncTrees(tx, rootPath, root, currentTree, newTree, newStructure);

            return null;
        });
    }

    @Override public SyncHash.Tree treeHash() {
        return inTx(this::treeHash);
    }

    @SuppressWarnings("unchecked")
    private void syncTrees(Transaction<BE> tx, CanonicalPath root, BE oldElement, SyncHash.Tree oldTree,
                           SyncHash.Tree newTree, InventoryStructure<?> newStructure) {

        if (!oldTree.getHash().equals(newTree.getHash())) {
            //we only need to do something if the hashes don't match. If they do, it means this entity and its whole
            //subtree is equivalent. But it isn't because the hashes differ, so...
            Blueprint newState = newStructure.get(newTree.getPath());
            Entity.Update entityUpdate = updateFromBlueprint(newState);

            Inventory inv = context.inventory.keepTransaction(tx);

            //update the current element - use the full API call so that all checks are enforced
            inv.inspect(tx.extractCanonicalPath(oldElement), ResolvableToSingle.class).update(entityUpdate);

            //now look through the old and new children and make old match new
            //it is important to make sure that resource or metric types are create prior to resources or metrics
            //we can exploit the InventoryStructure.EntityType enum which is ordered with this in mind.
            Set<SyncHash.Tree> unprocessedChildren = sortByType(newTree.getChildren());

            Map<SyncHash.Tree, SyncHash.Tree> updates = new HashMap<>();

            //again, it is important to create types before to resources or metrics, etc.
            Map<InventoryStructure.EntityType, Set<SyncHash.Tree>> childrenByType = splitByType(oldTree
                    .getChildren());

            for (InventoryStructure.EntityType type : InventoryStructure.EntityType.values()) {
                Set<SyncHash.Tree> oldChildren = childrenByType.get(type);
                if (oldChildren == null) {
                    continue;
                }

                for (SyncHash.Tree oldChild : oldChildren) {
                    SyncHash.Tree newChild = newTree.getChild(oldChild.getPath().getSegment());

                    if (newChild == null) {
                        //ok, this entity is no longer in the new structure
                        CanonicalPath childCp = oldChild.getPath().applyTo(root);
                        try {
                            //delete using a normal API so that all checks are run
                            inv.inspect(childCp, ResolvableToSingle.class).delete();
                        } catch (EntityNotFoundException e) {
                            Log.LOGGER.debug("Failed to find a child to be deleted on canonical path " + childCp
                                    + ". Ignoring this since we were going to delete it anyway.", e);
                        }
                    } else {
                        //kewl, we have a matching child that we need to sync
                        //let's just postpone the actual update until the end of the method, just in case Java gets
                        //tail-call optimization ;)
                        unprocessedChildren.remove(newChild);
                        updates.put(oldChild, newChild);
                    }
                }
            }

            //now create the new children
            unprocessedChildren.forEach(c -> create(tx, root, c, newStructure));

            //and finally updates...
            for (Map.Entry<SyncHash.Tree, SyncHash.Tree> e : updates.entrySet()) {
                SyncHash.Tree oldChild = e.getKey();
                SyncHash.Tree update = e.getValue();
                CanonicalPath childCp = update.getPath().applyTo(root);
                try {
                    BE child = tx.find(childCp);
                    syncTrees(tx, root, child, oldChild, update, newStructure);
                } catch (ElementNotFoundException ex) {
                    Log.LOGGER.debug("Failed to find entity on " + childCp + " that we thought was there. Never mind " +
                            "though, we can just create it again.", ex);
                    create(tx, root, update, newStructure);
                }
            }
        }
    }

    private Set<SyncHash.Tree> sortByType(Collection<SyncHash.Tree> col) {
        Set<SyncHash.Tree> set = new TreeSet<>((a, b) -> {
            InventoryStructure.EntityType aType =
                    InventoryStructure.EntityType.of(a.getPath().getSegment().getElementType());
            InventoryStructure.EntityType bType =
                    InventoryStructure.EntityType.of(b.getPath().getSegment().getElementType());

            int ret = aType.ordinal() - bType.ordinal();

            if (ret == 0) {
                //this is actually not important.. we only need to make sure we have a total ordering and that
                //the entities are sorted by their type..
                ret = a.getHash().compareTo(b.getHash());
            }

            return ret;
        });
        set.addAll(col);

        return set;
    }

    @SuppressWarnings("unchecked")
    private void create(Transaction<BE> tx, CanonicalPath root, SyncHash.Tree tree,
                        InventoryStructure<?> newStructure) {
        Inventory inv = context.inventory.keepTransaction(tx);
        CanonicalPath childCp = tree.getPath().applyTo(root);
        @SuppressWarnings("unchecked")
        ResolvableToSingle<?, ?> parentAccess = inv.inspect(childCp.up(), ResolvableToSingle.class);

        Blueprint blueprint = newStructure.get(tree.getPath());

        //the blueprint might actually be null, because the hash tree computes hash using some "virtual nodes"
        //for data entities - i.e. if a resource doesn't have a configuration an empty "virtual" config is used for
        //hash computation.
        //We'd see these nodes here, but there's no need to create them.
        if (blueprint == null) {
            return;
        }

        childCp.getSegment().accept(new ElementTypeVisitor.Simple<Void, Void>() {
            @Override public Void visitFeed(Void parameter) {
                ((Feeds.Container<Feeds.ReadWrite>) parentAccess).feeds().create((Feed.Blueprint) blueprint);
                return null;
            }

            @Override public Void visitMetric(Void parameter) {
                ((Metrics.Container<Metrics.ReadWrite>) parentAccess).metrics()
                        .create((Metric.Blueprint) blueprint);
                return null;
            }

            @Override public Void visitMetricType(Void parameter) {
                ((MetricTypes.Container<MetricTypes.ReadWrite>) parentAccess).metricTypes()
                        .create((MetricType.Blueprint) blueprint);
                return null;
            }

            @Override public Void visitResource(Void parameter) {
                ((Resources.Container<Resources.ReadWrite>) parentAccess).resources()
                        .create((Resource.Blueprint) blueprint);
                return null;
            }

            @Override public Void visitResourceType(Void parameter) {
                ((ResourceTypes.Container<ResourceTypes.ReadWrite>) parentAccess).resourceTypes()
                        .create((ResourceType.Blueprint) blueprint);
                return null;
            }

            @SuppressWarnings("rawtypes")
            @Override public Void visitData(Void parameter) {
                ((Data.Container<Data.ReadWrite>) parentAccess).data()
                        .create((DataEntity.Blueprint<?>) blueprint);
                return null;
            }

            @Override public Void visitOperationType(Void parameter) {
                ((OperationTypes.Container<OperationTypes.ReadWrite>) parentAccess).operationTypes()
                        .create((OperationType.Blueprint) blueprint);
                return null;
            }
        }, null);

        for (SyncHash.Tree child : tree.getChildren()) {
            create(tx, root, child, newStructure);
        }
    }

    private Entity.Update updateFromBlueprint(Blueprint blueprint) {
        return blueprint.accept(new ElementBlueprintVisitor.Simple<Entity.Update, Void>() {
            @Override public DataEntity.Update visitData(DataEntity.Blueprint<?> data, Void parameter) {
                return fillCommon(DataEntity.Update.builder(), data).withValue(data.getValue()).build();
            }

            @Override public Feed.Update visitFeed(Feed.Blueprint feed, Void parameter) {
                return fillCommon(Feed.Update.builder(), feed).build();
            }

            @Override public Metric.Update visitMetric(Metric.Blueprint metric, Void parameter) {
                return fillCommon(Metric.Update.builder(), metric).withInterval(metric.getCollectionInterval()).build();
            }

            @Override public MetricType.Update visitMetricType(MetricType.Blueprint type, Void parameter) {
                return fillCommon(MetricType.Update.builder(), type).withInterval(type.getCollectionInterval()).build();
            }

            @Override public OperationType.Update visitOperationType(OperationType.Blueprint operationType,
                                                                     Void parameter) {
                return fillCommon(OperationType.Update.builder(), operationType).build();
            }

            @Override public Resource.Update visitResource(Resource.Blueprint resource, Void parameter) {
                return fillCommon(Resource.Update.builder(), resource).build();
            }

            @Override public ResourceType.Update visitResourceType(ResourceType.Blueprint type, Void parameter) {
                return fillCommon(ResourceType.Update.builder(), type).build();
            }

            private <UU extends Entity.Update, Bld extends Entity.Update.Builder<UU, Bld>>
            Bld fillCommon(Bld bld, Entity.Blueprint bl) {
                if (bl.getProperties() != null) {
                    bld.withProperties(bl.getProperties());
                }
                return bld.withName(bl.getName());
            }
        }, null);
    }

    private SyncHash.Tree treeHash(Transaction<BE> tx) {
        BE root = tx.querySingle(context.select().get());
        //the closure is returned in a breadth-first manner
        Iterator<BE> closure = tx.getTransitiveClosureOver(root, outgoing, contains.name());

        SyncHash.Tree.Builder bld = SyncHash.Tree.builder();
        bld.withPath(RelativePath.empty().get()).withHash(tx.extractSyncHash(root));

        if (closure.hasNext()) {
            CanonicalPath rootPath = tx.extractCanonicalPath(root);
            List<SyncHash.Tree.ChildBuilder<?>> children = new ArrayList<>();
            buildChildTree(tx, rootPath, singletonList(bld), children, closure.next(), closure);
        }

        return bld.build();
    }

    @SuppressWarnings("unchecked")
    private void buildChildTree(Transaction<BE> tx, CanonicalPath root,
                                List<? extends SyncHash.Tree.AbstractBuilder<?>> possibleParents,
                        List<SyncHash.Tree.ChildBuilder<?>> currentRow,
                        BE currentElement, Iterator<BE> nextElements) {

        Consumer<BE> decider = e -> {
            CanonicalPath currentPath = tx.extractCanonicalPath(e);
            RelativePath relativeCurrentPath = currentPath.relativeTo(root);
            SyncHash.Tree.AbstractBuilder<?> parent = findParent(possibleParents, relativeCurrentPath);
            if (parent == null) {
                //ok, our parents don't have this child. Seems like we need to start a new row.

                //first end our parents
                possibleParents.forEach(p -> {
                    if (p instanceof SyncHash.Tree.ChildBuilder) {
                        ((SyncHash.Tree.ChildBuilder<?>) p).endChild();
                    }
                });

                //and start processing the next row
                buildChildTree(tx, root, currentRow, new ArrayList<>(), e, nextElements);
            } else {
                SyncHash.Tree.ChildBuilder<?> childBuilder = parent.startChild();
                childBuilder.withHash(tx.extractSyncHash(e));
                childBuilder.withPath(relativeCurrentPath);
                currentRow.add(childBuilder);
            }
        };

        decider.accept(currentElement);
        while (nextElements.hasNext()) {
            decider.accept(nextElements.next());
        }

        for (SyncHash.Tree.ChildBuilder<?> cb : currentRow) {
            cb.endChild();
        }
    }

    private SyncHash.Tree.AbstractBuilder<?>
    findParent(List<? extends SyncHash.Tree.AbstractBuilder<?>> possibleParents, RelativePath childPath) {
        return possibleParents.stream()
                .filter(p -> p.getPath().isParentOf(childPath) && p.getPath().getDepth() == childPath.getDepth() - 1)
                .findAny()
                .orElse(null);
    }

    private static Map<InventoryStructure.EntityType, Set<SyncHash.Tree>>
    splitByType(Collection<SyncHash.Tree> group) {

        EnumMap<InventoryStructure.EntityType, Set<SyncHash.Tree>> ret =
                new EnumMap<>(InventoryStructure.EntityType.class);

        group.forEach(t -> {
            SegmentType elementType = t.getPath().getSegment().getElementType();
            InventoryStructure.EntityType entityType = InventoryStructure.EntityType.of(elementType);
            Set<SyncHash.Tree> siblings = ret.get(entityType);
            if (siblings == null) {
                siblings = new HashSet<>();
                ret.put(entityType, siblings);
            }

            siblings.add(t);
        });

        return ret;
    }
}
