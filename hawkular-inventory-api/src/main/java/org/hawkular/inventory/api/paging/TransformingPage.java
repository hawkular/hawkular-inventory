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

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
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
    private Function<? super I, ? extends O> conversionFunction;
    private Page<I> wrappedPage;
    private PageContext pageContext;
    private Long totalSize;

    public TransformingPage(Page<I> wrappedPage, Function<? super I, ? extends O> conversionFunction) {
        super(wrappedPage.getPageContext(), wrappedPage.getTotalSize());
        if (conversionFunction == null) {
            throw new IllegalArgumentException("conversionFunction can't be null");
        }
        this.conversionFunction = conversionFunction;
        this.wrappedPage = wrappedPage;
        this.pageContext = wrappedPage.getPageContext();
    }

    /**
     * @return the conversion function that is mapped onto elements when calling {@link #next()}
     */
    public Function<? super I, ? extends O> getConversionFunction() {
        return conversionFunction;
    }

    @Override
    public O next() {
        if (conversionFunction == null) {
            throw new NoSuchElementException("the page has been closed");
        }
        return conversionFunction.apply(getPage().next());
    }

    @Override public boolean hasNext() {
        return wrappedPage != null && wrappedPage.hasNext();
    }

    @Override
    public PageContext getPageContext() {
        return pageContext;
    }

    @Override
    public long getTotalSize() {
        return totalSize == null ? getPage().getTotalSize() : totalSize;
    }

    @Override
    public List<O> toList() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED), false)
                .collect(Collectors.<O>toList());
    }

    @Override
    public void close() throws IOException {
        this.totalSize = wrappedPage.getTotalSize();
        this.wrappedPage.close();
        this.wrappedPage = null;
        this.conversionFunction = null;
        super.close();
    }

    private Page<I> getPage() {
        if (wrappedPage == null) {
            throw new IllegalStateException("the iterator has been already closed");
        }
        return wrappedPage;
    }
}
