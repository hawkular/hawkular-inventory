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
package org.hawkular.inventory.impl.tinkerpop.provider;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.test.AbstractBaseInventoryTestsuite;
import org.hawkular.inventory.base.BaseInventory;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
abstract class AbstractTinkerGraphTest extends AbstractBaseInventoryTestsuite<Element> {

    protected static void teardown(BaseInventory<?> inventory) throws Exception {
        String pathName = inventory.getConfiguration().getProperty(new DirProperty(), null);

        Path path = Paths.get(pathName);

        if (!path.toFile().exists()) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class DirProperty implements Configuration.Property {

        @Override public String getPropertyName() {
            return "blueprints.tg.directory";
        }

        @Override public List<String> getSystemPropertyNames() {
            return Collections.emptyList();
        }
    }
}
