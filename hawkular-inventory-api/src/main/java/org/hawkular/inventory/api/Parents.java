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

/**
 * Marker interface that step types of different traversals need to implement. The step types are supposed to be
 * enums but we need a common super type to them all to refer to them generically in the various "browser" methods.
 *
 * @author Lukas Krejci
 * @since 0.9.0
 */
public interface Parents {

    /**
     * @return represents any parent (this actually returns null, which is what impls need to understand as "any
     * parent")
     */
    static <T extends Enum<T> & Parents> T[] any() {
        return null;
    }
}
