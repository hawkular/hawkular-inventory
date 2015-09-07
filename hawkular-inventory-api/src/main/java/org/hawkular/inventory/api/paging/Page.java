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

import java.util.Iterator;

/**
 * A read-only list representing a single page of some results.
 *
 * <p>Contains a reference to the paging state object that describes the position of the page in some overall results.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class Page<T> implements Iterator<T> {
    private final Iterator<T> wrapped;
    private final PageContext pageContext;
    private final long totalSize;

    public Page(Iterator<T> wrapped, PageContext pageContext, long totalSize) {
        this.wrapped = wrapped;
        this.pageContext = pageContext;
        this.totalSize = totalSize;
    }

    protected Page(PageContext pageContext, long totalSize) {
        this(null, pageContext, totalSize);
    }

    /**
     * @return the information about the page of the results that this object represents
     */
    public PageContext getPageContext() {
        return pageContext;
    }

    /**
     * @return the total number of results of which this page is a subset of
     */
    public long getTotalSize() {
        return totalSize;
    }

    @Override public boolean hasNext() {
        return wrapped.hasNext();
    }

    @Override public T next() {
        return wrapped.next();
    }
}
