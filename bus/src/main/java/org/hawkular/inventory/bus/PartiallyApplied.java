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
package org.hawkular.inventory.bus;

import rx.functions.Action1;

import java.util.function.BiConsumer;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class PartiallyApplied<P, T> implements Action1<T> {
    private final P parameter;
    private final BiConsumer<P, T> target;

    public PartiallyApplied(BiConsumer<P, T> target, P parameter) {
        this.parameter = parameter;
        this.target = target;
    }

    @Override
    public void call(T t) {
        target.accept(parameter, t);
    }
}
