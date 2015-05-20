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

/**
 * Base class for entities in a tenant (i.e. everything but the {@link Tenant tenant}s themselves and relationships).
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public abstract class TenantBasedEntity<B extends Entity.Blueprint, U extends AbstractElement.Update>
        extends Entity<B, U> {

    @XmlAttribute(name = "tenant")
    @Expose
    private final String tenantId;

    /**
     * JAXB support
     */
    TenantBasedEntity() {
        tenantId = null;
    }

    TenantBasedEntity(String tenantId, String id) {
        super(id);
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId == null");
        }

        this.tenantId = tenantId;
    }

    TenantBasedEntity(String tenantId, String id, Map<String, Object> properties) {
        super(id, properties);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;

        TenantBasedEntity entity = (TenantBasedEntity) o;

        return tenantId.equals(entity.tenantId);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + tenantId.hashCode();
        return result;
    }

    @Override
    protected void appendToString(StringBuilder toStringBuilder) {
        super.appendToString(toStringBuilder);
        toStringBuilder.append(", tenantId='").append(tenantId).append("'");
    }
}
