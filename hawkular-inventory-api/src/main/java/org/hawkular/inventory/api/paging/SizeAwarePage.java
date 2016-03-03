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
package org.hawkular.inventory.api.paging;

import java.util.Iterator;

/**
 * Holds the lambda function to be able to correctly calculate the total size, once the
 * iterator is depleted
 *
 * @author Jirka Kremser
 * @since 0.3.4
 */
public class SizeAwarePage<T> extends Page<T> {
    private HasTotalSize hasTotalSize;
    private long totalSize;

    public SizeAwarePage(Iterator<T> wrapped, PageContext pageContext, HasTotalSize hasTotalSize) {
        super(wrapped, pageContext, HasTotalSize.NOT_DEPLETED);
        this.totalSize = HasTotalSize.NOT_DEPLETED;
        if (hasTotalSize == null) {
            throw new IllegalArgumentException("hasTotalSize can't be null");
        }
        this.hasTotalSize = hasTotalSize;
    }

    /**
     * This returns <ul>
     * <li>-1 if the iterator hasn't been depleted</li>
     * <li>the total size otherwise</li>
     * </ul>
     *
     * @return the total number of results of which this page is a subset of
     */
    @Override
    public long getTotalSize() {
        if (totalSize == HasTotalSize.NOT_DEPLETED && !hasNext()) {
            totalSize = hasTotalSize == null ? HasTotalSize.NOT_DEPLETED : hasTotalSize.getTotalSize();
        }
        return totalSize;
    }

    @Override public boolean hasNext() {
        return super.hasNext();
    }

    @Override
    public T next() {
        return super.next();
    }

    @Override public void close() {
        getTotalSize();
        this.hasTotalSize = null;
        super.close();
    }

    @FunctionalInterface
    public interface HasTotalSize {
        int NOT_DEPLETED = -1;

        long getTotalSize();
    }
}
