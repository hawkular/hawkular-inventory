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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.paging.Pager;

/**
 * An SPI interface to be used by the implementations to filter the results before they're returned to the API caller.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface ResultFilter {

    /**
     * Given a context (that is passed to this instance outside of the responsibility of this interface), this method
     * is called by the inventory implementations to check whether given element belongs to the result set of a call.
     *
     * <p>This is primarily geared towards applying security checks to the results before they are passed to the API
     * caller.
     *
     * @param element the element potentially returned from a inventory call (like {@link ResolvableToSingle#entity()}
     *                or {@link org.hawkular.inventory.api.ResolvableToMany#entities(Pager)}).
     * @return true if the element is allowed to be (part of) the result
     */
    boolean isApplicable(AbstractElement<?, ?> element);
}
