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
package org.hawkular.inventory.impl.tinkerpop;

import com.tinkerpop.pipes.AbstractPipe;
import com.tinkerpop.pipes.filter.FilterPipe;
import com.tinkerpop.pipes.util.PipeHelper;

/**
 * <p>
 * This simple pipe drops/skips the first n elements
 *
 * @author Jirka Kremser
 * @since 0.4.0
 */
class DropNPipe<S> extends AbstractPipe<S, S> implements FilterPipe<S> {
    private int counter;

    public DropNPipe(final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Not a legal n: [" + n + "]");
        }
        this.counter = n;
    }

    protected S processNextStart() {
        while (this.counter-- > 0) {
            this.starts.next();
        }
        return this.starts.next();
    }

    public String toString() {
        return PipeHelper.makePipeString(this, this.counter);
    }

    public void reset() {
        this.counter = -1;
        super.reset();
    }

    public int getN() {
        return this.counter;
    }
}
