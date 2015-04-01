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

import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
final class ObservableContext {
    private final Map<Interest<?>, Subject<?, ?>> observables = new ConcurrentHashMap<>();

    public <T> Subject<T, T> getSubject(Interest<T> interest) {
        @SuppressWarnings("unchecked")
        Subject<T, T> ret = (Subject<T, T>) observables.get(interest);

        if (ret == null) {
            ret = PublishSubject.create();
            observables.put(interest, ret);
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    public <T> Iterator<Subject<T, T>> matchingSubjects(Interest.Action action, T object) {
        return observables.entrySet().stream().filter((e) -> e.getKey().matches(action, object))
                .map((e) -> (Subject<T, T>) e.getValue()).iterator();
    }
}
