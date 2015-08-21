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
package org.hawkular.inventory.api.test;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.StructuredData;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.3.0
 */
public class StructuredDataTest {

    private static final CanonicalPath owner = CanonicalPath.of().tenant("t").environment("e").resource("r").get();

    private StructuredData bool = StructuredData.get().bool(true);
    private StructuredData integral = StructuredData.get().integral(42L);
    private StructuredData floatingPoint = StructuredData.get().floatingPoint(1.0);
    private StructuredData string = StructuredData.get().string("kachny");
    private StructuredData list = StructuredData.get().list().addBool(true).addIntegral(1L).build();
    private StructuredData map = StructuredData.get().map().putBool("bool", true)
            .putIntegral("int", 1L).build();
    private StructuredData listInList = StructuredData.get().list().addList().addBool(true)
            .closeList().build();
    private StructuredData mapInList = StructuredData.get().list().addMap()
            .putIntegral("answer", 42L).closeMap().build();
    private StructuredData listInMap = StructuredData.get().map().putList("answer")
            .addIntegral(42L).closeList().build();
    private StructuredData mapInMap = StructuredData.get().map().putMap("answer")
            .putIntegral("answer, really", 42L).closeMap().build();

    @SuppressWarnings("unchecked")
    @Test
    public void testBuilder() throws Exception {
        assertEquals(42L, integral.getValue());
        assertEquals(1.0, floatingPoint.getValue());
        assertEquals("kachny", string.getValue());
        assertEquals(Boolean.TRUE, ((List<StructuredData>) list.getValue()).get(0).getValue());
        assertEquals(1L, ((List<StructuredData>) list.getValue()).get(1).getValue());
        assertEquals(Boolean.TRUE, ((Map<String, StructuredData>) map.getValue()).get("bool").getValue());
        assertEquals(1L, ((Map<String, StructuredData>) map.getValue()).get("int").getValue());

        // yes, this is why visitors are better ;)
        assertEquals(Boolean.TRUE,
                (((List<StructuredData>) (((List<StructuredData>) listInList.getValue()).get(0).getValue())).get(0)
                        .getValue()));

        assertEquals(42L,
                (((Map<String, StructuredData>) (((List<StructuredData>) mapInList.getValue()).get(0).getValue()))
                        .get("answer").getValue()));

        assertEquals(42L,
                (((List<StructuredData>) (((Map<String, StructuredData>) listInMap.getValue())
                        .get("answer").getValue())).get(0).getValue()));

        assertEquals(42L,
                (((Map<String, StructuredData>) (((Map<String, StructuredData>) mapInMap.getValue())
                        .get("answer").getValue())).get("answer, really").getValue()));

    }

    @Test
    public void testVisitors() throws Exception {
        bool.accept(StructuredData.Visitor.bool((b, p) -> {
            assertEquals(true, b);
            return null;
        }), null);

        integral.accept(StructuredData.Visitor.integral((i, p) -> {
            assertEquals((Long) 42L, i);
            return null;
        }), null);

        floatingPoint.accept(StructuredData.Visitor.floatingPoint((f, p) -> {
            assertEquals((Double) 1.0, f);
            return null;
        }), null);

        string.accept(StructuredData.Visitor.string((s, p) -> {
            assertEquals("kachny", s);
            return null;
        }), null);

        list.accept(new StructuredData.Visitor.Simple<Void, Void>() {
            @Override
            public Void visitList(List<StructuredData> value, Void parameter) {
                value.forEach((v) -> v.accept(this, null));
                return null;
            }

            @Override
            public Void visitIntegral(long value, Void parameter) {
                assertEquals(1L, value);
                return null;
            }
        }, null);

        mapInList.accept(new StructuredData.Visitor.Simple<Void, Void>() {
            @Override
            public Void visitList(List<StructuredData> value, Void parameter) {
                value.forEach((v) -> v.accept(this, null));
                return null;
            }

            @Override
            public Void visitIntegral(long value, Void parameter) {
                assertEquals(42L, value);
                return null;
            }

            @Override
            public Void visitMap(Map<String, StructuredData> value, Void parameter) {
                value.get("answer").accept(this, null);
                return null;
            }
        }, null);

        listInMap.accept(new StructuredData.Visitor.Simple<Void, Void>() {
            @Override
            public Void visitList(List<StructuredData> value, Void parameter) {
                value.forEach((v) -> v.accept(this, null));
                return null;
            }

            @Override
            public Void visitIntegral(long value, Void parameter) {
                assertEquals(42L, value);
                return null;
            }

            @Override
            public Void visitMap(Map<String, StructuredData> value, Void parameter) {
                if (!value.containsKey("answer")) {
                    Assert.fail();
                }
                value.get("answer").accept(this, null);
                return null;
            }
        }, null);

        mapInMap.accept(new StructuredData.Visitor.Simple<Void, Void>() {
            @Override
            public Void visitIntegral(long value, Void parameter) {
                assertEquals(42L, value);
                return null;
            }

            @Override
            public Void visitMap(Map<String, StructuredData> value, Void parameter) {
                if (value.containsKey("answer")) {
                    value.get("answer").accept(this, null);
                } else if (value.containsKey("answer, really")) {
                    value.get("answer, really").accept(this, null);
                } else {
                    Assert.fail();
                }
                return null;
            }
        }, null);
    }

    @Test
    @SuppressWarnings("AssertEqualsBetweenInconvertibleTypes")
    public void testModification() throws Exception {
        assertEquals(false, bool.update().toBool(false).getValue());
        assertEquals(2D, bool.update().toFloatingPoint(2).getValue());
        assertEquals(1L, bool.update().toIntegral(1).getValue());
        assertEquals("kachny", bool.update().toString("kachny").getValue());

        assertEquals(Collections.emptyList(), bool.update().toList().build().getValue());
        assertEquals(StructuredData.get().list().addBool(true).addIntegral(1).build(),
                bool.update().toList().addBool(true).addIntegral(1).build());

        assertEquals(Collections.emptyMap(), bool.update().toMap().build().getValue());
        assertEquals(StructuredData.get().map().putBool("true", true).putString("str", "str").build(),
                bool.update().toMap().putBool("true", true).putString("str", "str").build());

        assertEquals(list, list.update().toList().build());
        assertEquals(StructuredData.get().list().addBool(true).addString("str").build(),
                list.update().toList().setString(1, "str").build());
        assertEquals(StructuredData.get().list().addBool(true).addIntegral(1).addUndefined().build(),
                list.update().toList().addUndefined().build());
        assertEquals(StructuredData.get().list().build(), list.update().toList().clear().build());
        assertEquals(StructuredData.get().list().addIntegral(1).build(),
                list.update().toList().remove(0).build());

        assertEquals(listInList, listInList.update().toList().build());
        assertEquals(StructuredData.get().list().addList().addUndefined().closeList().build(),
                listInList.update().toList().updateList(0).setUndefined(0).closeList().build());
        assertEquals(StructuredData.get().list().addList().addBool(true).addBool(false).closeList().build(),
                listInList.update().toList().updateList(0).addBool(false).closeList().build());

        assertEquals(map, map.update().toMap().build());
        assertEquals(StructuredData.get().map().putBool("bool", true).putString("int", "int").build(),
                map.update().toMap().putString("int", "int").build());
        assertEquals(StructuredData.get().map().build(), map.update().toMap().clear().build());
        assertEquals(StructuredData.get().map().putBool("bool", true).build(),
                map.update().toMap().remove("int").build());

        assertEquals(StructuredData.get().map().putBool("bool", true).putIntegral("int", 1)
                        .putMap("new key").putString("p1", "a1").closeMap().build(),
                map.update().toMap().updateMap("new key").putString("p1", "a1").closeMap().build());
    }
}
