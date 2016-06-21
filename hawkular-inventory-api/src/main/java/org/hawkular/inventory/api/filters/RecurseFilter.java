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
package org.hawkular.inventory.api.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A filter that can express conditions for retrieving elements recursively.
 *
 * @author Lukas Krejci
 * @since 0.9.0
 */
public final class RecurseFilter extends Filter {
    private final Filter[][] loopChains;

    public RecurseFilter(Filter[][] loopChains) {
        this.loopChains = loopChains;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Filter[][] getLoopChains() {
        return loopChains;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("RecurseFilter[loopChains=[");
        Stream.of(loopChains).forEach(fs -> sb.append(Arrays.asList(fs)));
        sb.append("]]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurseFilter)) return false;

        RecurseFilter that = (RecurseFilter) o;

        return Arrays.deepEquals(loopChains, that.loopChains);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(loopChains);
    }

    public static final class Builder {
        private final List<Filter[]> chains = new ArrayList<>();

        public ChainBuilder startChain() {
            return new ChainBuilder();
        }

        public Builder addChain(Filter... filterChain) {
            chains.add(filterChain);
            return this;
        }

        public RecurseFilter build() {
            Filter[][] chains = this.chains.toArray(new Filter[this.chains.size()][]);
            return new RecurseFilter(chains);
        }

        public final class ChainBuilder {
            private final List<Filter> chain = new ArrayList<>();

            public ChainBuilder add(Filter... filters) {
                Collections.addAll(chain, filters);
                return this;
            }

            public Builder done() {
                chains.add(chain.toArray(new Filter[chain.size()]));
                return Builder.this;
            }
        }
    }
}
