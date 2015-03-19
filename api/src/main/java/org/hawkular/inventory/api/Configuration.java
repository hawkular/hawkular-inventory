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

package org.hawkular.inventory.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Configuration {
    private final FeedIdStrategy feedIdStrategy;
    private final Map<String, String> implementationConfiguration;

    public static Builder builder() {
        return new Builder();
    }

    public Configuration(FeedIdStrategy feedIdStrategy, Map<String, String> implementationConfiguration) {
        this.feedIdStrategy = feedIdStrategy;
        this.implementationConfiguration = implementationConfiguration;
    }

    public FeedIdStrategy getFeedIdStrategy() {
        return feedIdStrategy;
    }

    public Map<String, String> getImplementationConfiguration() {
        return implementationConfiguration;
    }

    public static final class Builder {
        private FeedIdStrategy strategy;
        private Map<String, String> configuration = new HashMap<>();

        private Builder() {

        }

        public Builder withFeedIdStrategy(FeedIdStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder withConfiguration(Map<String, String> configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder withConfiguration(Properties properties) {
            Map<String, String> map = new HashMap<>();
            properties.forEach((k,v) -> map.put(k.toString(), v.toString()));
            return withConfiguration(map);
        }
        public Builder addConfigurationProperty(String key, String value) {
            configuration.put(key, value);
            return this;
        }

        public Configuration build() {
            return new Configuration(strategy, configuration);
        }
    }
}
