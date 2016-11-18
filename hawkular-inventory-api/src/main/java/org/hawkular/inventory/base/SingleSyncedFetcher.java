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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.hawkular.inventory.api.Data;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Log;
import org.hawkular.inventory.api.MetricTypes;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.Synced;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementBlueprintVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Hashes;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.SyncConfiguration;
import org.hawkular.inventory.api.model.SyncHash;
import org.hawkular.inventory.api.model.SyncRequest;
import org.hawkular.inventory.api.model.Syncable;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.ElementTypeVisitor;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.jboss.logging.Logger;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
abstract class SingleSyncedFetcher<BE, E extends Entity<B, U> & Syncable, B extends Entity.Blueprint,
        U extends Entity.Update>
        extends SingleEntityFetcher<BE, E, U>
        implements Synced.SingleWithRelationships<E, B, U> {

    private static final Logger DBG = Logger.getLogger(SingleSyncedFetcher.class);

    SingleSyncedFetcher(TraversalContext<BE, E> context) {
        super(context);
    }

    @Override public void synchronize(SyncRequest<B> syncRequest) {
        inTx(tx -> {
            BE root = tx.querySingle(context.select().get());

            E entity;

            if (root == null) {
                Mutator<BE, E, B, U, String> mutator = createMutator(tx);
                EntityAndPendingNotifications<BE, E> res =
                        mutator.doCreate(syncRequest.getInventoryStructure().getRoot(), tx);
                root = res.getEntityRepresentation();
                entity = res.getEntity();
            } else {
                entity = tx.convert(root, context.entityClass);
            }

            CanonicalPath rootPath = tx.extractCanonicalPath(root);

            InventoryStructure<B> currentStructure =
                    InventoryStructure.of(entity, context.inventory.keepTransaction(tx));

            //If we're using the deep search we need to load both trees in full to be able to determine what is synced.
            //If on the other hand we're syncing "shallowly", we can skip a lot of database access by computing the hash
            //only for the parts of the tree that has been changed in the incoming inventory structure.
            //This is the value of the hash loader which is used either if we're doing deep search or if we're syncing
            //everything - in which case we don't actually load anything from the database and just recompute the
            //hash of the whole new structure.
            Function<RelativePath, Hashes> hashLoader = rp -> null;

            InventoryStructure<B> newStructure;

            if (syncRequest.getConfiguration().getSyncedTypes().size() == SegmentType.values().length) {
                //special case if we are syncing everything - in this case we need no merging of the already persisted
                //parts of the tree into the new structure.
                DBG.debugf("Using the fast lane for full sync of %s", rootPath);

                newStructure = syncRequest.getInventoryStructure();
            } else {
                DBG.debugf("Merging persisted structure with the new data of %s", rootPath);
                newStructure =
                        mergeTree(currentStructure, syncRequest.getInventoryStructure(),
                                syncRequest.getConfiguration());
                DBG.debugf("Done merging the persisted and new data of %s", rootPath);

                if (!syncRequest.getConfiguration().isDeepSearch()) {
                    //Ok, so this is not deep search and we merged parts of the persisted tree into our new tree.
                    //So if we encounter such persisted node while computing the hashes, we actually don't need to
                    //compute its hash - it hasn't changed (because it's not in the incoming structure) and we know
                    //its hash already.
                    hashLoader = rp -> {
                        //just check if the node on the position has attachment - in that case it's been loaded from
                        //the database and we need not recompute its hash.
                        InventoryStructure.FullNode node = newStructure.getNode(rp);
                        if (node == null) {
                            return null;
                        }

                        Entity<?, ?> e = (Entity<?, ?>) node.getAttachment();
                        if (e == null) {
                            return null;
                        }

                        return Hashes.of(e);
                    };
                }
            }

            DBG.debugf("Computing sync tree of the merged structure of %s", rootPath);
            SyncHash.Tree newTree = SyncHash.treeOf(newStructure, rootPath, hashLoader);
            DBG.debugf("Done computing sync tree of the merged structure of %s", rootPath);

            DBG.debugf("Syncing the merged tree to the database state of root %s", rootPath);
            syncTrees(tx, rootPath, RelativePath.empty().get(), root, newTree, newStructure, currentStructure);
            DBG.debugf("Done syncing the merged tree and the database state of root %s", rootPath);

            return null;
        });
    }

    @Override public SyncHash.Tree treeHash() {
        return inTx(tx -> treeHashAndStructure(tx).getValue());
    }

    private InventoryStructure<B> mergeTree(InventoryStructure<B> currentTree, InventoryStructure<B> newTree,
                                    SyncConfiguration configuration) {
        if (configuration.isDeepSearch()) {
            return mergeDeepTree(currentTree, RelativePath.empty().get(),
                    InventoryStructure.Offline.copy(newTree).asBuilder(), configuration.getSyncedTypes()).build();
        } else {
            return mergeShallowTree(currentTree, RelativePath.empty().get(),
                    InventoryStructure.Offline.copy(newTree).asBuilder(), configuration.getSyncedTypes()).build();
        }
    }

    @SuppressWarnings("unchecked")
    private InventoryStructure.Builder<B> mergeShallowTree(InventoryStructure<?> currentTree, RelativePath treePath,
                                                           InventoryStructure.AbstractBuilder<?> newTree,
                                                           Set<SegmentType> mergedTypes) {

        DBG.debugf("Starting to merge shallow tree. Loading children under [%s]", treePath);
        try (Stream<InventoryStructure.FullNode> currentChildren = currentTree.getAllChildNodes(treePath)) {
            DBG.debugf("Done loading the children under [%s]", treePath);

            Set<Path.Segment> newChildPaths = newTree.getChildrenPaths();

            currentChildren.forEach(currentChild -> {
                SegmentType childType =
                        Inventory.types().byBlueprint(currentChild.getEntity().getClass()).getSegmentType();
                Path.Segment childPathSegment = new Path.Segment(childType, currentChild.getEntity().getId());

                if (newChildPaths.contains(childPathSegment)) {
                    mergeShallowTree(currentTree, treePath.modified().extend(childPathSegment).get(),
                            newTree.getChild(childPathSegment), mergedTypes);
                } else if (!mergedTypes.contains(childType)) {
                    newTree.addChild(currentChild.getEntity(), currentChild.getAttachment());
                }
            });

            DBG.debugf("Done merging shallow tree of [%s]", treePath);

            if (newTree instanceof InventoryStructure.Builder) {
                return (InventoryStructure.Builder<B>) newTree;
            } else {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private InventoryStructure.Builder<B> mergeDeepTree(InventoryStructure<?> currentTree, RelativePath treePath,
                                                        InventoryStructure.AbstractBuilder<?> newTree,
                                                        Set<SegmentType> mergedTypes) {
        DBG.debugf("Starting to merge shallow tree. Loading children under [%s]", treePath);
        try (Stream<InventoryStructure.FullNode> currentChildren = currentTree.getAllChildNodes(treePath)) {
            DBG.debugf("Done loading the children under [%s]", treePath);

            Set<Path.Segment> newChildPaths = newTree.getChildrenPaths();

            currentChildren.forEach(currentChild -> {
                SegmentType childType =
                        Inventory.types().byBlueprint(currentChild.getEntity().getClass()).getSegmentType();
                Path.Segment childPathSegment = new Path.Segment(childType, currentChild.getEntity().getId());

                if (newChildPaths.contains(childPathSegment)) {
                    mergeDeepTree(currentTree, treePath.modified().extend(childPathSegment).get(),
                            newTree.getChild(childPathSegment), mergedTypes);
                } else if (!mergedTypes.contains(childType)) {
                    newTree.addChild(currentChild.getEntity(), currentChild.getAttachment());
                    mergeDeepTree(currentTree, treePath.modified().extend(childPathSegment).get(),
                            newTree.getChild(childPathSegment), mergedTypes);
                }
            });

            DBG.debugf("Done merging shallow tree of [%s]", treePath);

            if (newTree instanceof InventoryStructure.Builder) {
                return (InventoryStructure.Builder<B>) newTree;
            } else {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void syncTrees(Transaction<BE> tx, CanonicalPath root, RelativePath pathFromRoot, BE oldElement,
                           SyncHash.Tree newTree, InventoryStructure<?> newStructure,
                           InventoryStructure<?> persistedStructure) {

        InventoryStructure.FullNode persistedNode = persistedStructure.getNode(pathFromRoot);
        String persistedHash = persistedNode == null ? null : ((Syncable) persistedNode.getAttachment()).getSyncHash();

        if (!Objects.equals(persistedHash, newTree.getHash())) {
            if (DBG.isDebugEnabled()) {
                DBG.debugf("Hashes differ on %s. Syncing...", pathFromRoot.applyTo(root));
            }

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

            Set<SyncHash.Tree> updates = new HashSet<>();

            for (InventoryStructure.EntityType type : InventoryStructure.EntityType.values()) {
                try (Stream<InventoryStructure.FullNode> oldChildren = persistedStructure.getChildNodes(pathFromRoot,
                        type.elementType)) {
                    if (oldChildren == null) {
                        continue;
                    }

                    oldChildren.forEach(oldChild -> {
                        Entity<?, ?> oldEntity = (Entity<?, ?>) oldChild.getAttachment();
                        CanonicalPath childCp = oldEntity.getPath();
                        SyncHash.Tree newChild = newTree.getChild(childCp.getSegment());

                        if (newChild == null) {
                            //ok, this entity is no longer in the new structure
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
                            updates.add(newChild);
                        }
                    });
                }
            }

            //now create the new children
            unprocessedChildren.forEach(c -> create(tx, root, c, newStructure));

            //and finally updates...
            for (SyncHash.Tree update : updates) {
                CanonicalPath childCp = update.getPath().applyTo(root);
                try {
                    BE child = tx.find(childCp);
                    syncTrees(tx, root, childCp.relativeTo(root), child, update, newStructure, persistedStructure);
                } catch (ElementNotFoundException ex) {
                    Log.LOGGER.debug("Failed to find entity on " + childCp + " that we thought was there. Never mind " +
                            "though, we can just create it again.", ex);
                    create(tx, root, update, newStructure);
                }
            }
        } else {
            if (DBG.isDebugEnabled()) {
                DBG.debugf("Hashes match on %s. Sync on its subtree done.", pathFromRoot.applyTo(root));
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

    private Map.Entry<InventoryStructure<B>, SyncHash.Tree> treeHashAndStructure(Transaction<BE> tx) {
        BE root = tx.querySingle(context.select().get());
        E entity = tx.convert(root, context.entityClass);

        SyncHash.Tree.Builder bld = SyncHash.Tree.builder();
        InventoryStructure.Builder<B> structBld = InventoryStructure.of(Inventory.asBlueprint(entity));

        bld.withPath(RelativePath.empty().get()).withHash(entity.getSyncHash());

        //the closure is returned in a breadth-first manner
        Iterator<BE> closure = tx.getTransitiveClosureOver(root, outgoing, contains.name());

        if (closure.hasNext()) {
            Function<BE, Entity<? extends Entity.Blueprint, ?>> convert =
                    e -> (Entity<Entity.Blueprint, ?>) tx.convert(e, tx.extractType(e));
            Stream<BE> st = StreamSupport.stream(Spliterators.spliteratorUnknownSize(closure, 0), false);
            Iterator<Entity<? extends Entity.Blueprint, ?>> entities = st.map(convert).iterator();

            buildChildTree(tx, entity.getPath(), singletonList(bld), singletonList(structBld), new ArrayList<>(),
                    new ArrayList<>(), entities.next(), entities);
        }

        return new SimpleImmutableEntry<>(structBld.build(), bld.build());
    }

    @SuppressWarnings("unchecked")
    private void buildChildTree(Transaction<BE> tx, CanonicalPath root,
                                List<? extends SyncHash.Tree.AbstractBuilder<?>> possibleTreeParents,
                                List<? extends InventoryStructure.AbstractBuilder<?>> possibleStructParents,
                                List<SyncHash.Tree.ChildBuilder<?>> currentTreeRow,
                                List<InventoryStructure.ChildBuilder<?>> currentStructRow,
                                Entity<? extends Entity.Blueprint, ?> currentElement,
                                Iterator<Entity<? extends Entity.Blueprint, ?>> nextElements) {

        Consumer<Entity<? extends Entity.Blueprint, ?>> decider = e -> {
            if (!(e instanceof Syncable)) {
                return;
            }

            CanonicalPath currentPath = e.getPath();
            RelativePath relativeCurrentPath = currentPath.relativeTo(root);
            SyncHash.Tree.AbstractBuilder<?> treeParent = findTreeParent(possibleTreeParents, relativeCurrentPath);
            InventoryStructure.AbstractBuilder<?> structParent = findStructParent(possibleStructParents, relativeCurrentPath);

            if ((treeParent == null && structParent != null) || (treeParent != null && structParent == null)) {
                throw new IllegalStateException(
                        "Inconsistent tree hash and inventory structure builders while computing the child tree of" +
                                " entity " + root + ". This is a bug.");
            }

            if (treeParent == null) {
                //ok, our parents don't have this child. Seems like we need to start a new row.

                //first end our parents
                possibleTreeParents.forEach(p -> {
                    if (p instanceof SyncHash.Tree.ChildBuilder) {
                        ((SyncHash.Tree.ChildBuilder<?>) p).endChild();
                    }
                });

                possibleStructParents.forEach(p -> {
                    if (p instanceof InventoryStructure.ChildBuilder) {
                        ((InventoryStructure.ChildBuilder<?>) p).end();
                    }
                });

                //and start processing the next row
                buildChildTree(tx, root, currentTreeRow, currentStructRow, new ArrayList<>(), new ArrayList<>(), e,
                        nextElements);
            } else {
                SyncHash.Tree.ChildBuilder<?> childTreeBuilder = treeParent.startChild();
                childTreeBuilder.withHash(((Syncable) e).getSyncHash());
                childTreeBuilder.withPath(relativeCurrentPath);
                currentTreeRow.add(childTreeBuilder);

                InventoryStructure.ChildBuilder<?> childStructBuilder =
                        structParent.startChild(Inventory.asBlueprint(e));
                currentStructRow.add(childStructBuilder);
            }
        };

        decider.accept(currentElement);
        while (nextElements.hasNext()) {
            decider.accept(nextElements.next());
        }

        for (SyncHash.Tree.ChildBuilder<?> cb : currentTreeRow) {
            cb.endChild();
        }

        for (InventoryStructure.ChildBuilder<?> cb : currentStructRow) {
            cb.end();
        }
    }

    private static SyncHash.Tree.AbstractBuilder<?>
    findTreeParent(List<? extends SyncHash.Tree.AbstractBuilder<?>> possibleParents, RelativePath childPath) {
        return possibleParents.stream()
                .filter(p -> p.getPath().isParentOf(childPath) && p.getPath().getDepth() == childPath.getDepth() - 1)
                .findAny()
                .orElse(null);
    }

    private static InventoryStructure.AbstractBuilder<?>
    findStructParent(List<? extends InventoryStructure.AbstractBuilder<?>> possibleParents, RelativePath childPath) {
        return possibleParents.stream()
                .filter(p -> p.getPath().isParentOf(childPath) && p.getPath().getDepth() == childPath.getDepth() - 1)
                .findAny()
                .orElse(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Mutator<BE, E, B, U, String> createMutator(Transaction<BE> tx) {
        return (Mutator<BE, E, B, U, String>) ElementTypeVisitor.accept(
                Inventory.types().byElement(context.entityClass).getSegmentType(),
                new ElementTypeVisitor<Mutator<BE, ?, ?, ?, String>, Void>() {
                    @Override public Mutator<BE, ?, ?, ?, String> visitTenant(Void parameter) {
                        return new BaseTenants.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitEnvironment(Void parameter) {
                        return new BaseEnvironments.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitFeed(Void parameter) {
                        return new BaseFeeds.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitMetric(Void parameter) {
                        return new BaseMetrics.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitMetricType(Void parameter) {
                        return new BaseMetricTypes.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitResource(Void parameter) {
                        return new BaseResources.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitResourceType(Void parameter) {
                        return new BaseResourceTypes.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitRelationship(Void parameter) {
                        throw new IllegalArgumentException(
                                "Cannot synchronize relationships. This codepath should have never been allowed and" +
                                        " is a bug.");
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitData(Void parameter) {
                        BE parent = tx.querySingle(context.previous.sourcePath);
                        if (parent == null) {
                            throw new EntityNotFoundException(Query.filters(context.previous.sourcePath));
                        }

                        Class parentType = tx.extractType(parent);

                        return new BaseData.ReadWrite(context.previous, dataRoleType(parentType),
                                dataModificationChecks(parentType));
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitOperationType(Void parameter) {
                        return new BaseOperationTypes.ReadWrite(context.previous);
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitMetadataPack(Void parameter) {
                        throw new IllegalStateException(
                                "Cannot synchronize metadata packs. This codepath should have never been allowed and" +
                                        " is a bug.");
                    }

                    @Override public Mutator<BE, ?, ?, ?, String> visitUnknown(Void parameter) {
                        throw new IllegalStateException(
                                "This is a bug! Unhandled type of entity for synchronization: " + context.entityClass);
                    }

                    private <E extends AbstractElement<B, U>, B extends Blueprint, U extends AbstractElement.Update>
                    Class<? extends DataRole> dataRoleType(Class<E> parentType) {
                        SegmentType st = Inventory.types().byElement(parentType).getSegmentType();
                        switch (st) {
                            case r:
                                return DataRole.Resource.class;
                            case rt:
                                return DataRole.ResourceType.class;
                            case ot:
                                return DataRole.OperationType.class;
                            default:
                                throw new IllegalStateException("This is a bug! Unhandled data role class" +
                                        " for type " + context.entityClass);
                        }
                    }

                    private <E extends AbstractElement<B, U>, B extends Blueprint, U extends AbstractElement.Update>
                    BaseData.DataModificationChecks<BE> dataModificationChecks(Class<E> parentType) {
                        SegmentType st = Inventory.types().byElement(parentType).getSegmentType();
                        switch (st) {
                            case r:
                                return BaseData.DataModificationChecks.none();
                            case rt:
                                return new BaseResourceTypes.ResourceTypeDataModificationChecks<>(context.previous);
                            case ot:
                                return new BaseOperationTypes.OperationTypeDataModificationChecks<>(context.previous);
                            default:
                                throw new IllegalStateException("This is a bug! Unhandled data modification checks" +
                                        " for type " + context.entityClass);
                        }
                    }
                }, null);
    }
}
