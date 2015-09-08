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
import java.util.Iterator;

/**
 * Holds the weak reference to the hawkular pipe to be able to correctly calculate the total size, once the
 * iterator is depleted
 *
 * @author Jirka Kremser
 * @since 0.3.4
 */
public class SizeAwarePage<T> extends Page<T> {
    private final WeakReference<HasTotalSize> hasTotalSize;
    private long totalSize;

    public SizeAwarePage(Iterator<T> wrapped, PageContext pageContext, WeakReference<HasTotalSize> hasTotalSize) {
        super(wrapped, pageContext, HasTotalSize.NOT_DEPLETED);
        this.totalSize = HasTotalSize.NOT_DEPLETED;
        this.hasTotalSize = hasTotalSize;
    }

    /**
     * This returns <ul>
     * <li>-1 if the iterator hasn't been depleted</li>
     * <li>-2 if the weak reference was GCed</li>
     * <li>the total size otherwise</li>
     * </ul>
     *
     * @return the total number of results of which this page is a subset of
     */
    @Override
    public long getTotalSize() {
        if (!hasNext() && totalSize == HasTotalSize.NOT_DEPLETED) {
            HasTotalSize hasTotalSizeInstance = hasTotalSize.get();
            if (hasTotalSizeInstance == null) {
                totalSize = HasTotalSize.GARBAGE_COLLECTED;
            } else {
                totalSize = hasTotalSizeInstance.getTotalSize();
            }
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

    @FunctionalInterface
    public interface HasTotalSize {
        int NOT_DEPLETED = -1;
        int GARBAGE_COLLECTED = -2;

        long getTotalSize();
    }
}
