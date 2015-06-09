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
package org.hawkular.inventory.impl.tinkerpop.test;

import com.tinkerpop.blueprints.Element;
import org.hawkular.inventory.api.test.AbstractLazyInventoryPersistenceCheck;
import org.hawkular.inventory.impl.tinkerpop.lazy.TinkerpopInventory;
import org.hawkular.inventory.lazy.LazyInventory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public class LazyTest extends AbstractLazyInventoryPersistenceCheck<Element> {
    @Override
    protected LazyInventory<Element> instantiateNewInventory() {
        return new TinkerpopInventory();
    }

    @Override
    protected void destroyStorage() throws IOException {
        Path path = Paths.get("target", "__tinker.graph");

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
}
