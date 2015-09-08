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

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A read-only list representing a single page of some results lazily applying the conversionFunction on each of its
 * elements.
 *
 * @author Jirka Kremser
 * @since 0.3.4
 */
public final class TransformingPage<I, O> extends Page<O> {
    private final Function<? super I, ? extends O> conversionFunction;
    private final WeakReference<Page<I>> wrappedPage;

    public TransformingPage(Page<I> wrappedPage, Function<? super I, ? extends O> conversionFunction) {
        super(wrappedPage.getPageContext(), wrappedPage.getTotalSize());
        if (conversionFunction == null) {
            throw new IllegalArgumentException("conversionFunction can't be null");
        }
        this.conversionFunction = conversionFunction;
        this.wrappedPage = new WeakReference<>(wrappedPage);
    }

    /**
     * @return the conversion function that is mapped onto elements when calling {@link #next()}
     */
    public Function<? super I, ? extends O> getConversionFunction() {
        return conversionFunction;
    }

    @Override
    public O next() {
        return conversionFunction.apply(getPage().next());
    }

    @Override public boolean hasNext() {
        Page<I> it = wrappedPage.get();
        return it != null && it.hasNext();
    }

    @Override
    public PageContext getPageContext() {
        return getPage().getPageContext();
    }

    @Override
    public long getTotalSize() {
        return getPage().getTotalSize();
    }

    @Override
    public List<O> toList() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED), false)
                .collect(Collectors.<O>toList());
    }

    private Page<I> getPage() {
        Page<I> it = wrappedPage.get();
        if (it == null) {
            throw new IllegalStateException("the weak reference has been cleared");
        }
        return it;
    }
}
