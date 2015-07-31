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
 * This is almost the same as the {@link com.tinkerpop.pipes.filter.RangeFilterPipe} in Tinkerpop with the difference
 * being that the pipe is always fully drained even if the elements being processed would be out of range of this
 * filter.
 *
 * <p>This is useful in scenarios where you want all the elements processed by the previous pipes in the pipeline but
 * you don't want elements out of the range be present in the final result. Example use case is getting the total of
 * elements while at the same time filtering out a subset of them in a single query.
 *
 * @author Lukas Krejci
 * @since 0.0.2
 */
class DrainedRangeFilterPipe<S> extends AbstractPipe<S, S> implements FilterPipe<S> {
    private final int low;
    private final int high;
    private int counter = -1;

    public DrainedRangeFilterPipe(final int low, final int high) {
        this.low = low;
        this.high = high;
        if (this.low != -1 && this.high != -1 && this.low > this.high) {
            throw new IllegalArgumentException("Not a legal range: [" + low + ", " + high + "]");
        }
    }

    protected S processNextStart() {
        while (true) {
            final S s = this.starts.next();
            this.counter++;
            if ((this.low == -1 || this.counter >= this.low) && (this.high == -1 || this.counter <= this.high)) {
                return s;
            }
// This is the only difference from the RangeFilterPipe - we let the pipeline process all the elements instead of
// bailing out quickly.. Because we're swallowing the elements coming here from the input, they will not appear in the
// output, so we're keeping the range semantics.
//            if (this.high != -1 && this.counter > this.high) {
//                throw FastNoSuchElementException.instance();
//            }
        }
    }

    public String toString() {
        return PipeHelper.makePipeString(this, this.low, this.high);
    }

    public void reset() {
        this.counter = -1;
        super.reset();
    }

    public int getHighRange() {
        return this.high;
    }

    public int getLowRange() {
        return this.low;
    }
}
