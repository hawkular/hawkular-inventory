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
package org.hawkular.inventory.base;

import static org.hawkular.inventory.api.Relationships.Direction.both;
import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.defines;
import static org.hawkular.inventory.api.Relationships.WellKnown.hasData;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.Relationships.WellKnown.isParentOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.base.spi.InventoryBackend;

/**
 * Some well-known relationships have certain semantic rules that need to be checked for when creating/deleting them.
 *
 * <p>This class concentrates those checks so that they can be called easily from the various places in the codebase
 * that work with relationships.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class RelationshipRules {

    private static final Map<Relationships.WellKnown, List<RuleCheck<?>>> CREATE_RULES;
    private static final Map<Relationships.WellKnown, List<RuleCheck<?>>> DELETE_RULES;

    private static final List<RuleCheck<?>> GLOBAL_CREATE_RULES;

    static {
        GLOBAL_CREATE_RULES = new ArrayList<>();
        CREATE_RULES = new HashMap<>();
        DELETE_RULES = new HashMap<>();

        GLOBAL_CREATE_RULES.add(RelationshipRules::disallowCreateAcrossTenants);

        CREATE_RULES.put(contains, Arrays.asList(RelationshipRules::checkDiamonds, RelationshipRules::checkLoops));
        CREATE_RULES.put(isParentOf, Collections.singletonList(RelationshipRules::checkLoops));
        CREATE_RULES.put(hasData, Collections.singletonList(RelationshipRules::disallowCreate));
        CREATE_RULES.put(defines, Collections.singletonList(RelationshipRules::disallowCreate));
        CREATE_RULES.put(incorporates,
                Arrays.asList(RelationshipRules::disallowCreateOfIfFeedAlreadyIncorporatedInAnotherEnvironment,
                        RelationshipRules::disallowWhenMetadataPackIsSource));

        DELETE_RULES.put(contains, Collections.singletonList(RelationshipRules::disallowDelete));
        DELETE_RULES.put(defines, Collections.singletonList(RelationshipRules::disallowDelete));
        DELETE_RULES.put(isParentOf, Collections.singletonList(
                (b, o, d, r, t) -> disallowDeleteWhenTheresContainsToo(b, o, d, r, t, "This would mean that a" +
                        " sub-resource would no longer be considered a child of the parent resource, which doesn't " +
                        " make  sense.")));
        DELETE_RULES.put(incorporates, Collections.singletonList(
                (b, o, d, r, t) -> disallowDeleteWhenTheresContainsToo(b, o, d, r, t, "When an entity is contained" +
                        " within another, it implies it is also incorporated. It would be illegal to delete only the" +
                        " 'incorporates' relationship.")));
        DELETE_RULES.put(hasData, Collections.singletonList(RelationshipRules::disallowDelete));
        DELETE_RULES.put(incorporates, Collections.singletonList(RelationshipRules::disallowWhenMetadataPackIsSource));
    }

    private RelationshipRules() {
    }

    public static <E> void checkCreate(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            Relationships.WellKnown relationship, E target) {

        check(backend, origin, direction, relationship.name(), target, CheckType.CREATE);
    }

    public static <E> void checkCreate(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {

        check(backend, origin, direction, relationship, target, CheckType.CREATE);
    }

    public static <E> void checkDelete(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            Relationships.WellKnown relationship, E target) {

        check(backend, origin, direction, relationship.name(), target, CheckType.DELETE);
    }

    public static <E> void checkDelete(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {

        check(backend, origin, direction, relationship, target, CheckType.DELETE);
    }

    @SuppressWarnings("unchecked")
    private static <E> void check(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target, CheckType checkType) {

        List<RuleCheck<?>> rules = checkType.getRuleChecks(relationship);

        rules.forEach((r) -> ((RuleCheck<E>) r).check(backend, origin, direction, relationship, target));
    }

    private static <E> void checkDiamonds(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {
        if (direction == outgoing && backend.hasRelationship(target, Relationships.Direction.incoming,
                relationship)) {
            throw new IllegalArgumentException("The target is already connected with another entity using the" +
                    " relationship: '" + relationship + "'. It is illegal for such relationships to form" +
                    " diamonds.");
        } else if (direction == Relationships.Direction.incoming) {
            if (backend.hasRelationship(origin, Relationships.Direction.incoming, relationship)) {
                throw new IllegalArgumentException("The source is already connected with another entity using the" +
                        " relationship: '" + relationship + "'. It is illegal for such relationships to form" +
                        " diamonds.");
            }
        }
    }

    private static <E> void checkLoops(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {
        if (direction == Relationships.Direction.both) {
            throw new IllegalArgumentException("Relationship '" + relationship + "' cannot form a loop" +
                    " between 2 entities.");
        }

        if (origin.equals(target)) {
            throw new IllegalArgumentException("Relationship '" + relationship + "' cannot both start and end" +
                    " on the same entity.");
        }

        if (direction == Relationships.Direction.incoming) {
            Iterator<E> closure = backend.getTransitiveClosureOver(origin, outgoing, relationship);

            while (closure.hasNext()) {
                E e = closure.next();
                if (e.equals(target)) {
                    throw new IllegalArgumentException("The target and the source (indirectly) form a loop while" +
                            " traversing over '" + relationship + "' relationships. This is illegal for that" +
                            " relationship.");
                }
            }
        } else if (direction == outgoing) {
            Iterator<E> closure = backend.getTransitiveClosureOver(origin, incoming, relationship);

            while (closure.hasNext()) {
                E e = closure.next();
                if (e.equals(target)) {
                    throw new IllegalArgumentException("The source and the target (indirectly) form a loop while" +
                            " traversing over '" + relationship + "' relationships. This is illegal for that" +
                            " relationship.");
                }
            }
        }
    }

    private static <E> void disallowDelete(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {
        throw new IllegalArgumentException("Relationship '" + relationship + "' cannot be explicitly deleted.");
    }

    private static <E> void disallowCreate(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {
        throw new IllegalArgumentException("Relationship '" + relationship + "' cannot be explicitly created.");
    }

    private static <E> void disallowWhenMetadataPackIsSource(InventoryBackend<E> backend, E origin,
                                                             Relationships.Direction direction, String relationship,
                                                             E target) {
        String message = "Manual manipulation of the 'incorporates' relationships where the" +
                " MetadataPack is the source is disallowed.";

        if (direction == both) {
            throw new IllegalArgumentException(message);
        }

        Class<?> type = backend.extractType(direction == outgoing ? origin : target);

        if (MetadataPack.class.equals(type)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <E> void disallowDeleteWhenTheresContainsToo(InventoryBackend<E> backend, E origin,
                                                                Relationships.Direction direction, String relationship,
                                                                E target, String errorDetails) {
        if (backend.hasRelationship(origin, target, contains.name())) {
            throw new IllegalArgumentException("'" + relationship + "' relationship cannot be deleted if there is" +
                    " also a '" + contains + "' relationship between the same two entities. " + errorDetails);
        }
    }

    private static <E>
    void disallowCreateOfIfFeedAlreadyIncorporatedInAnotherEnvironment(InventoryBackend<E> backend,
                                                                       E origin, Relationships.Direction direction,
                                                                       String relationship, E target) {
        if (!incorporates.name().equals(relationship)) {
            return;
        }

        Class<?> originType = backend.extractType(origin);
        Class<?> targetType = backend.extractType(target);

        if (Environment.class.equals(originType) && Feed.class.equals(targetType) && backend.hasRelationship(target,
                incoming, relationship)) {
            throw new IllegalArgumentException("Relationship '" + relationship + "' between "
                    + originType.getSimpleName() + " and " + targetType.getSimpleName() + " is 1:N." +
                    " The target entity - " + backend.extractCanonicalPath(target) + " - is already a target of" +
                    " another relationship of this name. Creating another would be illegal.");
        }
    }

    private static <E> void disallowCreateAcrossTenants(InventoryBackend<E> backend, E origin,
            Relationships.Direction direction, String relationship, E target) {

    }

    @FunctionalInterface
    private interface RuleCheck<E> {
        void check(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
                String relationship, E target);
    }

    private enum CheckType {
        CREATE {
            @Override
            public List<RuleCheck<?>> getRuleChecks(String relationship) {
                List<RuleCheck<?>> ret = new ArrayList<>(GLOBAL_CREATE_RULES);
                Relationships.WellKnown r = getWellKnown(relationship);
                if (r != null) {
                    List<RuleCheck<?>> additional = CREATE_RULES.get(r);
                    if (additional != null) {
                        ret.addAll(additional);
                    }
                }

                return ret;
            }
        }, DELETE {
            @Override
            public List<RuleCheck<?>> getRuleChecks(String relationship) {
                Relationships.WellKnown r = getWellKnown(relationship);
                if (r != null) {
                    List<RuleCheck<?>> checks = DELETE_RULES.get(r);
                    if (checks != null) {
                        return checks;
                    }
                }

                return Collections.emptyList();
            }
        };

        public abstract List<RuleCheck<?>> getRuleChecks(String relationship);

        private static Relationships.WellKnown getWellKnown(String relationship) {
            for (Relationships.WellKnown r : Relationships.WellKnown.values()) {
                if (r.name().equals(relationship)) {
                    return r;
                }
            }

            return null;
        }
    }
}
