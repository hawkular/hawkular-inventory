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

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Provides new instances of {@link Inventory} by locating it using java services.
 *
 * <p>This class is just for convenience. You can easily do without it using the {@link java.util.ServiceLoader}
 * mechanisms.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class InventoryFactory {

    /**
     * Uses the current thread's context classloader to locate the instance.
     *
     * @return a new instance of inventory or null if there is none available
     * @see #newInstance(ClassLoader)
     */
    public Inventory newInstance() {
        return newInstance(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Instantiates new {@link Inventory} by locating its service specification in the provided classloader.
     *
     * @param classLoader the classloader to use
     * @return a new inventory instance or null if no service could be located
     * @see java.util.ServiceLoader#load(Class, ClassLoader)
     */
    public Inventory newInstance(ClassLoader classLoader) {
        Iterator<Inventory> it = ServiceLoader.load(Inventory.class, classLoader).iterator();
        if (!it.hasNext()) {
            return null;
        } else {
            return it.next();
        }
    }
}
