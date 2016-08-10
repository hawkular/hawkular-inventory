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
package org.hawkular.inventory.json;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Supplier;

import org.hawkular.inventory.api.model.AbstractHashTree;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.RelativePath;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * @author Lukas Krejci
 * @since 0.18.0
 */
public class GenericHashTreeDeserializer<T extends AbstractHashTree<T, H>, H extends Serializable>
        extends JsonDeserializer<T> {

    private final Supplier<AbstractHashTree.TopBuilder<?, ?, T, H>> topBuilderSupplier;
    private final Class<H> hashType;

    public GenericHashTreeDeserializer(Supplier<AbstractHashTree.TopBuilder<?, ?, T, H>> topBuilderSupplier,
                                       Class<H> hashType) {
        this.topBuilderSupplier = topBuilderSupplier;
        this.hashType = hashType;
    }

    @Override public T deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        AbstractHashTree.TopBuilder<?, ?, T, H> bld = topBuilderSupplier.get();

        RelativePath.Extender emptyPath = RelativePath.empty();

        deserializeChild(p, bld, emptyPath, ctxt);

        return bld.build();
    }

    private void deserializeChildren(JsonParser p, AbstractHashTree.Builder<?, ?, T, H> bld,
                                     RelativePath.Extender parentPath, DeserializationContext ctx)
            throws IOException {

        //make a copy so that we don't modify the parent
        RelativePath.Extender origParentPath = parentPath;
        parentPath = origParentPath.get().modified();

        RelativePath childPath = null;
        while (p.nextToken() != null) {
            switch (p.getCurrentToken()) {
                case FIELD_NAME:
                    childPath = parentPath.extend(Path.Segment.from(p.getCurrentName())).get();
                    break;
                case START_OBJECT:
                    AbstractHashTree.ChildBuilder<?, ?, ?, T, H> childBld = bld.startChild();
                    deserializeChild(p, childBld, childPath.modified(), ctx);
                    childBld.endChild();
                    parentPath = origParentPath.get().modified();
                    break;
                case END_OBJECT:
                    return;
            }
        }
    }

    private void deserializeChild(JsonParser p, AbstractHashTree.Builder<?, ?, T, H> bld,
                                  RelativePath.Extender childPath, DeserializationContext ctx)
            throws IOException {

        bld.withPath(childPath.get());

        String currentField = null;
        while (p.nextToken() != null) {
            switch (p.getCurrentToken()) {
                case END_OBJECT:
                    return;
                case FIELD_NAME:
                    currentField = p.getCurrentName();
                    break;
                default:
                    switch (currentField) {
                        case "hash":
                            H hash = ctx.readValue(p, hashType);
                            bld.withHash(hash);
                            break;
                        case "children":
                            deserializeChildren(p, bld, childPath, ctx);
                            break;
                    }
            }
        }
    }
}
