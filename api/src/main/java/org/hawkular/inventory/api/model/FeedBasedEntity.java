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
package org.hawkular.inventory.api.model;

import com.google.gson.annotations.Expose;

import javax.xml.bind.annotation.XmlAttribute;
import java.util.Map;
import java.util.Objects;

/**
 * @author Lukas Krejci
 * @since 0.0.2
 */
public abstract class FeedBasedEntity<B extends Entity.Blueprint, U extends AbstractElement.Update>
        extends EnvironmentalEntity<B, U> {

    @XmlAttribute(name = "feed")
    @Expose
    private final String feedId;

    FeedBasedEntity() {
        //JAXB support
        feedId = null;
    }

    protected FeedBasedEntity(String tenantId, String environmentId, String feedId, String id) {
        super(tenantId, environmentId, id);
        this.feedId = feedId;
    }

    public FeedBasedEntity(String tenantId, String environmentId, String feedId, String id,
        Map<String, Object> properties) {

        super(tenantId, environmentId, id, properties);
        this.feedId = feedId;
    }

    public String getFeedId() {
        return feedId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;

        FeedBasedEntity that = (FeedBasedEntity) o;

        return Objects.equals(feedId, that.feedId);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(feedId);
        return result;
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", feedId='").append(feedId).append('\'');
    }
}
