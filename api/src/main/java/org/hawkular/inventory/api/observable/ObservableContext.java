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
package org.hawkular.inventory.api.observable;

import rx.Observable;
import rx.functions.Action0;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class ObservableContext {
    private final Map<Interest<?>, Subject<?, ?>> observables = new ConcurrentHashMap<>();

    public <T> Observable<T> getObservableFor(Interest<T> interest) {
        @SuppressWarnings("unchecked")
        Subject<T, T> sub = (Subject<T, T>) observables.get(interest);

        if (sub == null) {
            sub = PublishSubject.<T>create().toSerialized();
            observables.put(interest, sub);
        }

        SubscriptionTracker tracker = new SubscriptionTracker(() -> observables.remove(interest));

        return sub.doOnSubscribe(tracker.onSubscribe()).doOnUnsubscribe(tracker.onUnsubscribe());
    }

    @SuppressWarnings("unchecked")
    public <T> Iterator<Subject<T, T>> matchingSubjects(Action<T> action, T object) {
        return observables.entrySet().stream().filter((e) -> e.getKey().matches(action, object))
                .map((e) -> (Subject<T, T>) e.getValue()).iterator();
    }

    private static class SubscriptionTracker {

        private final AtomicLong counter = new AtomicLong(0);
        private final Runnable action;

        public SubscriptionTracker(Runnable action) {
            this.action = action;
        }

        public Action0 onSubscribe() {
            return counter::incrementAndGet;
        }

        public Action0 onUnsubscribe() {
            return () -> {
                if (counter.decrementAndGet() == 0) {
                    action.run();
                }
            };
        }
    }
}
