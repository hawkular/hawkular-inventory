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
package org.hawkular.inventory.impl.tinkerpop;

import org.apache.tinkerpop.gremlin.structure.Direction;

/**
 * @author Lukas Krejci
 * @since 0.6.0
 */
final class QueryTranslationState implements Cloneable {
    private boolean inEdges;
    private boolean explicitChange;
    private Direction comingFrom;

    public boolean isInEdges() {
        return inEdges;
    }

    public void setInEdges(boolean inEdges) {
        this.inEdges = inEdges;
        explicitChange = false;
    }

    public Direction getComingFrom() {
        return comingFrom;
    }

    public void setComingFrom(Direction comingFrom) {
        this.comingFrom = comingFrom;
        explicitChange = false;
    }

    public boolean isExplicitChange() {
        return explicitChange;
    }

    public void setExplicitChange(boolean explicitChange) {
        this.explicitChange = explicitChange;
    }

    @Override
    public QueryTranslationState clone() {
        try {
            return (QueryTranslationState) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("CloneNotSupportedException on a Cloneable class. What?");
        }
    }
}
