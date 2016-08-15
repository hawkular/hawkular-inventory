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
package org.hawkular.inventory.api.model;

import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.SegmentType;

import io.swagger.annotations.ApiModel;

/**
 * A data entity is an entity wrapping the data. It's sole purpose is to give a path to the piece of structured data.
 *
 * <p>A data entity has an owner - the data entities are not shared amongst entities. Further, it has a role - a purpose
 * for which the owner holds on to the data.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
@ApiModel(description = "Data entity contains JSON data and serves a certain \"role\" in the entity it is contained in",
        parent = SyncedEntity.class)
public final class DataEntity extends SyncedEntity<DataEntity.Blueprint<?>, DataEntity.Update> {

    public static final SegmentType SEGMENT_TYPE = SegmentType.d;

    private final StructuredData value;

    @SuppressWarnings("unused")
    private DataEntity() {
        this.value = null;
    }

    public DataEntity(CanonicalPath owner, DataRole role, StructuredData value, String identityHash, String contentHash,
                      String syncHash) {
        super(null, owner.extend(DataEntity.SEGMENT_TYPE, role.name()).get(), identityHash, contentHash, syncHash);
        this.value = value;
    }

    public DataEntity(CanonicalPath owner, DataRole role, StructuredData value, String identityHash,
                      String contentHash, String syncHash, Map<String, Object> properties) {
        super(owner.extend(DataEntity.SEGMENT_TYPE, role.name()).get(), identityHash, contentHash, syncHash, properties);
        this.value = value;
    }

    public DataEntity(CanonicalPath path, StructuredData value, String identityHash, String contentHash,
                      String syncHash, Map<String, Object> properties) {
        this(path.up(), DataRole.valueOf(path.getSegment().getElementId()), value, identityHash, contentHash, syncHash,
                properties);
    }

    @Override
    public String getName() {
        return getRole().name();
    }

    public StructuredData getValue() {
        return value;
    }

    public DataRole getRole() {
        return DataRole.valueOf(getId());
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitData(this, null);
    }

    @Override
    public Updater<Update, DataEntity> update() {
        return new Updater<>((u) -> {
            StructuredData newValue = valueOrDefault(u.getValue(), getValue());
            String identityHash = getIdentityHash();
            if (u.getValue() != null) {
                DataEntity.Blueprint updateBlueprint = DataEntity.Blueprint.builder().withRole(getRole())
                        .withValue(u.getValue()).build();
                InventoryStructure<DataEntity.Blueprint> structure = InventoryStructure.of(updateBlueprint).build();
                identityHash = IdentityHash.of(structure);
            }
            return new DataEntity(this.getPath().up(), this.getRole(), newValue, identityHash, getContentHash(),
                    getSyncHash(), u.getProperties());
        });
    }

    @ApiModel("DataEntityBlueprint")
    public static final class Blueprint<DR extends DataRole> extends Entity.Blueprint {
        private static final StructuredData UNDEFINED = StructuredData.get().undefined();
        private final StructuredData value;
        private final DR role;

        public static <R extends DataRole> Builder<R> builder() {
            return new Builder<>();
        }

        public Blueprint(DR role, StructuredData value, Map<String, Object> properties) {
            this(role, value, properties, null, null);
        }

        public Blueprint(DR role, StructuredData value, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(role.name(), properties, outgoing, incoming);
            if (value == null) {
                value = StructuredData.get().undefined();
            }
            this.role = role;
            this.value = value;
        }

        // this is needed for Jackson deserialization
        @SuppressWarnings("unused")
        private Blueprint() {
            value = null;
            role = null;
        }

        public StructuredData getValue() {
            //value can be null if constructed using the no-arg constructor through Jackson
            return value == null ? UNDEFINED : value;
        }

        @SuppressWarnings("unchecked")
        public DR getRole() {
            return (role == null) ? (DR) DataRole.valueOf(getId()) : role;
        }

        /**
         * This effectively calls {@code getRole().name()}.
         *
         * @return data entities don't really have a name - they derive it from their roles.
         */
        @Override public String getName() {
            return getRole().name();
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitData(this, parameter);
        }

        public static final class Builder<R extends DataRole> extends Entity.Blueprint.Builder<Blueprint, Builder<R>> {

            private R role;
            private StructuredData value;

            public Builder<R> withValue(StructuredData value) {
                this.value = value;
                return this;
            }

            public Builder<R> withRole(R role) {
                this.role = role;
                return this;
            }

            @SuppressWarnings("unchecked")
            @Override public Builder<R> withId(String id) {
                return withRole((R) DataRole.valueOf(id));
            }

            @Override
            public Blueprint<R> build() {
                if (role == null) {
                    throw new NullPointerException("Data entity role not specified.");
                }
                return new Blueprint<>(role, value, properties);
            }
        }
    }

    @ApiModel("DataEntityUpdate")
    public static final class Update extends Entity.Update {

        private final StructuredData value;

        public static Builder builder() {
            return new Builder();
        }

        public Update(StructuredData value, Map<String, Object> properties) {
            super(null, properties);
            this.value = value;
        }

        // this is needed for Jackson deserialization
        @SuppressWarnings("unused")
        private Update() {
            this(null, null);
        }

        public StructuredData getValue() {
            return value;
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitData(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {

            private StructuredData value;

            public Builder withValue(StructuredData value) {
                this.value = value;
                return this;
            }

            @Override
            public Update build() {
                return new Update(value, properties);
            }
        }
    }
}
