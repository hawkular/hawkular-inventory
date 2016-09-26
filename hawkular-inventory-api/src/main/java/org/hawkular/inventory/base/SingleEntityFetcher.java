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

import static org.hawkular.inventory.api.Relationships.Direction.outgoing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Change;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.base.spi.EntityStateChange;

/**
 * Base for {@code *Single} implementations on entities.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
class SingleEntityFetcher<BE, E extends Entity<?, U>, U extends Entity.Update>
        extends Fetcher<BE, E, U> {
    public SingleEntityFetcher(TraversalContext<BE, E> context) {
        super(context);
    }


    public List<Change<E, ?>> history(Instant from, Instant to) {
        List<EntityStateChange<E>> changes = inTx(tx -> {
            BE myEntity = tx.querySingle(context.discriminator(), context.select().get());

            return tx.getHistory(myEntity, context.entityClass, from, to);
        });

        List<Change<E, ?>> ret = new ArrayList<>(changes.size());

        boolean first = true;
        int processed = 0;
        Action.Enumerated created = Action.Enumerated.CREATED;
        Action.Enumerated updated = Action.Enumerated.UPDATED;
        Action.Enumerated deleted = Action.Enumerated.DELETED;

        for (EntityStateChange<E> ch : changes) {
            Action.Enumerated chAction = ch.getAction().asEnum();

            if (chAction == updated) {
                if (first && (processed == 0
                        || changes.size() > processed && changes.get(processed).getAction().asEnum() == deleted)) {
                    //the backend actually might represent a create immediatelly followed by delete as an update
                    //followed by delete.
                    ret.add(new Change<>(ch.getOccurrenceTime(), Action.created(), ch.getEntity(), ch.getEntity()));
                } else {
                    //k, ordinary update... we need to compute the update object from the previous and current state
                    E previous = changes.get(processed - 1).getEntity();
                    E current = ch.getEntity();

                    //casting fun to overcome the imperfect typing of the update() method
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    U update = (U) ((Entity.Updater) previous.update()).to(current);

                    ret.add(new Change<E, Action.Update<E, U>>(ch.getOccurrenceTime(), Action.updated(),
                            new Action.Update<>(previous, update), previous));
                }
                first = false;
            } else if (chAction == created) {
                ret.add(new Change<>(ch.getOccurrenceTime(), Action.created(), ch.getEntity(), ch.getEntity()));
                first = false;
            } else {
                ret.add(new Change<>(ch.getOccurrenceTime(), Action.deleted(), ch.getEntity(), ch.getEntity()));
                first = true; //the next change will be understood as a create, i.e. the first state in that
                              //incarnation of the entity
            }
            processed++;
        }

        return ret;
    }

    public Relationships.ReadWrite relationships() {
        return relationships(outgoing);
    }

    public Relationships.ReadWrite relationships(Relationships.Direction direction) {
        return new BaseRelationships.ReadWrite<>(context.proceedToRelationships(direction).get(), context.entityClass);
    }
}
