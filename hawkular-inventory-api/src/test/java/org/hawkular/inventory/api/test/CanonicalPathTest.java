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
package org.hawkular.inventory.api.test;

import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.paths.CanonicalPath;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public class CanonicalPathTest {

    @Test
    public void testEntityInstantiationWithWrongPath() throws Exception {
        try {
            new Environment(CanonicalPath.of().tenant("t").get());
            Assert.fail("Creating an entity with a path pointing to a different entity type should not be possible.");
        } catch (IllegalArgumentException e) {
            //good
        }
    }

    // other tests moved to org.hawkular.inventory.paths.CanonicalPathTest
}
