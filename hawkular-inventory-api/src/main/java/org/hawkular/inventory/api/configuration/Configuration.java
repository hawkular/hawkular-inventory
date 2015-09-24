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

package org.hawkular.inventory.api.configuration;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.hawkular.inventory.api.FeedIdStrategy;
import org.hawkular.inventory.api.ResultFilter;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class Configuration {
    private final FeedIdStrategy feedIdStrategy;
    private final Map<String, String> implementationConfiguration;
    private final ResultFilter resultFilter;

    public static Builder builder() {
        return new Builder();
    }

    public Configuration(FeedIdStrategy feedIdStrategy, ResultFilter resultFilter,
                         Map<String, String> implementationConfiguration) {
        this.feedIdStrategy = feedIdStrategy;
        this.resultFilter = resultFilter;
        this.implementationConfiguration = implementationConfiguration;
    }

    public FeedIdStrategy getFeedIdStrategy() {
        return feedIdStrategy;
    }

    /**
     * @return the result filter to be used to filter the query results or null if none necessary
     */
    public ResultFilter getResultFilter() {
        return resultFilter;
    }

    /**
     * Returns the value of the property.
     *
     * <p>If there is a system property with the name from {@link Property#getSystemPropertyNames()} ()}, then its value
     * is returned, otherwise if the property defines an environment variable and that env variable exists, then its
     * value is returned otherwise the value of a property with a name from {@link Property#getPropertyName()}
     * from the values loaded using {@link Builder#withConfiguration(Map)} or
     * {@link Builder#withConfiguration(Properties)} is returned.
     *
     * @param property the property to obtain
     * @return the value or null if none found
     */
    public String getProperty(Property property, String defaultValue) {
        String value = null;

        List<String> systemProps = property.getSystemPropertyNames();

        if (systemProps != null) {
            for (String sp : systemProps) {
                value = System.getProperty(sp);
                if (value != null) {
                    break;
                }
            }

        }

        if (value == null) {
            List<String> envVars = property.getEnvironmentVariableNames();
            if (envVars != null) {
                for (String ev : envVars) {
                    value = System.getenv(ev);
                    if (value != null) {
                        break;
                    }
                }
            }
        }

        if (value == null && property.getPropertyName() != null) {
            value = implementationConfiguration.get(property.getPropertyName());
        }

        return value == null ? defaultValue : value;
    }

    public boolean getFlag(Property property, String defaultValue) {
        return Boolean.valueOf(getProperty(property, defaultValue));
    }

    public boolean getFlag(String key, String defaultValue) {
        return getFlag(Property.builder().withPropertyNameAndSystemProperty(key).build(), defaultValue);
    }

    /**
     * Returns the implementation configuration with the values changed to conform the provided properties.
     * <p>
     * I.e. if a system property defined by some of the provided property objects overrides a property in the
     * implementation configuration, the value of the system property is used instead for that property (i.e. the key
     * stays the same but the value gets overridden to the value of the system property).
     *
     * @param overridableProperties the properties to override the implementation configuration with.
     * @return an overridden implementation configuration
     */
    public Map<String, String> getImplementationConfiguration(Collection<? extends Property> overridableProperties) {
        return getOverriddenImplementationConfiguration(overridableProperties);
    }

    //this hoop is needed so that we can type-check the "T" through the stream manipulations
    private <T extends Property> Map<String, String> getOverriddenImplementationConfiguration(
            Collection<T> overridableProperties) {
        Map<String, T> propsByName = overridableProperties.stream().collect(toMap(Property::getPropertyName,
                identity()));

        return implementationConfiguration.entrySet().stream().filter(e -> !e.getKey().startsWith
                (RestConfiguration.PROPERTY_PREFIX)).collect(toMap(Map.Entry::getKey, e -> {
            Property p = propsByName.get(e.getKey());
            return p == null ? e.getValue() : getProperty(p, e.getValue());
        }));
    }

    public static final class Builder {
        private FeedIdStrategy strategy;
        private ResultFilter resultFilter;
        private Map<String, String> configuration = new HashMap<>();

        private Builder() {

        }

        public Builder withFeedIdStrategy(FeedIdStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder withResultFilter(ResultFilter resultFilter) {
            this.resultFilter = resultFilter;
            return this;
        }

        public Builder withConfiguration(Map<String, String> configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder withConfiguration(Properties properties) {
            Map<String, String> map = new HashMap<>();
            properties.forEach((k, v) -> map.put(k.toString(), v.toString()));
            return withConfiguration(map);
        }

        public Builder addConfigurationProperty(String key, String value) {
            configuration.put(key, value);
            return this;
        }

        public Configuration build() {
            return new Configuration(strategy, resultFilter, configuration);
        }
    }

    /**
     * Represents a single configuration property. A property can be read either using a property name from a file,
     * a system property name (with potentially different name from the one used in the file) or potentially from an
     * environment variable.
     *
     * <p>See {@link Configuration#getProperty(Property, String)} for how the value of a property is resolved.
     */
    public interface Property {
        /**
         * @return the name of the property
         */
        String getPropertyName();

        /**
         * @return the names of the system properties to try.
         */
        List<String> getSystemPropertyNames();

        /**
         * @return the names of an environment variables to try. By default this is empty, meaning there is no env
         * variable to use for this property.
         */
        default List<String> getEnvironmentVariableNames() {
            return Collections.emptyList();
        }

        static Builder builder() {
            return new Builder();
        }

        class Builder {
            private String propertyName;
            private List<String> sysProps;
            private List<String> envVars;

            public Builder withPropertyName(String propertyName) {
                this.propertyName = propertyName;
                return this;
            }

            public Builder withPropertyNameAndSystemProperty(String propertyName) {
                return withPropertyName(propertyName).withSystemProperties(propertyName);
            }

            public Builder withSystemProperties(String... sysProps) {
                this.sysProps = Arrays.asList(sysProps);
                return this;
            }

            public Builder withEnvironmentVariables(String... envVars) {
                this.envVars = Arrays.asList(envVars);
                return this;
            }

            public Property build() {
                return new Property() {
                    //capture the values in the builder as they are at this very moment - further changes to the builder
                    //won't affect the constructed instance
                    private final String propertyName = Builder.this.propertyName;
                    private final List<String> sysProps = Collections.unmodifiableList(
                            new ArrayList<>(Builder.this.sysProps));
                    private final List<String> envVars = Collections.unmodifiableList(
                            new ArrayList<>(Builder.this.envVars));

                    @Override
                    public String getPropertyName() {
                        return propertyName;
                    }

                    @Override
                    public List<String> getSystemPropertyNames() {
                        return sysProps;
                    }

                    @Override
                    public List<String> getEnvironmentVariableNames() {
                        return envVars;
                    }
                };
            }
        }
    }
}
