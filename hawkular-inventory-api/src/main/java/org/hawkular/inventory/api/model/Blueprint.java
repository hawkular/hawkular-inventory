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
package org.hawkular.inventory.api.model;

import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.3.0
 */
public interface Blueprint {

    @SuppressWarnings("unchecked")
    static <B extends Blueprint, E extends AbstractElement<B, ?>> Class<? extends E> getEntityTypeOf(B blueprint) {
        return (Class<? extends E>) Inventory.types().byBlueprint(blueprint.getClass()).getElementType();
    }

    static <B extends Blueprint> SegmentType getSegmentTypeOf(B blueprint) {
        return Inventory.types().byBlueprint(blueprint.getClass()).getSegmentType();
    }

    <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter);
}
