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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;

import org.jboss.logging.processor.util.Objects;

/**
 * Feed is a source of data. It reports about resources and metrics it knows about (and can send the actual data to
 * other Hawkular components like metrics).
 *
 * <p>Note that the feed does not have a dedicated blueprint type (i.e. data required to create a new feed
 * in some context), because the only data needed to create a new feed is its ID, which can easily be modelled
 * by a {@code String}.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
@XmlRootElement
public final class Feed extends /*IdentityHashed*/Entity<Feed.Blueprint, Feed.Update> {

    /**
     * JAXB support
     */
    @SuppressWarnings("unused")
    private Feed() {
    }

    public Feed(CanonicalPath path) {
        this(path, null);
    }

    public Feed(String name, CanonicalPath path) {
        super(name, path);
    }

    public Feed(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
    }

    public Feed(String name, CanonicalPath path, Map<String, Object> properties) {
        super(name, path, properties);
    }

    @Override
    public Updater<Update, Feed> update() {
        return new Updater<>((u) -> new Feed(u.getName(), getPath(), u.getProperties()));
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
        return visitor.visitFeed(this, parameter);
    }

    public static final class Blueprint extends Entity.Blueprint {

        public static Builder builder() {
            return new Builder();
        }
        private static final String AUTO_ID_FLAG = "__auto-generate-id";

        //JAXB support
        @SuppressWarnings("unused")
        private Blueprint() {
        }

        public Blueprint(String id, Map<String, Object> properties) {
            super(id == null ? AUTO_ID_FLAG : id, properties);
        }

        public Blueprint(String id, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, properties, outgoing, incoming);
        }

        public Blueprint(String id, String name, Map<String, Object> properties) {
            super(id, name, properties);
        }

        public Blueprint(String id, String name, Map<String, Object> properties,
                         Map<String, Set<CanonicalPath>> outgoing,
                         Map<String, Set<CanonicalPath>> incoming) {
            super(id, name, properties, outgoing, incoming);
        }

        @Override
        public <R, P> R accept(ElementBlueprintVisitor<R, P> visitor, P parameter) {
            return visitor.visitFeed(this, parameter);
        }

        public static final class Builder extends Entity.Blueprint.Builder<Blueprint, Builder> {

            @Override
            public Blueprint build() {
                return new Blueprint(id, name, properties, outgoing, incoming);
            }
        }

        public static boolean shouldAutogenerateId(Feed proposedFeed) {
            return AUTO_ID_FLAG.equals(proposedFeed.getId());
        }
    }

    public static final class Update extends Entity.Update {

        public static Builder builder() {
            return new Builder();
        }

        //JAXB support
        @SuppressWarnings("unused")
        private Update() {
            this(null);
        }

        public Update(Map<String, Object> properties) {
            super(null, properties);
        }

        public Update(String name, Map<String, Object> properties) {
            super(name, properties);
        }

        @Override
        public <R, P> R accept(ElementUpdateVisitor<R, P> visitor, P parameter) {
            return visitor.visitFeed(this, parameter);
        }

        public static final class Builder extends Entity.Update.Builder<Update, Builder> {
            @Override
            public Update build() {
                return new Update(name, properties);
            }
        }
    }

    public static final class Sync {
        private final RelativePath root;
        private final Node tree;

        public static Sync of(RelativePath metricPath, Metric.Blueprint metric) {
            Objects.checkNonNull(metricPath, "metricPath == null");
            Objects.checkNonNull(metric, "metric == null");
            checkRootEquivalence(metric, metricPath);

            return new Sync(metricPath, new Node(Collections.emptySet(), metric));
        }

        public static Builder of(RelativePath resourcePath, Resource.Blueprint resource) {
            Objects.checkNonNull(resourcePath, "resourcePath == null");
            Objects.checkNonNull(resource, "resource == null");
            checkRootEquivalence(resource, resourcePath);

            return new Builder(resourcePath, resource);
        }

        /**
         * Required for jackson (de)ser.
         */
        @SuppressWarnings("unused")
        private Sync() {
            root = null;
            tree = null;
        }

        private Sync(RelativePath root, Node tree) {
            this.root = root;
            this.tree = tree;
        }

        public RelativePath getRoot() {
            return root;
        }

        public Node getTree() {
            return tree;
        }


        private static void checkRootEquivalence(Entity.Blueprint data, RelativePath root) {
            if (root.isDefined()) {
                Class<?> expectedRootType = data instanceof Resource.Blueprint ? Resource.class : Metric.class;
                if (!(root.getSegment().getElementId().equals(data.getId()) && root.getSegment().getElementType()
                        .equals(expectedRootType))) {
                    throw new IllegalArgumentException("Provided root blueprint tries to define an entity with " +
                            "different ID than specified by the root path.");
                }
            }
        }

        public static final class Node {
            private final Entity.Blueprint data;
            private final Set<Node> children;

            /**
             * Required for jackson (de)ser.
             */
            @SuppressWarnings("unused")
            private Node() {
                data = null;
                children = null;
            }

            private Node(Set<Node> children, Entity.Blueprint data) {
                this.children = children;
                this.data = data;
            }

            public Set<Node> getChildren() {
                return children;
            }

            public Entity.Blueprint getData() {
                return data;
            }

            public static final class Builder<Parent extends ResourceNodeBuilder> extends ResourceNodeBuilder {
                private final Entity.Blueprint data;
                private Set<Node> children = new LinkedHashSet<>();
                private final Parent parentBuilder;

                public Builder(Entity.Blueprint data, Parent parentBuilder) {
                    this.data = data;
                    this.parentBuilder = parentBuilder;
                }


                public Builder<Parent> addChild(Resource.Blueprint childResource) {
                    addNode(new Node(Collections.emptySet(), childResource));
                    return this;
                }

                public Builder<Parent> addChild(Metric.Blueprint childMetric) {
                    addNode(new Node(Collections.emptySet(), childMetric));
                    return this;
                }

                public Builder<Builder<Parent>> startChildResource(Resource.Blueprint childResource) {
                    return new Builder<>(childResource, this);
                }

                public Parent end() {
                    parentBuilder.addNode(new Node(children, data));
                    return parentBuilder;
                }

                @Override
                protected void addNode(Node node) {
                    children.add(node);
                }
            }
        }

        private abstract static class ResourceNodeBuilder {
            protected abstract void addNode(Node node);
        }

        public static final class Builder extends ResourceNodeBuilder {
            private final RelativePath root;
            private final Node.Builder<Builder> rootBuilder;

            private Builder(RelativePath root, Entity.Blueprint data) {
                this.root = root;
                this.rootBuilder = new Node.Builder<>(data, this);
            }

            public Builder addChild(Resource.Blueprint childResource) {
                rootBuilder.addChild(childResource);
                return this;
            }

            public Builder addChild(Metric.Blueprint childMetric) {
                rootBuilder.addChild(childMetric);
                return this;
            }

            public Node.Builder<Node.Builder<Builder>> startResource(Resource.Blueprint childResource) {
                return rootBuilder.startChildResource(childResource);
            }

            public Sync build() {
                return new Sync(root, new Node(rootBuilder.children, rootBuilder.data));
            }

            @Override
            protected void addNode(Node node) {
                rootBuilder.children.add(node);
            }
        }
    }
}
