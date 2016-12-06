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
package org.hawkular.inventory.impl.cassandra;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import rx.Observable;

/**
 * @author Joel Takvorian
 */
public class TransitiveClosureProcessorTest {

    @Test
    public void shouldProcessTree() {
        Map<String, Observable<String>> dataSet = ImmutableMap.<String, Observable<String>>builder()
                .put("conn_1", Observable.just("conn_2"))
                .put("conn_2", Observable.just("conn_3", "conn_4"))
                .put("conn_3", Observable.just("conn_5"))
                .put("conn_4", Observable.just("conn_6"))
                .put("conn_6", Observable.just("conn_7"))
                .put("unco_1", Observable.just("unco_2"))
                .put("unco_2", Observable.just("conn_4"))
                .build();
        TransitiveClosureProcessor runner =
                new TransitiveClosureProcessor(cp -> dataSet.getOrDefault(cp, Observable.empty()));
        List<String> transitiveClosure = runner.process("conn_1").toList().toBlocking().first();
        Assert.assertEquals(7, transitiveClosure.size());
        Assert.assertEquals("conn_1", transitiveClosure.get(0));
        Assert.assertEquals("conn_2", transitiveClosure.get(1));
        Assert.assertEquals("conn_3", transitiveClosure.get(2));
        Assert.assertEquals("conn_4", transitiveClosure.get(3));
        Assert.assertEquals("conn_5", transitiveClosure.get(4));
        Assert.assertEquals("conn_6", transitiveClosure.get(5));
        Assert.assertEquals("conn_7", transitiveClosure.get(6));
    }

    @Test
    public void shouldProcessWithCycle() {
        Map<String, Observable<String>> dataSet = ImmutableMap.<String, Observable<String>>builder()
                .put("conn_1", Observable.just("conn_2"))
                .put("conn_2", Observable.just("conn_3"))
                .put("conn_3", Observable.just("conn_1"))
                .put("unco_1", Observable.just("unco_2"))
                .put("unco_2", Observable.just("conn_3"))
                .build();
        TransitiveClosureProcessor runner =
                new TransitiveClosureProcessor(cp -> dataSet.getOrDefault(cp, Observable.empty()));
        List<String> transitiveClosure = runner.process("conn_1").toList().toBlocking().first();
        Assert.assertEquals(3, transitiveClosure.size());
        Assert.assertEquals("conn_1", transitiveClosure.get(0));
        Assert.assertEquals("conn_2", transitiveClosure.get(1));
        Assert.assertEquals("conn_3", transitiveClosure.get(2));
    }
}