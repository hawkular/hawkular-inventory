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

import static org.hawkular.inventory.api.ResourceTypes.DataRole.configurationSchema;
import static org.hawkular.inventory.api.ResourceTypes.DataRole.connectionConfigurationSchema;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.hawkular.inventory.api.OperationTypes;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.IdentityHash;
import org.hawkular.inventory.api.model.MetadataPack;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public class ContentHashTest {

    @Test
    public void testMetricTypeHash() throws Exception {
        MetricType.Blueprint mtb =
                MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("mt").withUnit(MetricUnit.NONE).build();

        MetadataPack.Members members = MetadataPack.Members.builder().with(mtb).build();

        String blueprintHash = IdentityHash.of(members);

        String expectedHash = digest(mtb.getId() + mtb.getType() + mtb.getUnit());

        Assert.assertEquals(expectedHash, blueprintHash);
    }

    @Test
    public void testResourceTypeHashWithNoAppendages() throws Exception {
        ResourceType.Blueprint rtb = ResourceType.Blueprint.builder().withId("rt").build();

        MetadataPack.Members members = MetadataPack.Members.builder().with(rtb).done().build();

        String blueprintHash = IdentityHash.of(members);

        String expectedHash = digest(rtb.getId() + "nullnull"); //nullnull is for the undefined config and conn schemas

        Assert.assertEquals(expectedHash, blueprintHash);
    }

    @Test
    public void testResourceTypeHashWithAppendages() throws Exception {
        DataEntity.Blueprint<ResourceTypes.DataRole> configSchema = DataEntity.Blueprint
                .<ResourceTypes.DataRole>builder()
                .withRole(configurationSchema).withValue(StructuredData.get().integral(5L)).build();
        DataEntity.Blueprint<ResourceTypes.DataRole> connSchema = DataEntity.Blueprint.<ResourceTypes.DataRole>builder()
                .withRole(connectionConfigurationSchema)
                .withValue(StructuredData.get().list().addBool(true).addUndefined().build())
                .build();

        OperationType.Blueprint otb = OperationType.Blueprint.builder().withId("op").build();
        DataEntity.Blueprint<OperationTypes.DataRole> returnType = DataEntity.Blueprint
                .<OperationTypes.DataRole>builder()
                .withRole(OperationTypes.DataRole.returnType)
                .withValue(StructuredData.get().integral(42L)).build();
        DataEntity.Blueprint<OperationTypes.DataRole> parameterTypes = DataEntity.Blueprint
                .<OperationTypes.DataRole>builder()
                .withRole(OperationTypes.DataRole.parameterTypes)
                .withValue(StructuredData.get().string("answer")).build();

        ResourceType.Blueprint rtb = ResourceType.Blueprint.builder().withId("rt").build();

        MetadataPack.Members members = MetadataPack.Members.builder().with(rtb).with(configSchema)
                .with(connSchema).with(otb).with(returnType).with(parameterTypes).done().done().build();

        String expectedHash = digest(rtb.getId() + configSchema.getValue().toJSON() + connSchema.getValue().toJSON()
                + otb.getId() + returnType.getValue().toJSON() + parameterTypes.getValue().toJSON());

        String blueprintHash = IdentityHash.of(members);

        Assert.assertEquals(expectedHash, blueprintHash);
    }

    private String digest(String content) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(content.getBytes(Charset.forName("UTF-8")));

        StringBuilder bld = new StringBuilder();
        for (byte b : digest) {
            bld.append(Integer.toHexString(Byte.toUnsignedInt(b)));
        }

        return bld.toString();
    }

}
