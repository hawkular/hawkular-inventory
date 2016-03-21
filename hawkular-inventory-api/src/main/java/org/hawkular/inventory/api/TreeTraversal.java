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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Function;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
public final class TreeTraversal<Node> {

    private final Function<Node, Iterator<Node>> childrenExtractor;

    public TreeTraversal(Function<Node, Iterator<Node>> childrenExtractor) {
        this.childrenExtractor = childrenExtractor;
    }

    public void depthFirst(Node node, Function<Node, Boolean> visitor) {
        Memory<Iterator<Node>> stack = new Memory<Iterator<Node>>() {
            private final Deque<Iterator<Node>> stack = new ArrayDeque<>();

            @Override public void store(Iterator<Node> data) {
                stack.addFirst(data);
            }

            @Override public Iterator<Node> getCurrent() {
                return stack.peekFirst();
            }

            @Override public void removeCurrent() {
                stack.removeFirst();
            }

            @Override public boolean hasData() {
                return !stack.isEmpty();
            }
        };

        traverse(node, stack, visitor);
    }

    public void breadthFirst(Node node, Function<Node, Boolean> visitor) {
        Memory<Iterator<Node>> queue = new Memory<Iterator<Node>>() {
            private final Deque<Iterator<Node>> queue = new ArrayDeque<>();

            @Override public void store(Iterator<Node> data) {
                queue.addLast(data);
            }

            @Override public Iterator<Node> getCurrent() {
                return queue.peekFirst();
            }

            @Override public void removeCurrent() {
                queue.removeFirst();
            }

            @Override public boolean hasData() {
                return !queue.isEmpty();
            }
        };

        traverse(node, queue, visitor);
    }

    private void traverse(Node node, Memory<Iterator<Node>> memory, Function<Node, Boolean> visitor) {
        if (!visitor.apply(node)) {
            return;
        }

        Iterator<Node> children = childrenExtractor.apply(node);

        memory.store(children);

        while (memory.hasData()) {
            Iterator<Node> it = memory.getCurrent();

            if (!it.hasNext()) {
                memory.removeCurrent();
                continue;
            }

            node = it.next();
            if (visitor.apply(node)) {
                children = childrenExtractor.apply(node);
                memory.store(children);
            }
        }
    }

    private interface Memory<T> {
        void store(T data);

        T getCurrent();

        void removeCurrent();

        boolean hasData();
    }
}
