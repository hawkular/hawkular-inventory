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

/**
 * Base class for entities that are part of an environment.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class EnvironmentalEntity extends OwnedEntity {

    @XmlAttribute(name = "environment")
    @Expose
    private final String environmentId;

    /** JAXB support */
    EnvironmentalEntity() {
        environmentId = null;
    }

    EnvironmentalEntity(String tenantId, String environmentId, String id) {
        super(tenantId, id);

        if (environmentId == null) {
            throw new IllegalArgumentException("environmentId == null");
        }

        this.environmentId = environmentId;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;

        EnvironmentalEntity that = (EnvironmentalEntity) o;

        return environmentId.equals(that.environmentId);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + environmentId.hashCode();
        return result;
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", environmentId='").append(environmentId).append('\'');
    }
}
