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

package org.hawkular.inventory.impl.blueprints;

import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.model.Entity;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
class PathVisitor<S, E> extends FilterVisitor<S, E> {
    @Override
    public void visit(HawkularPipeline<S, E> query, Related<? extends Entity> related) {
        switch (related.getDirection()) {
            case IN:
                query.in(related.getRelationshipName());
                break;
            case OUT:
                query.out(related.getRelationshipName());
                break;
            case ANY:
                query.both(related.getRelationshipName());
        }

        if (related.getEntity() != null) {
            Constants.Type desiredType = Constants.Type.of(related.getEntity());

            query.hasType(desiredType).hasUid(related.getEntity().getId());
        }
    }
}
