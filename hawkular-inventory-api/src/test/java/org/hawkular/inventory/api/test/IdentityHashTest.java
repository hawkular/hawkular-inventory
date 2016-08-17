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

import static org.hawkular.inventory.paths.DataRole.OperationType.parameterTypes;
import static org.hawkular.inventory.paths.DataRole.OperationType.returnType;
import static org.hawkular.inventory.paths.DataRole.Resource.configuration;
import static org.hawkular.inventory.paths.DataRole.Resource.connectionConfiguration;
import static org.hawkular.inventory.paths.DataRole.ResourceType.configurationSchema;
import static org.hawkular.inventory.paths.DataRole.ResourceType.connectionConfigurationSchema;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.IdentityHash;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.paths.DataRole;
import org.hawkular.inventory.paths.RelativePath;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public class IdentityHashTest {

    @Test
    public void testMetricTypeHash() throws Exception {
        MetricType.Blueprint mtb =
                MetricType.Blueprint.builder(MetricDataType.GAUGE).withId("mt").withUnit(MetricUnit.NONE).build();

        InventoryStructure<MetricType.Blueprint> structure = InventoryStructure.of(mtb).build();

        String blueprintHash = IdentityHash.of(structure);

        String expectedHash = digest(mtb.getId() + mtb.getMetricDataType() + mtb.getUnit());

        Assert.assertEquals(expectedHash, blueprintHash);
    }

    @Test
    public void testResourceTypeHashWithNoAppendages() throws Exception {
        ResourceType.Blueprint rtb = ResourceType.Blueprint.builder().withId("rt").build();

        InventoryStructure<ResourceType.Blueprint> members = InventoryStructure.of(rtb).build();

        String blueprintHash = IdentityHash.of(members);

        String configSchemaHash = digest(configurationSchema + "null");
        String connSchemaHash = digest(connectionConfigurationSchema + "null");

        //nullnull is for the undefined config and conn schemas
        String expectedHash = digest(configSchemaHash + connSchemaHash + rtb.getId());

        Assert.assertEquals(expectedHash, blueprintHash);
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testResourceTypeHashWithAppendages() throws Exception {
        DataEntity.Blueprint<DataRole.ResourceType> configSchema = DataEntity.Blueprint
                .<DataRole.ResourceType>builder()
                .withRole(configurationSchema).withValue(StructuredData.get().integral(5L)).build();
        DataEntity.Blueprint<DataRole.ResourceType> connSchema = DataEntity.Blueprint.<DataRole.ResourceType>builder()
                .withRole(connectionConfigurationSchema)
                .withValue(StructuredData.get().list().addBool(true).addUndefined().build())
                .build();

        OperationType.Blueprint otb = OperationType.Blueprint.builder().withId("op").build();
        DataEntity.Blueprint<DataRole.OperationType> retType = DataEntity.Blueprint
                .<DataRole.OperationType>builder()
                .withRole(returnType)
                .withValue(StructuredData.get().integral(42L)).build();
        DataEntity.Blueprint<DataRole.OperationType> paramTypes = DataEntity.Blueprint
                .<DataRole.OperationType>builder()
                .withRole(parameterTypes)
                .withValue(StructuredData.get().string("answer")).build();

        ResourceType.Blueprint rtb = ResourceType.Blueprint.builder().withId("rt").build();

        InventoryStructure<ResourceType.Blueprint> structure = InventoryStructure.of(rtb).addChild(configSchema)
                .addChild(connSchema).startChild(otb).addChild(retType).addChild(paramTypes).end().build();

        String configSchemaHash = digest("" + configurationSchema + configSchema.getValue().toJSON());
        String connSchemaHash = digest("" + connectionConfigurationSchema + connSchema.getValue().toJSON());
        String returnTypeHash = digest("" + returnType + retType.getValue().toJSON());
        String parameterTypesHash = digest("" + parameterTypes + paramTypes.getValue()
                .toJSON());
        String operationTypeHash = digest(returnTypeHash + parameterTypesHash + otb.getId());

        String expectedHash = digest(configSchemaHash + connSchemaHash + operationTypeHash + rtb.getId());

        String blueprintHash = IdentityHash.of(structure);

        Assert.assertEquals(expectedHash, blueprintHash);
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testIdentityHashTree_ResourceTypes() throws Exception {
        DataEntity.Blueprint<DataRole.ResourceType> configSchema = DataEntity.Blueprint
                .<DataRole.ResourceType>builder()
                .withRole(configurationSchema).withValue(StructuredData.get().integral(5L)).build();
        DataEntity.Blueprint<DataRole.ResourceType> connSchema = DataEntity.Blueprint.<DataRole.ResourceType>builder()
                .withRole(connectionConfigurationSchema)
                .withValue(StructuredData.get().list().addBool(true).addUndefined().build())
                .build();

        OperationType.Blueprint otb = OperationType.Blueprint.builder().withId("op").build();
        DataEntity.Blueprint<DataRole.OperationType> retType = DataEntity.Blueprint
                .<DataRole.OperationType>builder()
                .withRole(returnType)
                .withValue(StructuredData.get().integral(42L)).build();
        DataEntity.Blueprint<DataRole.OperationType> paramTypes = DataEntity.Blueprint
                .<DataRole.OperationType>builder()
                .withRole(parameterTypes)
                .withValue(StructuredData.get().string("answer")).build();

        ResourceType.Blueprint rtb = ResourceType.Blueprint.builder().withId("rt").build();

        InventoryStructure<ResourceType.Blueprint> structure = InventoryStructure.of(rtb).addChild(configSchema)
                .addChild(connSchema).startChild(otb).addChild(retType).addChild(paramTypes).end().build();

        String configSchemaHash = digest("" + configurationSchema + configSchema.getValue().toJSON());
        String connSchemaHash = digest("" + connectionConfigurationSchema + connSchema.getValue().toJSON());
        String returnTypeHash = digest("" + returnType + retType.getValue().toJSON());
        String parameterTypesHash = digest("" + parameterTypes + paramTypes.getValue()
                .toJSON());
        String operationTypeHash = digest(returnTypeHash + parameterTypesHash + otb.getId());

        String resourceTypeHash = digest(configSchemaHash + connSchemaHash + operationTypeHash + rtb.getId());

        IdentityHash.Tree treeHash = IdentityHash.treeOf(structure);

        Assert.assertEquals(resourceTypeHash, treeHash.getHash());
        Assert.assertEquals(RelativePath.empty().get(), treeHash.getPath());
        Assert.assertEquals(3, treeHash.getChildren().size());

        Assert.assertTrue(treeHash.getChildren().stream()
                .filter(c -> RelativePath.to().operationType("op").get().equals(c.getPath()))
                .filter(c -> operationTypeHash.equals(c.getHash()))
                .findFirst().isPresent());

        Assert.assertTrue(treeHash.getChildren().stream()
                .filter(c -> RelativePath.to().dataEntity(configurationSchema).get().equals(c.getPath()))
                .filter(c -> configSchemaHash.equals(c.getHash()))
                .findFirst().isPresent());

        Assert.assertTrue(treeHash.getChildren().stream()
                .filter(c -> RelativePath.to().dataEntity(connectionConfigurationSchema).get().equals(c.getPath()))
                .filter(c -> connSchemaHash.equals(c.getHash()))
                .findFirst().isPresent());

        Assert.assertTrue(treeHash.getChildren().stream()
                .filter(c -> RelativePath.to().operationType("op").get().equals(c.getPath()))
                .flatMap(t -> t.getChildren().stream())
                .filter(c -> RelativePath.to().operationType("op").data(returnType).get().equals(c.getPath()))
                .filter(c -> returnTypeHash.equals(c.getHash()))
                .findFirst().isPresent());

        Assert.assertTrue(treeHash.getChildren().stream()
                .filter(c -> RelativePath.to().operationType("op").get().equals(c.getPath()))
                .flatMap(t -> t.getChildren().stream())
                .filter(c -> RelativePath.to().operationType("op").data(parameterTypes).get().equals(c.getPath()))
                .filter(c -> parameterTypesHash.equals(c.getHash()))
                .findFirst().isPresent());
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testIdentityHashTree_Resources() throws Exception {
        Resource.Blueprint rb = Resource.Blueprint.builder().withId("res").withResourceTypePath("../rt;RT").build();
        Resource.Blueprint crb =
                Resource.Blueprint.builder().withId("childRes").withResourceTypePath("../../rt;RT").build();

        DataEntity.Blueprint conf = DataEntity.Blueprint.builder().withRole(configuration)
                .withValue(StructuredData.get().integral(42L)).build();

        InventoryStructure<Resource.Blueprint> structure = InventoryStructure.of(rb).addChild(conf).addChild(crb)
                .build();

        String confHash = digest("" + configuration + conf.getValue().toJSON());
        String dummyConnConfHash = digest("" + connectionConfiguration +
                dummyDataBlueprint(connectionConfiguration).getValue().toJSON());
        String dummyconfHash = digest("" + configuration + dummyDataBlueprint(configuration).getValue().toJSON());
        String childHash = digest(dummyconfHash + dummyConnConfHash + crb.getId());
        String resourceHash = digest(confHash + dummyConnConfHash + childHash + rb.getId());

        IdentityHash.Tree treeHash = IdentityHash.treeOf(structure);

        Assert.assertEquals(resourceHash, treeHash.getHash());
        Assert.assertEquals(RelativePath.empty().get(), treeHash.getPath());
        //we get a "dummy" hash for non-existant configurations
        Assert.assertEquals(3, treeHash.getChildren().size());

        Assert.assertTrue(treeHash.getChildren().stream()
                .filter(c -> RelativePath.to().resource("childRes").get().equals(c.getPath()))
                .filter(c -> childHash.equals(c.getHash()))
                .findFirst().isPresent());

        Assert.assertTrue(treeHash.getChildren().stream()
                .filter(c -> RelativePath.to().dataEntity(configuration).get().equals(c.getPath()))
                .filter(c -> confHash.equals(c.getHash()))
                .findFirst().isPresent());
    }

    private String digest(String content) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(content.getBytes(Charset.forName("UTF-8")));

        StringBuilder bld = new StringBuilder();
        for (byte b : digest) {
            bld.append(Integer.toHexString(Byte.toUnsignedInt(b)));
        }

        return bld.toString();
    }

    private static <R extends DataRole> DataEntity.Blueprint<R> dummyDataBlueprint(R role) {
        return DataEntity.Blueprint.<R>builder().withRole(role).withValue(StructuredData.get().undefined()).build();
    }
}
