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
package org.hawkular.inventory.rest.test;

import static org.junit.Assert.assertEquals;

import org.hawkular.inventory.api.model.Environment;
import org.junit.Test;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class PermissiveSecurityITest extends AbstractTestBase {

    @Test
    public void failWithoutTenantHeader() throws Throwable {

        /* First make sure inventory has started through checking the test env is there
         * This also ensures that the access is granted with the tenantId set by newAuthRequest() */
        String path = "/hawkular/inventory/environments/" + testEnvId;
        Environment env = getWithRetries(path, Environment.class, 10, 2000);
        assertEquals("Unable to get the '" + testEnvId + "' environment.", testEnvId, env.getId());

        /* Now do the same without the tenantId header */
        String url = baseURI + path;
        Request request = new Request.Builder()//
                .addHeader("Authorization", authHeader)//
                .addHeader("Accept", "application/json")//
                .url(url).build();
        Response response = client.newCall(request).execute();
        assertEquals("Accessing Inventory without Hawkular-Tenant header should not be possible.", 400,
                response.code());
    }

}
