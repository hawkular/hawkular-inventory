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
package org.hawkular.inventory.cdi;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public class TokenReplacingReaderTest {

    @Test
    public void testSimple() throws Exception {
        testExpression("Hello, ${who}!").with("who", "world").matches("Hello, world!");
    }

    @Test
    public void testTokensInValues() throws Exception {
        testExpression("Hello, ${who}!").with("who", "${whose} world").with("whose", "my").matches("Hello, my world!");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInfiniteRecursionAvoidance() throws Exception {
        testExpression("Hello, ${who}!").with("who", "${who}").parse();
    }

    @Test
    public void testDefaultValue() throws Exception {
        testExpression("Hello, ${who:world}!").matches("Hello, world!");
    }

    @Test
    public void testMultipleChoice() throws Exception {
        testExpression("Hello, ${blah,who}!").with("who", "world").matches("Hello, world!");
    }

    @Test
    public void testMultipleChoiceWithDefaultValue() throws Exception {
        testExpression("Hello, ${blah,who:world}!").matches("Hello, world!");
    }

    @Test
    public void testEscapesInFreeTextAndNames() throws Exception {
        testExpression("Hel\\lo, ${who\\} \\${escaped}!").with("who\\", "world").matches("Hel\\lo, world ${escaped}!");
    }

    @Test
    public void testEscapesInDefaultValues() throws Exception {
        testExpression("Hello, ${who:world\\}}!").matches("Hello, world}!");
        testExpression("Hello, ${who:world\\}s}!").matches("Hello, world}s!");
        testExpression("Hello, ${who:\\}worlds}!").matches("Hello, }worlds!");
    }

    @Test
    public void testColonAsName() throws Exception {
        testExpression("Hello, ${:}!").with(":", "world").matches("Hello, world!");
        testExpression("Hello, ${:who}!").with(":who", "world").matches("Hello, world!");
    }

    @Test
    public void testDefaultValueInNestedProp() throws Exception {
        testExpression("Hello, ${who}!").with("who", "${guess:world}").matches("Hello, world!");
    }

    private TestCaseBuilder testExpression(String expression) {
        return new TestCaseBuilder(expression);
    }

    private static class TestCaseBuilder {
        final Map<String, String> tokens = new HashMap<>();
        final String expression;

        private TestCaseBuilder(String expression) {
            this.expression = expression;
        }

        public TestCaseBuilder with(String key, String value) {
            tokens.put(key, value);
            return this;
        }

        public String parse() throws IOException {
            TokenReplacingReader rdr = new TokenReplacingReader(new StringReader(expression), tokens);
            int c;
            StringBuilder bld = new StringBuilder();
            while ((c = rdr.read()) >= 0) {
                bld.append((char) c);
            }

            return bld.toString();
        }

        public void matches(String expectedParseResult) throws IOException {
            Assert.assertEquals(expectedParseResult, expectedParseResult, parse());
        }
    }
}
