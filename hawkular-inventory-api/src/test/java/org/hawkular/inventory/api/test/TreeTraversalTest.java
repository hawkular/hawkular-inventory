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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hawkular.inventory.api.TreeTraversal;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.15.0
 */
public class TreeTraversalTest {

    private final Node tree = new Node(-1);
    {
        //      -1
        //     / | \
        //    0  1  2
        //   /|  |
        //  3 4  5
        //       |
        //       6
        tree.children.add(new Node(0));
        tree.children.add(new Node(1));
        tree.children.add(new Node(2));

        tree.children.get(0).children.add(new Node(3));
        tree.children.get(0).children.add(new Node(4));
        tree.children.get(1).children.add(new Node(5));
        tree.children.get(1).children.get(0).children.add(new Node(6));
    }

    @Test
    public void testDfs() throws Exception {
        TreeTraversal<Node> tr = new TreeTraversal<>(n -> n.children.iterator());

        List<Integer> order = new ArrayList<>();

        tr.depthFirst(tree, n -> order.add(n.id));

        Assert.assertEquals(Arrays.asList(-1, 0, 3, 4, 1, 5, 6, 2), order);
    }

    @Test
    public void testBfs() throws Exception {
        TreeTraversal<Node> tr = new TreeTraversal<>(n -> n.children.iterator());

        List<Integer> order = new ArrayList<>();

        tr.breadthFirst(tree, n -> order.add(n.id));

        Assert.assertEquals(Arrays.asList(-1, 0, 1, 2, 3, 4, 5, 6), order);
    }

    private static final class Node {
        final int id;
        final List<Node> children = new ArrayList<>();

        public Node(int id) {
            this.id = id;
        }
    }
}
