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
 * Generic methods for readonly access to entities.
 *
 * @param <Single>   an interface for traversing and resolving a single entity
 * @param <Multiple> an interface for traversing and resolving multiple entities
 * @param <Address>  the type of addressing to resolve a single entity
 * @author Lukas Krejci
 * @since 0.0.1
 */
public interface ReadInterface<Single, Multiple, Address> extends ResolvingToSingle<Single, Address>,
        ResolvingToMultiple<Multiple> {
}
