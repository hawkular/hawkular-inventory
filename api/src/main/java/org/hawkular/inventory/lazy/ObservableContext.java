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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Log;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.observers.SafeSubscriber;
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
    private final Map<Interest<?, ?>, SubjectAndWrapper<?>> observables = new ConcurrentHashMap<>();

    public <C> Observable<C> getObservableFor(Interest<C, ?> interest) {
        SubjectAndWrapper<C> sub = getSubjectAndWrapper(interest, true);
        return sub.wrapper;
    }

    public boolean isObserved(Interest<?, ?> interest) {
        return observables.containsKey(interest);
    }

    @SuppressWarnings("unchecked")
    public <C, T> Iterator<Subject<C, C>> matchingSubjects(Action<C, T> action, T object) {
        return observables.entrySet().stream().filter((e) -> e.getKey().matches(action, object))
                .map((e) -> ((SubjectAndWrapper<C>) e.getValue()).subject).iterator();
    }

    private <C> SubjectAndWrapper<C> getSubjectAndWrapper(Interest<C, ?> interest, boolean initialize) {
        @SuppressWarnings("unchecked")
        SubjectAndWrapper<C> sub = (SubjectAndWrapper<C>) observables.get(interest);

        if (initialize && sub == null) {
            SubscriptionTracker tracker = new SubscriptionTracker(() -> observables.remove(interest));
            Subject<C, C> subject = PublishSubject.<C>create().toSerialized();

            //error handling:
            //OperatorIgnoreError - in case subscribers and us run in the same thread, an error in the subscriber
            //may error out the whole observable, which is definitely NOT what we want.
            Observable<C> wrapper = null;
            wrapper = subject.lift(new OperatorIgnoreError<>()).doOnSubscribe(tracker.onSubscribe())
                    .doOnUnsubscribe(tracker.onUnsubscribe());

            sub = new SubjectAndWrapper<>(subject, wrapper);
            observables.put(interest, sub);
        }

        return sub;
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

    private static class SubjectAndWrapper<T> {
        final Subject<T, T> subject;
        final Observable<T> wrapper;

        private SubjectAndWrapper(Subject<T, T> subject, Observable<T> wrapper) {
            this.subject = subject;
            this.wrapper = wrapper;
        }
    }

    private static final class OperatorIgnoreError<T> implements Observable.Operator<T, T> {

        @SuppressWarnings("unchecked")
        @Override
        public Subscriber<? super T> call(Subscriber<? super T> subscriber) {
            return new SafeSubscriber<T>(subscriber) {
                private boolean done = false;

                private final Subscriber<? super T> actual;

                {
                    Subscriber<? super T> s = subscriber;
                    while (s instanceof SafeSubscriber) {
                        s = ((SafeSubscriber<? super T>) s).getActual();
                    }

                    actual = s;
                }

                @Override
                public void onNext(T t) {
                    if (done) {
                        return;
                    }

                    try {
                        actual.onNext(t);
                    } catch (Exception e) {
                        Log.LOGGER.debugf(e, "Subscriber %s failed to process %s.", actual, t);
                    }
                }

                @Override
                protected void _onError(Throwable e) {
                    done = true;
                    super._onError(e);
                }
            };
        }
    }
}
