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
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Query;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.model.Change;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.base.spi.EntityHistory;
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


    public List<Change<E>> history(Instant from, Instant to) {
        EntityHistory<E> history = inTx(tx -> {
            //the null discriminator is here intentionally - we need to find the entity whenever it existed in time..
            BE myEntity = tx.querySingle(null, context.select().get());

            if (myEntity == null) {
                throw new EntityNotFoundException(Query.filters(context.select().get()));
            }

            Instant f = from == null ? Instant.ofEpochMilli(0) : from;
            Instant t = to == null ? Instant.ofEpochMilli(Long.MAX_VALUE) : to;

            return tx.getHistory(myEntity, context.entityClass, f, t);
        });

        List<Change<E>> ret = new ArrayList<>(history.getChanges().size());

        int processed = 0;
        Action.Enumerated created = Action.Enumerated.CREATED;
        Action.Enumerated updated = Action.Enumerated.UPDATED;

        for (EntityStateChange<E> ch : history.getChanges()) {
            Action.Enumerated chAction = ch.getAction().asEnum();

            if (chAction == updated) {
                E previous;
                //we need to compute the update object from the previous and current state
                if (processed == 0) {
                    previous = history.getInitialState();
                } else {
                    previous = history.getChanges().get(processed - 1).getEntity();
                }

                E current = ch.getEntity();

                //casting fun to overcome the imperfect typing of the update() method
                @SuppressWarnings({"unchecked", "rawtypes"})
                U update = (U) ((Entity.Updater) previous.update()).to(current);

                ret.add(new Change<E>(ch.getOccurrenceTime(), Action.updated(),
                        new Action.Update<>(previous, update)));
            } else if (chAction == created) {
                ret.add(new Change<>(ch.getOccurrenceTime(), Action.created(), ch.getEntity()));
            } else {
                ret.add(new Change<>(ch.getOccurrenceTime(), Action.deleted(), ch.getEntity()));
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
