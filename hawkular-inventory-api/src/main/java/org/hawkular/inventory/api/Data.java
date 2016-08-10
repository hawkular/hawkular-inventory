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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.RelativePath;

/**
 * @author Lukas Krejci
 * @since 0.3.0
 */
public final class Data {

    private Data() {

    }

    /**
     * An interface implemented by Single/Multiple interfaces of entities that can contain data.
     * @param <Access> the type of access to data
     */
    public interface Container<Access> {
        Access data();
    }

    public interface Read<Role extends DataRole> extends ReadInterface<Single, Multiple, Role> {
    }

    public interface ReadWrite<Role extends DataRole>
            extends ReadWriteInterface<DataEntity.Update, DataEntity.Blueprint<Role>, Single, Multiple, Role> {

        @Override
        Single create(DataEntity.Blueprint<Role> blueprint, boolean cache) throws EntityAlreadyExistsException,
                ValidationException;

        @Override
        default Single create(DataEntity.Blueprint<Role> blueprint) throws EntityAlreadyExistsException {
            return create(blueprint, true);
        }

        @Override
        void update(Role role, DataEntity.Update update) throws EntityNotFoundException, ValidationException;
    }

    public interface Single extends Synced.Single<DataEntity, DataEntity.Blueprint<?>, DataEntity.Update> {

        /**
         * Loads the data entity on the current position in the inventory traversal along with its data.
         *
         * <p>Note that this might be a potentially expensive operation because of the attached data structure being
         * loaded.
         *
         * @return the fully loaded structured data on the current position in the inventory traversal
         * @throws EntityNotFoundException if there is no structured data on the current position in the inventory
         * @see ResolvableToSingle#entity()
         */
        @Override
        DataEntity entity() throws EntityNotFoundException;

        /**
         * Returns the data on the path relative to the entity.
         *
         * In another words, you can use this method to obtain only a subset of the data stored on the data entity.
         *
         * <p>I.e if you have an data entity which contains a map with a key "foo", which contains a list and you
         * want to obtain a third element of that list, you'd do:
         * {@code
         * ...data(RelativePath.to().structuredData().key("foo").index(2).get());
         * }
         *
         * <p>If you want to obtain the whole data structure, use an empty path: {@code RelativePath.empty().get()}.
         *
         * @param dataPath the path to the subset of the data.
         * @return the subset of the data stored with the data entity
         * @see #flatData(RelativePath)
         */
        StructuredData data(RelativePath dataPath);

        /**
         * This is very similar to {@link #data(RelativePath)} but this method doesn't load the child data.
         *
         * <p>If the data on the path contains a "primitive" value, the value is loaded. If the data contains a list,
         * the returned instance will contain an empty list and if the data contains a map the returned instance will
         * contain an empty map.
         *
         * @param dataPath the path to the subset of the data to return
         * @return the subset of the data stored with the data entity
         */
        StructuredData flatData(RelativePath dataPath);

        @Override
        default boolean exists() {
            try {
                flatData(RelativePath.empty().get());
                return true;
            } catch (EntityNotFoundException | RelationNotFoundException ignored) {
                return false;
            }
        }

        @Override
        void update(DataEntity.Update update) throws EntityNotFoundException, RelationNotFoundException,
                ValidationException;
    }

    public interface Multiple extends ResolvableToMany<DataEntity> {

        /**
         * Note that this is potentially expensive operation because it loads all the data associated with each of the
         * returned data entities.
         *
         * @param pager the pager object describing the subset of the entities to return
         * @return the page of the results
         */
        @Override
        Page<DataEntity> entities(Pager pager);

        /**
         * Similar to {@link Single#data(RelativePath)}, only resolved over multiple entities.
         *
         * @param dataPath the path to the data entry inside the data entity
         * @param pager    pager to use to page the data
         * @return the page of the data entries
         */
        Page<StructuredData> data(RelativePath dataPath, Pager pager);

        /**
         * Similar to {@link Single#flatData(RelativePath)}, only resolved over multiple entities.
         *
         * @param dataPath the path to the data entry inside the data entity
         * @param pager    pager to use to page the data
         * @return the page of the data entries
         */
        Page<StructuredData> flatData(RelativePath dataPath, Pager pager);

        @Override
        default boolean anyExists() {
            return flatData(RelativePath.empty().get(), Pager.single()).hasNext();
        }
    }
}
