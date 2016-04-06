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

import static java.util.stream.Collectors.toList;

import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.identityHashChanged;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.TreeTraversal;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.IdentityHash;
import org.hawkular.inventory.api.model.IdentityHashable;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;

/**
 * Takes care of defining the pre-commit actions based on the set of entities modified within a single transaction.
 * <p>
 * <p>Most importantly it takes care of minimizing the number of computations needed to compute identity hashes of
 * entities. To compute identity hash of a parent resource, one needs to compute the hashes of all its children so if
 * also the child is modified, it only is necessary to compute the tree of the identity hashes of the parent and pick
 * the hashes of the modified children from that.
 *
 * @author Lukas Krejci
 * @since 0.13.0
 */
public class BasePreCommit<BE> implements Transaction.PreCommit<BE> {

    /**
     * Notifications about entities that are not hashable and therefore need no processing
     */
    private final List<EntityAndPendingNotifications<BE, ?>> nonHashedChanges = new ArrayList<>();
    /**
     * Pre-commit actions explicitly requested by callers
     */
    private final List<Consumer<Transaction<BE>>> explicitActions = new ArrayList<>();
    /**
     * The notifications of the processed changes
     */
    private final List<EntityAndPendingNotifications<BE, ?>> correctedChanges = new ArrayList<>();
    /**
     * Keeps track of the work needed to be done.
     */
    private final ProcessingTree<BE> processingTree = new ProcessingTree<>();

    private Inventory inventory;
    private Transaction<BE> tx;

    /**
     * Pre-commit actions that reset the identity hash
     */
    private Consumer<Transaction<BE>> correctiveAction;

    @Override public void initialize(Inventory inventory, Transaction<BE> tx) {
        this.inventory = inventory;
        this.tx = tx;
    }

    @Override public void reset() {
        nonHashedChanges.clear();
        explicitActions.clear();
        correctiveAction = null;
        correctedChanges.clear();
        processingTree.clear();
    }

    @Override public List<EntityAndPendingNotifications<BE, ?>> getFinalNotifications() {
        List<EntityAndPendingNotifications<BE, ?>> ret = new ArrayList<>(nonHashedChanges);
        ret.addAll(correctedChanges);

        return ret;
    }

    @Override public void addAction(Consumer<Transaction<BE>> action) {
        explicitActions.add(action);
    }

    @Override public List<Consumer<Transaction<BE>>> getActions() {
        process();

        List<Consumer<Transaction<BE>>> ret;

        if (correctiveAction != null) {
            ret = new ArrayList<>(explicitActions);
            ret.add(correctiveAction);
        } else {
            ret = explicitActions;
        }

        return ret;
    }

    @Override public void addNotifications(EntityAndPendingNotifications<BE, ?> element) {
        if (needsProcessing(element)) {
            processingTree.add(element);
        } else {
            nonHashedChanges.add(element);
        }
    }

    @Override public void addProcessedNotifications(EntityAndPendingNotifications<BE, ?> element) {
        nonHashedChanges.add(element);
    }

    private boolean needsProcessing(EntityAndPendingNotifications<?, ?> element) {
        return element.getEntity() instanceof IdentityHashable;
    }

    private void process() {
        //walk the processing tree and process the top most
        List<ProcessingTree<BE>> identityHashRoots = new ArrayList<>();

        processingTree.dfsTraversal(t -> {
            if (t.needsResolution()) {
                identityHashRoots.add(t);
                return false;
            } else {
                //look for resolution-needing children
                return true;
            }
        });

        if (identityHashRoots.isEmpty()) {
            correctiveAction = null;
            return;
        }

        correctiveAction = t -> {
            for (ProcessingTree<BE> root : identityHashRoots) {
                try {
                    root.loadFrom(tx);
                } catch (ElementNotFoundException e) {
                    //ok, we're inside a delete and the root entity no longer exists... bail out quickly...
                    break;
                }
                @SuppressWarnings("unchecked")
                Entity<? extends Entity.Blueprint, ?> e = (Entity<? extends Entity.Blueprint, ?>) root.element;

                IdentityHash.Tree treeHash = IdentityHash.treeOf(InventoryStructure.of(e, inventory));

                correctChanges(treeHash, root);
            }
        };
    }

    private void correctChanges(IdentityHash.Tree treeHash, ProcessingTree<BE> changesTree) {
        if (changesTree.needsResolution()) {
            try {
                changesTree.loadFrom(tx);
            } catch (ElementNotFoundException e) {
                throw new EntityNotFoundException(Query.filters(Query.to(changesTree.cp)));
            }

            Entity<?, ?> e = cloneWithHash((Entity<?, ?>) changesTree.element, treeHash.getHash());

            List<Notification<?, ?>> ns = changesTree.notifications.stream().map(n -> cloneWithNewEntity(n, e))
                    .collect(toList());

            if (processDeletion(changesTree, e, ns)) {
                return;
            }

            //check if there is a create or update notification if necessary
            String origIdentityHash = ((IdentityHashable) changesTree.element).getIdentityHash();
            if (!Objects.equals(origIdentityHash, treeHash.getHash())) {
                //check if the notifications contain an update or create
                Optional<Notification<?, ?>> createNotif = ns.stream()
                        .filter(n -> n.getAction() == created() && n.getValue().equals(changesTree.element))
                        .findAny();

                if (!createNotif.isPresent()) {
                    if (origIdentityHash == null) {
                        ns.add(new Notification<>(changesTree.element, changesTree.element, created()));
                    } else {
                        IdentityHashable el = (IdentityHashable) changesTree.element;
                        ns.add(new Notification<>(el, el, identityHashChanged()));
                    }
                }

                //now also actually update the element in inventory with the new hash
                tx.updateIdentityHash(changesTree.representation, treeHash.getHash());
            }

            //set the notifications to emit
            correctedChanges.add(new EntityAndPendingNotifications<BE, AbstractElement<?, ?>>(
                    changesTree.representation, e, ns));

            //traverse the children to reset their identity hashes
            ArrayList<IdentityHash.Tree> treeChildren = new ArrayList<>(treeHash.getChildren());
            Collections.sort(treeChildren, (a, b) ->
                    a.getPath().getSegment().getElementId().compareTo(b.getPath().getSegment().getElementId()));

            ArrayList<ProcessingTree<BE>> processedChildren = new ArrayList<>(changesTree.children);
            Collections.sort(processedChildren, (a, b) ->
                    a.path.getElementId().compareTo(b.path.getElementId()));


            for (int i = 0, j = 0; i < processedChildren.size() && j < treeChildren.size();) {
                ProcessingTree<BE> p = processedChildren.get(i);
                IdentityHash.Tree h = treeChildren.get(j);

                int cmp = p.path.getElementId().compareTo(h.getPath().getSegment().getElementId());
                if (cmp == 0) {
                    //we found 2 equal elements, let's compare and continue
                    correctChanges(h, p);
                    i++;
                    j++;
                } else if (cmp > 0) {
                    //the processing tree element is "greater than" the tree hash element. This means that in our
                    //processing tree we don't have all elements that contribute to the tree hash. This is ok - not
                    //all children of an entity need to be updated for the tree hash to change.
                    //
                    //We don't do any comparisons here, just increase the index in the tree hash, but NOT our
                    //processing tree index.
                    j++;
                } else {
                    //the processing tree element is "less" than the tree hash element, which means that there can be
                    //other processing tree elements after this one that are equal to the curent tree hash element.
                    //
                    //Let's just increment our processing tree index to try and find such element.
                    i++;

                    if (p.needsResolution()) {
                        //if p was deleted, it's ok, but if it wasn't we have a serious problem - we found a change
                        //contributing to the identity hash of some parent, but the computed treeHash that should
                        //reflect the current state of inventory doesn't have a reference to the changed entity
                        if (!processDeletion(p, (Entity<?, ?>) p.element, p.notifications)) {
                            throw new IllegalStateException(
                                    "Entity on path " + p.element.getPath() + " requires identity hash re-computation" +
                                            " but was not found contributing to a tree hash of a parent. This is" +
                                            " inconsistency in the inventory model and a bug.");
                        }
                    }
                }
            }
        }
    }

    private boolean processDeletion(ProcessingTree<BE> changesTree, Entity<?, ?> e,
                                    List<Notification<?, ?>> ns) {
        Optional<Notification<?, ?>> deleteNotification = ns.stream()
                .filter(n -> n.getAction() == Action.deleted() && n.getValue().equals(changesTree.element))
                .findAny();


        if (deleteNotification.isPresent()) {
            //just emit what the caller wanted and don't bother any longer
            correctedChanges.add(new EntityAndPendingNotifications<BE, AbstractElement<?, ?>>(
                    changesTree.representation, e, ns));

            IdentityHash.Tree emptyTree = IdentityHash.Tree.builder().build();
            changesTree.dfsTraversal(pt -> {
                correctChanges(emptyTree, pt);
                return true;
            });

            return true;
        }
        return false;
    }

    private Entity<?, ?> cloneWithHash(Entity<?, ?> entity, String identityHash) {
        return entity.accept(new ElementVisitor.Simple<Entity<?, ?>, Void>() {
            @Override protected Entity<?, ?> defaultAction() {
                if (entity instanceof IdentityHashable) {
                    throw new IllegalStateException("Unhandled IdentityHashable type: " + entity);
                }

                return entity;
            }

            @Override public Entity<?, ?> visitData(DataEntity data, Void parameter) {
                return new DataEntity(data.getPath(), data.getValue(), identityHash, data.getProperties());
            }

            @Override public Entity<?, ?> visitFeed(Feed feed, Void parameter) {
                return new Feed(feed.getName(), feed.getPath(), identityHash, feed.getProperties());
            }

            @Override public Entity<?, ?> visitMetric(Metric metric, Void parameter) {
                return new Metric(metric.getName(), metric.getPath(), identityHash, metric.getType(),
                        metric.getCollectionInterval(), metric.getProperties());
            }

            @Override public Entity<?, ?> visitMetricType(MetricType type, Void parameter) {
                return new MetricType(type.getName(), type.getPath(), identityHash, type.getUnit(),
                        type.getType(), type.getProperties(), type.getCollectionInterval());
            }

            @Override public Entity<?, ?> visitOperationType(OperationType operationType, Void parameter) {
                return new OperationType(operationType.getName(), operationType.getPath(), identityHash,
                        operationType.getProperties());
            }

            @Override public Entity<?, ?> visitResource(Resource resource, Void parameter) {
                return new Resource(resource.getName(), resource.getPath(), identityHash, resource.getType(),
                        resource.getProperties());
            }

            @Override public Entity<?, ?> visitResourceType(ResourceType type, Void parameter) {
                return new ResourceType(type.getName(), type.getPath(), identityHash, type.getProperties());
            }
        }, null);
    }

    @SuppressWarnings("unchecked")
    private Notification<?, ?> cloneWithNewEntity(Notification<?, ?> notif, AbstractElement<?, ?> element) {
        if (!(notif.getValue() instanceof AbstractElement)) {
            return notif;
        }

        Object context = notif.getActionContext();
        Object value = notif.getValue();

        if (context instanceof AbstractElement && element.getPath().equals(((AbstractElement) context).getPath())) {
            context = element;
        }

        if (element.getPath().equals(((AbstractElement) value).getPath())) {
            value = element;
        }

        return new Notification(context, value, notif.getAction());
    }

    private static class ProcessingTree<BE> {
        //keep it small, we're not going to have many children usually
        final Set<ProcessingTree<BE>> children = new HashSet<>(2);
        final Path.Segment path;
        final CanonicalPath cp;
        final List<Notification<?, ?>> notifications;
        AbstractElement<?, ?> element;
        BE representation;

        ProcessingTree() {
            this(null, null, null, null, null);
        }

        private ProcessingTree(BE representation, AbstractElement<?, ?> element, Path.Segment path, CanonicalPath cp,
                               List<Notification<?, ?>> notifications) {
            this.representation = representation;
            this.element = element;
            this.path = path;
            this.cp = cp;
            this.notifications = notifications == null ? new ArrayList<>(0) : notifications;
            clear();
        }

        void clear() {
            children.clear();
        }

        public boolean needsResolution() {
            return InventoryStructure.EntityType.supports(path.getElementType());
        }

        public void loadFrom(Transaction<BE> tx) throws ElementNotFoundException {
            if (element == null) {
                representation = tx.find(cp);
                @SuppressWarnings("unchecked")
                Class<? extends AbstractElement<?, ?>> type = (Class<? extends AbstractElement<?, ?>>)
                        tx.extractType(representation);
                element = tx.convert(representation, type);
            }
        }

        void add(EntityAndPendingNotifications<BE, ?> entity) {
            if (this.path != null) {
                throw new IllegalStateException("Cannot add element to partial results from a non-root segment.");
            }

            Set<ProcessingTree<BE>> children = this.children;

            CanonicalPath.Extender cp = CanonicalPath.empty();

            ProcessingTree<BE> found = null;
            for (Path.Segment seg : entity.getEntity().getPath().getPath()) {
                cp.extend(seg);

                found = null;
                for (ProcessingTree<BE> child : children) {
                    if (seg.equals(child.path)) {
                        found = child;
                        break;
                    }
                }

                if (found == null) {
                    found = new ProcessingTree<>(null, null, seg, cp.get(), null);
                    children.add(found);
                }

                children = found.children;
            }

            if (found == null) {
                throw new IllegalStateException("Could not figure out the processing tree element for entity.");
            }

            found.representation = entity.getEntityRepresentation();
            found.element = entity.getEntity();
            found.notifications.addAll(entity.getNotifications());
        }

        void dfsTraversal(Function<ProcessingTree<BE>, Boolean> visitor) {
            TreeTraversal<ProcessingTree<BE>> traversal = new TreeTraversal<>(t -> t.children.iterator());

            //skip the root element - this is what the callers assume of the dfsTraversal
            traversal.depthFirst(this, t -> {
                if (t == this) {
                    return true;
                } else {
                    return visitor.apply(t);
                }
            });
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ProcessingTree<BE> that = (ProcessingTree<BE>) o;

            return path.equals(that.path);
        }

        @Override public int hashCode() {
            return path.hashCode();
        }
    }
}
