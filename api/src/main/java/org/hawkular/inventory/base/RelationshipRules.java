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

import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.base.spi.InventoryBackend;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static org.hawkular.inventory.api.Relationships.Direction.incoming;
import static org.hawkular.inventory.api.Relationships.Direction.outgoing;
import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.isParentOf;

/**
 * Some well-known relationships have certain semantic rules that need to be checked for when creating/deleting them.
 *
 * <p>This class concentrates those checks so that they can be called easily from the various places in the codebase
 * that work with relationships.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class RelationshipRules {

    private static final Map<Relationships.WellKnown, List<RuleCheck<?>>> CREATE_RULES;
    private static final Map<Relationships.WellKnown, List<RuleCheck<?>>> DELETE_RULES;

    static {
        CREATE_RULES = new HashMap<>();
        DELETE_RULES = new HashMap<>();

        CREATE_RULES.put(contains, Arrays.asList(RelationshipRules::checkDiamonds, RelationshipRules::checkLoops));
        CREATE_RULES.put(isParentOf, Arrays.asList(RelationshipRules::checkLoops));

        DELETE_RULES.put(contains, Collections.singletonList(RelationshipRules::disallowDelete));
        DELETE_RULES.put(isParentOf, Collections.singletonList(
                RelationshipRules::disallowDeleteOfIsParentOfWhenTheresContainsToo));
    }

    private RelationshipRules() {
    }

    public static <E> void checkCreate(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            Relationships.WellKnown relationship, E target) {

        check(backend, origin, direction, relationship, target, CREATE_RULES);
    }

    public static <E> void checkCreate(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {

        check(backend, origin, direction, relationship, target, CREATE_RULES);
    }

    public static <E> void checkDelete(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            Relationships.WellKnown relationship, E target) {

        check(backend, origin, direction, relationship, target, DELETE_RULES);
    }

    public static <E> void checkDelete(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target) {

        check(backend, origin, direction, relationship, target, DELETE_RULES);
    }

    public static <E> void check(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            String relationship, E target, Map<Relationships.WellKnown, List<RuleCheck<?>>> ruleSet) {

        for (Relationships.WellKnown r : Relationships.WellKnown.values()) {
            if (r.name().equals(relationship)) {
                check(backend, origin, direction, r, target, ruleSet);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> void check(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            Relationships.WellKnown relationship, E target, Map<Relationships.WellKnown, List<RuleCheck<?>>> ruleSet) {

        List<RuleCheck<?>> rules = ruleSet.get(relationship);
        if (rules == null) {
            return;
        }

        rules.forEach((r) -> ((RuleCheck<E>) r).check(backend, origin, direction, relationship, target));
    }

    @FunctionalInterface
    public interface RuleCheck<E> {
        void check(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
                Relationships.WellKnown relationship, E target);
    }

    private static <E> void checkDiamonds(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            Relationships.WellKnown relationship, E target) {
        if (direction == outgoing && backend.hasRelationship(target, Relationships.Direction.incoming,
                relationship.name())) {
            throw new IllegalArgumentException("The target is already connected with another entity using the" +
                    " relationship: '" + relationship + "'. It is illegal for such relationships to form" +
                    " diamonds.");
        } else if (direction == Relationships.Direction.incoming) {
            if (backend.hasRelationship(origin, Relationships.Direction.incoming, relationship.name())) {
                throw new IllegalArgumentException("The source is already connected with another entity using the" +
                        " relationship: '" + relationship + "'. It is illegal for such relationships to form" +
                        " diamonds.");
            }
        }
    }

    private static <E> void checkLoops(InventoryBackend<E> backend, E origin, Relationships.Direction direction,
            Relationships.WellKnown relationship, E target) {
        if (direction == Relationships.Direction.both) {
            throw new IllegalArgumentException("Relationship '" + relationship + "' cannot form a loop" +
                    " between 2 entities.");
        }

        if (origin.equals(target)) {
            throw new IllegalArgumentException("Relationship '" + relationship + "' cannot both start and end" +
                    " on the same entity.");
        }

        if (direction == Relationships.Direction.incoming) {
            Iterator<E> closure = backend.getTransitiveClosureOver(origin, relationship.name(), outgoing);

            while (closure.hasNext()) {
                E e = closure.next();
                if (e.equals(target)) {
                    throw new IllegalArgumentException("The target and the source (indirectly) form a loop while" +
                            " traversing over '" + relationship + "' relationships. This is illegal for that" +
                            " relationship.");
                }
            }
        } else if (direction == outgoing) {
            Iterator<E> closure = backend.getTransitiveClosureOver(origin, relationship.name(), incoming);

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
            Relationships.WellKnown relationship, E target) {
        throw new IllegalArgumentException("Relationship '" + relationship + "' cannot be explicitly deleted.");
    }

    private static <E> void disallowDeleteOfIsParentOfWhenTheresContainsToo(InventoryBackend<E> backend, E origin,
            Relationships.Direction direction, Relationships.WellKnown relationship, E target) {
        if (backend.hasRelationship(origin, target, contains.name())) {
            throw new IllegalArgumentException("'" + relationship + "' relationship cannot be deleted if there is" +
                    " also a '" + contains + "' relationship between the same two entities. This would mean that a" +
                    " sub-resource would no longer be considered a child of the parent resource, which doesn't make" +
                    " sense.");
        }
    }
}
