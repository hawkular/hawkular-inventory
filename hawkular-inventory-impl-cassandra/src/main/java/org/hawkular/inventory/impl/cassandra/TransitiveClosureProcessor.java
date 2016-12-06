/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.impl.cassandra;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import rx.Observable;

/**
 * @author Joel Takvorian
 */
class TransitiveClosureProcessor {

    private final Set<String> processed = new LinkedHashSet<>();
    private final Function<String, Observable<String>> otherEndMapper;

    TransitiveClosureProcessor(Function<String, Observable<String>> otherEndMapper) {
        this.otherEndMapper = otherEndMapper;
    }

    Observable<String> process(String cp) {
        processed.add(cp);
        return Observable.just(cp).concatWith(iterateSingle(cp));
    }

    private Observable<String> iterate(List<String> cps) {
        // Note: for breadth-first algorithm we have to make things a bit more complicated, storing this list of CP
        //  then continue to dive deeper in tree. Algorithm without breadth-first would be simpler.
        return Observable.from(cps)
                .concatWith(Observable.from(cps).flatMap(this::iterateSingle));
    }

    private Observable<String> iterateSingle(String cp) {
        return otherEndMapper.apply(cp)
                .filter(next -> !processed.contains(next))
                .toList()
                .flatMap(this::iterate);
    }
}
