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
package org.hawkular.inventory.api.paging;

import java.util.function.Function;

/**
 * A read-only list representing a single page of some results lazily applying the conversionFunction on each of its
 * elements.
 *
 * @author Jirka Krejci
 * @since 0.3.4
 */
public final class TransformingPage<T, R> extends Page<R> {
    private final Function<? super T, ? extends R> conversionFunction;
    private final Page<T> wrappedPage;

    public TransformingPage(Page<T> wrappedPage, Function<? super T, ? extends R> conversionFunction) {
        super(wrappedPage.getPageContext(), wrappedPage.getTotalSize());
        this.conversionFunction = conversionFunction;
        this.wrappedPage = wrappedPage;
    }

    /**
     * @return the conversion function that is mapped onto elements when calling {@link #next()}
     */
    public Function<? super T, ? extends R> getConversionFunction() {
        return conversionFunction;
    }

    @Override
    public R next() {
        return conversionFunction == null ? null : conversionFunction.apply(wrappedPage.next());
    }

    @Override public boolean hasNext() {
        return wrappedPage.hasNext();
    }
}
