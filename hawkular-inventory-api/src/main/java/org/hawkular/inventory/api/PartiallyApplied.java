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

import java.util.function.BiConsumer;

import rx.functions.Action1;

/**
 * This is a helper for the observables of the inventory, using which you can pass additional information to the actions
 * subscribed to an inventory interest.
 *
 * Example usage:
 * <pre>{@code
 *
 *     MyInfo context = ...;
 *     observableInventory.observable(Interest.in(Tenant.class).being(Action.created())
 *         .subscribe(PartiallyApplied.method(Receiver::method).first(context));
 * }</pre>
 *
 * The {@code Receiver.method} above has the following signature: {@code void method(MyInfo, Tenant)}.
 *
 * <p>If the partially applied method was created such as:
 * <pre>{@code
 *     PartiallyApplied.method(Receiver::method).second(context)
 * }</pre>
 *
 * The {@code Receiver.method} would have the signature: {@code void method(Tenant, MyInfo)}.
 *
 * @param <P> the type of the parameter that is NOT partially applied.
 * @author Lukas Krejci
 * @since 0.0.1
 */
public abstract class PartiallyApplied<P> implements Action1<P> {

    private PartiallyApplied() {

    }

    /**
     * Creates a new builder of the partially applied method call.
     *
     * @param method the method to be partially applied
     * @param <A>    the type of the first parameter of the method
     * @param <B>    the type of the second parameter of the method
     * @return a builder for the partial application of the method
     */
    public static <A, B> Builder<A, B> method(BiConsumer<A, B> method) {
        return new Builder<>(method);
    }

    public static final class Builder<A, B> {
        private final BiConsumer<A, B> method;

        private Builder(BiConsumer<A, B> method) {
            this.method = method;
        }

        /**
         * Returns a partially applied method call that has the provided value applied as its first parameter.
         *
         * @param param the parameter to be applied
         * @return the partially applied method
         */
        public First<A, B> first(A param) {
            return new First<>(method, param);
        }

        /**
         * Returns a partially applied method call that has the provided value applied as its second parameter.
         *
         * @param param the parameter to be applied
         * @return the partially applied method
         */
        public Second<B, A> second(B param) {
            return new Second<>(method, param);
        }
    }

    public static final class First<A, P> extends PartiallyApplied<P> {
        private final A appliedParam;
        private final BiConsumer<A, P> targetFunction;

        private First(BiConsumer<A, P> targetFunction, A appliedParam) {
            this.appliedParam = appliedParam;
            this.targetFunction = targetFunction;
        }

        @Override
        public void call(P p) {
            targetFunction.accept(appliedParam, p);
        }
    }

    public static final class Second<A, P> extends PartiallyApplied<P> {
        private final A appliedParam;
        private final BiConsumer<P, A> targetFunction;

        private Second(BiConsumer<P, A> targetFunction, A appliedParam) {
            this.appliedParam = appliedParam;
            this.targetFunction = targetFunction;
        }

        @Override public void call(P p) {
            targetFunction.accept(p, appliedParam);
        }
    }
}
