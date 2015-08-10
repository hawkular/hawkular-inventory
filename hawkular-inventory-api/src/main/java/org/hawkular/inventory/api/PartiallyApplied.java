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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
    public static <A, B> ActionBuilder<A, B> procedure(BiConsumer<A, B> method) {
        return new ActionBuilder<>(method);
    }

    public static <T, U, R> FunctionBuilder<T, U, R> function(BiFunction<T, U, R> method) {
        return new FunctionBuilder<>(method);
    }


    public abstract static class ActionBase<P> implements Action1<P> {

        /**
         * Converts the partially applied function from the rxjava's {@link Action1} to java's {@link Consumer}.
         *
         * @return the partially applied function as a consumer
         */
        public Consumer<P> asConsumer() {
            return this::call;
        }
    }

    public static final class ActionBuilder<A, B> {
        private final BiConsumer<A, B> method;

        private ActionBuilder(BiConsumer<A, B> method) {
            this.method = method;
        }

        /**
         * Returns a partially applied method call that has the provided value applied as its first parameter.
         *
         * @param param the parameter to be applied
         * @return the partially applied method
         */
        public ActionWithAppliedFirst<A, B> first(A param) {
            return new ActionWithAppliedFirst<>(method, param);
        }

        /**
         * Returns a partially applied method call that has the provided value applied as its second parameter.
         *
         * @param param the parameter to be applied
         * @return the partially applied method
         */
        public ActionAppliedSecond<B, A> second(B param) {
            return new ActionAppliedSecond<>(method, param);
        }
    }

    public static final class FunctionBuilder<T, U, R> {
        private final BiFunction<T, U, R> method;

        private FunctionBuilder(BiFunction<T, U, R> method) {
            this.method = method;
        }

        /**
         * Returns a partially applied method call that has the provided value applied as its first parameter.
         *
         * @param param the parameter to be applied
         * @return the partially applied method
         */
        public FunctionWithAppliedFirst<T, U, R> first(T param) {
            return new FunctionWithAppliedFirst<>(param, method);
        }

        /**
         * Returns a partially applied method call that has the provided value applied as its second parameter.
         *
         * @param param the parameter to be applied
         * @return the partially applied method
         */
        public FunctionWithAppliedSecond<T, U, R> second(U param) {
            return new FunctionWithAppliedSecond<>(param, method);
        }
    }

    public static final class ActionWithAppliedFirst<A, P> extends ActionBase<P> {
        private final A appliedParam;
        private final BiConsumer<A, P> targetFunction;

        private ActionWithAppliedFirst(BiConsumer<A, P> targetFunction, A appliedParam) {
            this.appliedParam = appliedParam;
            this.targetFunction = targetFunction;
        }

        @Override
        public void call(P p) {
            targetFunction.accept(appliedParam, p);
        }
    }

    public static final class ActionAppliedSecond<A, P> extends ActionBase<P> {
        private final A appliedParam;
        private final BiConsumer<P, A> targetFunction;

        private ActionAppliedSecond(BiConsumer<P, A> targetFunction, A appliedParam) {
            this.appliedParam = appliedParam;
            this.targetFunction = targetFunction;
        }

        @Override public void call(P p) {
            targetFunction.accept(p, appliedParam);
        }
    }

    public static final class FunctionWithAppliedFirst<T, U, R> implements Function<U, R> {
        private final T appliedParam;
        private final BiFunction<T, U, R> function;

        private FunctionWithAppliedFirst(T appliedParam, BiFunction<T, U, R> function) {
            this.appliedParam = appliedParam;
            this.function = function;
        }

        @Override
        public R apply(U u) {
            return function.apply(appliedParam, u);
        }
    }

    public static final class FunctionWithAppliedSecond<T, U, R> implements Function<T, R> {
        private final U appliedParam;
        private final BiFunction<T, U, R> function;

        private FunctionWithAppliedSecond(U appliedParam, BiFunction<T, U, R> function) {
            this.appliedParam = appliedParam;
            this.function = function;
        }

        @Override
        public R apply(T t) {
            return function.apply(t, appliedParam);
        }
    }
}
