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

import com.tinkerpop.blueprints.Vertex;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Tenant;

import java.util.Map;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.impl.tinkerpop.Constants.Type.environment;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
final class FeedsService extends AbstractSourcedGraphService<Feeds.Single, Feeds.Multiple, Feed, Feed.Blueprint,
        Feed.Update> implements Feeds.ReadUpdateRegister, Feeds.Read {

    FeedsService(InventoryContext context, PathContext ctx) {
        super(context, Feed.class, ctx);
    }

    @Override
    protected Filter[] initNewEntity(Vertex newEntity, Feed.Blueprint blueprint) {
        Vertex env = null;
        for(Vertex sourceEnv : source().hasType(environment)) {
            addEdge(sourceEnv, contains.name(), newEntity);
            env = sourceEnv;
        }

        Vertex tenant = getTenantVertexOf(env);
        return Filter.by(With.type(Tenant.class), With.id(getEid(tenant)), Related.by(contains),
                With.type(Environment.class), With.id(getEid(env)), Related.by(contains),
                With.type(Feed.class), With.id(getEid(newEntity))).get();
    }

    @Override
    protected Feeds.Single createSingleBrowser(FilterApplicator.Tree path) {
        return FeedBrowser.single(context, path);
    }

    @Override
    protected Feeds.Multiple createMultiBrowser(FilterApplicator.Tree path) {
        return FeedBrowser.multiple(context, path);
    }

    @Override
    protected String getProposedId(Feed.Blueprint b) {
        Vertex env = null;
        for(Vertex sourceEnv : source().hasType(environment)) {
            env = sourceEnv;
        }

        Vertex tenant = getTenantVertexOf(env);

        String envId = getEid(env);
        String tenantId = getEid(tenant);

        return context.getFeedIdStrategy().generate(context.getInventory(), new Feed(tenantId, envId, b.getId()));
    }

    @Override
    public Feeds.Single register(String proposedId, Map<String, Object> properties) {
        return super.create(new Feed.Blueprint(proposedId, properties));
    }
}
