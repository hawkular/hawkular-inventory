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

import static org.hawkular.inventory.api.Action.contentHashChanged;
import static org.hawkular.inventory.api.Action.created;
import static org.hawkular.inventory.api.Action.identityHashChanged;
import static org.hawkular.inventory.api.Action.syncHashChanged;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
import org.hawkular.inventory.api.model.ContentHashable;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.ElementVisitor;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Hashes;
import org.hawkular.inventory.api.model.IdentityHashable;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Syncable;
import org.hawkular.inventory.base.spi.ElementNotFoundException;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.jboss.logging.Logger;

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
    //DON'T USE THIS FOR ANYTHING ELSE BUT DEBUG LOGGING - for normal logging purposes, use the typed logger
    //from the api package
    private static final Logger DBG = Logger.getLogger(BasePreCommit.class);

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
        if (element.getEntity() instanceof Relationship) {
            return false;
//Including relationship changes in hash computations would lead to the loss custom relationships
//created out of the control of the syncing party. E.g. custom relationships created by the glue code
//or users of inventory. Thus this is kept here only for reference if we revisit this requirement in the future.
//            //relationships need processing if they involve syncable entities.
//
//            Relationship rl = (Relationship) element.getEntity();
//
//            if (isSyncable(rl.getSource()) || isSyncable(rl.getTarget())) {
//                //we only need processing if the relationship is created or deleted. Updates of a relationship don't
//                //affect the hashes of the related entities
//                return element.getNotifications().stream()
//                        .filter(n -> n.getAction() == Action.created() || n.getAction() == Action.deleted())
//                        .findAny().isPresent();
//            }
        }

        return true;
    }

    private void process() {
        //walk the processing tree and process the top most
        List<ProcessingTree<BE>> syncHashRoots = new ArrayList<>();

        processingTree.dfsTraversal(t -> {
            syncHashRoots.add(t);
            //we stop the filling the roots if we find a identity-hashable entity - all its children will be part
            //of the hash tree, so we don't need to explicitly look for them...
            return !t.canBeIdentityRoot();
        });

        if (syncHashRoots.isEmpty()) {
            correctiveAction = null;
            return;
        }

        correctiveAction = t -> {
            DBG.debug("Processing pre-commit changes.");
            processingTree.children.forEach(c -> correctChanges(c, true));
            DBG.debug("Done processing pre-commit changes.");
        };
    }

    private void correctChanges(ProcessingTree<BE> changedEntity, boolean computeHashes) {
        DBG.debugf("Processing changes of %s (compute hashes = %s)", changedEntity.cp, computeHashes);
        if (__correctChangesPrologue(changedEntity)) {
            return;
        }

        if (changedEntity.isSignificant()) {
            @SuppressWarnings("unchecked")
            Entity<? extends Entity.Blueprint, ?> e = (Entity<? extends Entity.Blueprint, ?>) changedEntity.element;
            Hashes.Tree treeHash;

            if (computeHashes) {
                DBG.debugf("About to compute treehash of %s", changedEntity.cp);

                //this inventory structure stores the loaded entities as the attachments of the structure elements
                //we can take advantage of that below to "load" the hashes, by just looking up the attachment
                InventoryStructure<?> struct = InventoryStructure.of(e, inventory);

                treeHash = Hashes.treeOf(struct, e.getPath(), rp -> {
                    if (DBG.isDebugEnabled()) {
                        DBG.debugf("About to load hashes of %s", rp.applyTo(changedEntity.cp));
                    }

                    if (rp.getPath().isEmpty()) {
                        //this represents the changedEntity itself, which is by definition in its processing tree...
                        DBG.debugf("Not loading the hashes, because this is the changed entity itself.");
                        return null;
                    }
                    ProcessingTree<BE> child = changedEntity.getChild(rp);
                    if (child != null) {
                        //we've found the child in the processing tree. This means there's been some changes
                        //made to it or its children, so we need to recompute the hashes. Returning null means
                        //"recompute the hashes".
                        DBG.debugf("Child %s found in processing tree. Hashes need to be recomputed, not loaded.",
                                rp.applyTo(changedEntity.cp));
                        return null;
                    } else {
                        //ok, this entity is not in our processing tree.. This means there's been no change to
                        //it made in this transaction, so we can just load its hashes from the database instead
                        //of (recursively) computing them
                        CanonicalPath childCp = rp.applyTo(e.getPath());

                        DBG.debugf("Using pre-loaded hashes of %s.", childCp);

                        InventoryStructure.FullNode node = struct.getNode(rp);
                        if (node == null) {
                            //hmm, ok, let's just try and compute this then
                            DBG.debugf("Entity %s not found in the database, its hashes will be recomputed instead.",
                                    childCp);
                            return null;
                        }

                        Entity<?, ?> childE = (Entity<?, ?>) node.getAttachment();

                        DBG.debugf("Hashes of %s loaded from database.", childCp);

                        return Hashes.of(childE);
                    }
                });
            } else {
                DBG.debugf("Not computing hashes of %s as instructed.", changedEntity.cp);
                treeHash = Hashes.Tree.builder().build();
            }

            __correctChangesNoPrologue(changedEntity, treeHash);
        } else {
            DBG.debugf("Entity %s not significant for hashing, proceeding to its children.", changedEntity.cp);
            changedEntity.children.forEach(c -> correctChanges(c, computeHashes));
        }

        DBG.debugf("Done processing changes of %s", changedEntity.cp);
    }

    private void correctChanges(ProcessingTree<BE> changedEntity, Hashes.Tree newHash) {
        DBG.debugf("Processing changes of %s with tree loaded", changedEntity.cp);
        if (!__correctChangesPrologue(changedEntity)) {
            __correctChangesNoPrologue(changedEntity, newHash);
        }
        DBG.debugf("Done processing changes of %s with tree loaded", changedEntity.cp);
    }

    private void __correctChangesNoPrologue(ProcessingTree<BE> changedEntity, Hashes.Tree newHash) {
        if (changedEntity.element instanceof Syncable || changedEntity.element instanceof IdentityHashable) {
            correctHierarchicalHashChanges(newHash, changedEntity);
        } else {
            correctNonHierarchicalHashChanges(newHash.getHash(), changedEntity);
        }
    }

    private boolean __correctChangesPrologue(ProcessingTree<BE> changedEntity) {
        if (changedEntity.cp == null) {
            return false;
        }

        if (!changedEntity.isSignificant()) {
            //fast track - this is an entity that has not been CRUD'd but is present in the tree to complete the
            //hierarchy
            DBG.debug("Entity not hash-significant. Proceeding quickly.");
            return false;
        }

        if (!(Entity.Blueprint.class.isAssignableFrom(
                Inventory.types().bySegment(changedEntity.cp.getSegment().getElementType()).getBlueprintType()))) {
            //this is currently the case for metadata packs, which do not have their IDs assigned by the user.
            //we therefore mark them as processed.
            DBG.debug("Metadatapacks not processable. Bailing out quickly.");
            return true;
        }

        try {
            DBG.debug("Refreshing the entity in the current transaction.");
            changedEntity.loadFrom(tx);
        } catch (ElementNotFoundException e) {
            //ok, we're inside a delete and the root entity no longer exists... bail out quickly...
            if (processDeletion(changedEntity, changedEntity.notifications)) {
                return true;
            } else {
                throw new EntityNotFoundException(Query.filters(Query.to(changedEntity.cp)));
            }
        }

        return processDeletion(changedEntity, changedEntity.notifications);
    }

    private void correctNonHierarchicalHashChanges(Hashes hashes, ProcessingTree<BE> changedEntity) {
        @SuppressWarnings("unchecked")
        Entity<? extends Entity.Blueprint, ?> e = (Entity<? extends Entity.Blueprint, ?>) changedEntity.element;
        addHashChangeNotifications(changedEntity.element, hashes, changedEntity.notifications);

        DBG.debugf("Persisting changed hashes of %s to the database", changedEntity.cp);

        tx.updateHashes(changedEntity.representation, hashes);
        correctedChanges.add(new EntityAndPendingNotifications<BE, AbstractElement<?, ?>>(
                changedEntity.representation, e, changedEntity.notifications));

        DBG.debugf("Proceeding to children of %s", changedEntity.cp);

        changedEntity.children.forEach(c -> correctChanges(c, true));
    }

    private void correctHierarchicalHashChanges(Hashes.Tree treeHash, ProcessingTree<BE> changesTree) {
        Entity<?, ?> e = cloneWithHash((Entity<?, ?>) changesTree.element, treeHash.getHash());

        List<Notification<?, ?>> ns = changesTree.notifications.stream().map(n -> cloneWithNewEntity(n, e))
                .collect(toList());

        //check if there is a create or update notification if necessary
        Hashes origHash = hashesOf(changesTree.element);
        if (!Objects.equals(origHash, treeHash.getHash())) {
            //check if the notifications contain a create
            Optional<Notification<?, ?>> createNotif = ns.stream()
                    .filter(n -> n.getAction() == created() && n.getValue().equals(changesTree.element))
                    .findAny();

            if (!createNotif.isPresent()) {
                if (origHash == null) {
                    ns.add(new Notification<>(changesTree.element, changesTree.element, created()));
                } else {
                    addHashChangeNotifications(changesTree.element, treeHash.getHash(), ns);
                }
            }

            //now also actually update the element in inventory with the new hashes
            DBG.debugf("Updating hashes of %s in database.", changesTree.cp);
            tx.updateHashes(changesTree.representation, treeHash.getHash());
            DBG.debugf("Done updating hashes of %s in database.", changesTree.cp);
        }

        //set the notifications to emit
        correctedChanges.add(new EntityAndPendingNotifications<BE, AbstractElement<?, ?>>(
                changesTree.representation, e, ns));

        //traverse the children to reset their identity hashes
        ArrayList<Hashes.Tree> treeChildren = new ArrayList<>(treeHash.getChildren());
        Collections.sort(treeChildren, (a, b) ->
                a.getPath().getSegment().getElementId().compareTo(b.getPath().getSegment().getElementId()));

        ArrayList<ProcessingTree<BE>> processedChildren = new ArrayList<>(changesTree.children);
        Collections.sort(processedChildren, (a, b) ->
                a.path.getElementId().compareTo(b.path.getElementId()));


        for (int i = 0, j = 0; i < processedChildren.size() && j < treeChildren.size();) {
            ProcessingTree<BE> p = processedChildren.get(i);
            Hashes.Tree h = treeChildren.get(j);

            int cmp = p.path.getElementId().compareTo(h.getPath().getSegment().getElementId());
            if (cmp == 0) {
                //we found 2 equal elements, let's compare and continue
                correctChanges(p, h);
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

                if (p.canBeIdentityRoot()) {
                    //if p was deleted, it's ok, but if it wasn't we have a serious problem - we found a change
                    //contributing to the identity hash of some parent, but the computed treeHash that should
                    //reflect the current state of inventory doesn't have a reference to the changed entity
                    if (!processDeletion(p, p.notifications)) {
                        throw new IllegalStateException(
                                "Entity on path " + p.element.getPath() + " requires identity hash re-computation" +
                                        " but was not found contributing to a tree hash of a parent. This is" +
                                        " inconsistency in the inventory model and a bug.");
                    }
                }
            }
        }

        //now process all the children that were not part of the computed tree hash. This will happen if a child
        //entity is not taken into account in the tree hash computation but is updated in the transaction.
        for (int i = treeChildren.size(); i < processedChildren.size(); ++i) {
            correctChanges(processedChildren.get(i), true);
        }
    }

    private void addHashChangeNotifications(AbstractElement<?, ?> entity, Hashes newHashes,
                                            List<Notification<?, ?>> notifications) {
        DBG.debugf("Adding hash change notifications to entity %s", entity.getPath());

        if (entity instanceof Syncable) {
            Syncable el = (Syncable) entity;

            if (!Objects.equals(el.getSyncHash(), newHashes.getSyncHash())) {
                notifications.add(new Notification<>(el, el, syncHashChanged()));
            }
        }

        if (entity instanceof ContentHashable) {
            ContentHashable el = (ContentHashable) entity;

            if (!Objects.equals(el.getContentHash(), newHashes.getContentHash())) {
                notifications.add(new Notification<>(el, el, contentHashChanged()));
            }
        }

        if (entity instanceof IdentityHashable) {
            IdentityHashable el = (IdentityHashable) entity;

            if (!Objects.equals(el.getIdentityHash(), newHashes.getIdentityHash())) {
                notifications.add(new Notification<>(el, el, identityHashChanged()));
            }
        }
    }

    private Hashes hashesOf(AbstractElement<?, ?> el) {
        String contentHash = null;
        String identityHash = null;
        String syncHash = null;

        if (el instanceof ContentHashable) {
            contentHash = ((ContentHashable) el).getContentHash();
        }

        if (el instanceof IdentityHashable) {
            identityHash = ((IdentityHashable) el).getIdentityHash();
        }

        if (el instanceof Syncable) {
            syncHash = ((Syncable) el).getSyncHash();
        }

        return contentHash == null && identityHash == null && syncHash == null
                ? null
                : new Hashes(identityHash, contentHash, syncHash);
    }

    private boolean processDeletion(ProcessingTree<BE> changesTree, List<Notification<?, ?>> ns) {
        DBG.debugf("Checking for deletion of entity %s", changesTree.cp);

        Optional<Notification<?, ?>> deleteNotification = ns.stream()
                .filter(n -> n.getAction() == Action.deleted() && n.getValue() instanceof AbstractElement)
                .filter(n -> ((AbstractElement<?, ?>) n.getValue()).getPath().equals(changesTree.cp))
                .findAny();


        if (deleteNotification.isPresent()) {
            DBG.debugf("Processing deletion of the entity %s", changesTree.cp);

            //just emit what the caller wanted and don't bother any longer
            Notification<?, ?> n = deleteNotification.get();

            correctedChanges.add(new EntityAndPendingNotifications<BE, AbstractElement<?, ?>>(
                    null, (AbstractElement<?, ?>) n.getValue(), ns));

            changesTree.dfsTraversal(pt -> {
                correctChanges(pt, false);
                return true;
            });

            DBG.debugf("Done processing deletion of entity %s", changesTree.cp);
            return true;
        }

        DBG.debugf("No delete notifications found for entity %s", changesTree.cp);
        return false;
    }

    private Entity<?, ?> cloneWithHash(Entity<?, ?> entity, Hashes hashes) {
        if (hashes == null) {
            return entity;
        }

        return entity.accept(new ElementVisitor.Simple<Entity<?, ?>, Void>() {
            @Override protected Entity<?, ?> defaultAction() {
                if (entity instanceof IdentityHashable) {
                    throw new IllegalStateException("Unhandled IdentityHashable type: " + entity);
                }

                return entity;
            }

            @Override public Entity<?, ?> visitData(DataEntity data, Void parameter) {
                return new DataEntity(data.getPath(), data.getValue(), hashes.getIdentityHash(),
                        hashes.getContentHash(), hashes.getSyncHash(), data.getProperties());
            }

            @Override public Entity<?, ?> visitFeed(Feed feed, Void parameter) {
                return new Feed(feed.getName(), feed.getPath(), hashes.getIdentityHash(),
                        hashes.getContentHash(), hashes.getSyncHash(), feed.getProperties());
            }

            @Override public Entity<?, ?> visitMetric(Metric metric, Void parameter) {
                return new Metric(metric.getName(), metric.getPath(), hashes.getIdentityHash(), hashes.getContentHash(),
                        hashes.getSyncHash(), metric.getType(),
                        metric.getCollectionInterval(), metric.getProperties());
            }

            @Override public Entity<?, ?> visitMetricType(MetricType type, Void parameter) {
                return new MetricType(type.getName(), type.getPath(), hashes.getIdentityHash(), hashes.getContentHash(),
                        hashes.getSyncHash(), type.getUnit(), type.getMetricDataType(), type.getProperties(),
                        type.getCollectionInterval());
            }

            @Override public Entity<?, ?> visitOperationType(OperationType operationType, Void parameter) {
                return new OperationType(operationType.getName(), operationType.getPath(), hashes.getIdentityHash(),
                        hashes.getContentHash(), hashes.getSyncHash(), operationType.getProperties());
            }

            @Override public Entity<?, ?> visitResource(Resource resource, Void parameter) {
                return new Resource(resource.getName(), resource.getPath(), hashes.getIdentityHash(),
                        hashes.getContentHash(), hashes.getSyncHash(),
                        resource.getType(),
                        resource.getProperties());
            }

            @Override public Entity<?, ?> visitResourceType(ResourceType type, Void parameter) {
                return new ResourceType(type.getName(), type.getPath(), hashes.getIdentityHash(),
                        hashes.getContentHash(), hashes.getSyncHash(), type.getProperties());
            }
        }, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
        private Transaction<BE> loadingTx;

        ProcessingTree() {
            this(null, null);
        }

        private ProcessingTree(Path.Segment path, CanonicalPath cp) {
            this.representation = null;
            this.element = null;
            this.path = path;
            this.cp = cp;
            this.notifications = new ArrayList<>(0);
            clear();
        }

        void clear() {
            children.clear();
        }

        private static boolean canBeIdentityRoot(SegmentType segmentType) {
            return IdentityHashable.class.isAssignableFrom(Inventory.types().bySegment(segmentType).getElementType());
        }

        boolean canBeIdentityRoot() {
            return canBeIdentityRoot(path.getElementType());
        }

        void loadFrom(Transaction<BE> tx) throws ElementNotFoundException {
            if (element == null || loadingTx != tx) {
                loadingTx = tx;
                representation = tx.find(cp);
                @SuppressWarnings("unchecked")
                Class<? extends AbstractElement<?, ?>> type = (Class<? extends AbstractElement<?, ?>>)
                        tx.extractType(representation);
                element = tx.convert(representation, type);
            }
        }

        boolean isSignificant() {
            return canBeIdentityRoot() || !notifications.isEmpty();
        }

        void add(EntityAndPendingNotifications<BE, ?> entity) {
            if (this.path != null) {
                throw new IllegalStateException("Cannot add element to partial results from a non-root segment.");
            }

            ProcessingTree<BE> found = extendTreeTo(entity.getEntity().getPath());

            found.representation = entity.getEntityRepresentation();
            found.element = entity.getEntity();
            found.notifications.addAll(entity.getNotifications());
        }

        ProcessingTree<BE> getChild(RelativePath childPath) {
            Iterator<Path.Segment> segments = childPath.getPath().iterator();
            Set<ProcessingTree<BE>> children = this.children;

            ProcessingTree<BE> current = null;
            while (segments.hasNext()) {
                Path.Segment seg = segments.next();

                current = null;
                for (ProcessingTree<BE> c : children) {
                    //children are bound to each have a different ending segment, otherwise their CP would be the same
                    //which is illegal in inventory. We can therefore just compare the current segment with the last
                    //segment of the child path and if they match, move on to that child.
                    if (seg.equals(c.cp.getSegment())) {
                        current = c;
                        children = c.children;
                        break;
                    }
                }

                //no child found matching this segment...
                if (current == null) {
                    break;
                }
            }

            return segments.hasNext() ? null : current;
        }

        private ProcessingTree<BE> extendTreeTo(CanonicalPath entityPath) {
            Set<ProcessingTree<BE>> children = this.children;

            CanonicalPath.Extender cp = CanonicalPath.empty();

            ProcessingTree<BE> found = null;
            for (Path.Segment seg : entityPath.getPath()) {
                cp.extend(seg);

                found = null;
                for (ProcessingTree<BE> child : children) {
                    if (seg.equals(child.path)) {
                        found = child;
                        break;
                    }
                }

                if (found == null) {
                    found = new ProcessingTree<>(seg, cp.get());
                    children.add(found);
                }

                children = found.children;
            }

            if (found == null) {
                throw new IllegalStateException("Could not figure out the processing tree element for entity on path "
                        + entityPath);
            }

            return found;
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

            ProcessingTree<?> that = (ProcessingTree<?>) o;

            return path.equals(that.path);
        }

        @Override public int hashCode() {
            return path.hashCode();
        }
    }
}
