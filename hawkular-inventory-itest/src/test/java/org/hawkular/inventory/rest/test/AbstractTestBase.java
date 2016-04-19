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

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.junit.Assert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class AbstractTestBase {

    protected static final String testUser = "jdoe";
    protected static final String testPasword = "password";
    protected static String authHeader;
    protected static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    protected static TypeReference<Map<String, String>> mapTypeRef = new TypeReference<Map<String, String>>() {
    };

    protected static final String baseURI;

    static {
        String host = System.getProperty("hawkular.bind.address", "localhost");
        if ("0.0.0.0".equals(host)) {
            host = "localhost";
        }
        int portOffset = Integer.parseInt(System.getProperty("hawkular.port.offset", "0"));
        int httpPort = portOffset + 8080;
        baseURI = "http://" + host + ":" + httpPort;

        authHeader = Credentials.basic(testUser, testPasword);
    }

    protected static OkHttpClient client;
    protected static ObjectMapper mapper;

    static {
        client = new OkHttpClient();
        if (Boolean.parseBoolean(System.getProperty("http.log.wire", "false"))) {
            client.interceptors().add(new LoggingInterceptor());
        }
        client.setConnectTimeout(60, TimeUnit.SECONDS);
        client.setReadTimeout(60, TimeUnit.SECONDS);
        client.setWriteTimeout(60, TimeUnit.SECONDS);

        mapper = new ObjectMapper();
        AnnotationIntrospector jacksonIntrospector = new JacksonAnnotationIntrospector();
        AnnotationIntrospector jaxbIntrospector = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        AnnotationIntrospector introspectorPair =
                new AnnotationIntrospectorPair(jacksonIntrospector, jaxbIntrospector);
        mapper.setAnnotationIntrospector(introspectorPair);
        InventoryJacksonConfig.configure(mapper);

    }

    protected Response delete(String path) throws IOException {
        String url = baseURI + path;
        System.out.println("deleting "+ url);
        Response response = client.newCall(newAuthRequest()
                .url(url).delete().build()).execute();
        Assert.assertEquals(204, response.code());
        return response;
    }

    protected static Request.Builder newAuthRequest() {
        return new Request.Builder()//
                .addHeader("Authorization", authHeader)//
                .addHeader("Accept", "application/json");
    }

    protected static Response postNew(String path, Object payload) throws Throwable {
        String json = mapper.writeValueAsString(payload);
        Response response = post(path, json);
        Assert.assertEquals("Response msg: " + response.message(), 201, response.code());
        return response;
    }

    protected static Response post(String path, String payload) throws Throwable {
        String url = baseURI + path;
        //System.out.println("posting to "+ url + " payload ="+ payload +"=");
        System.out.println("posting "+ url);
        Request request =
                newAuthRequest().url(url).post(RequestBody.create(MEDIA_TYPE_JSON, payload)).build();
        Response response = client.newCall(request).execute();
        return response;
    }

    protected static <T> T getWithRetries(String path, Class<T> type, int attemptCount, long attemptDelay)
            throws Throwable {
        String json = getWithRetries(path, attemptCount, attemptDelay);
        return mapper.readValue(json, type);
    }

    protected static Response get(String path, String... query) throws Throwable {
        String url = baseURI + path;
        for (int i = 0; i < query.length; ) {
            url += i == 0 ? "?" : "&";
            String key = query[i++];
            String value = query[i++];
            url += URLEncoder.encode(key, "utf-8") + "=" + URLEncoder.encode(value, "utf-8");
        }
        Request request = newAuthRequest().url(url).build();
        return client.newCall(request).execute();
    }

    protected static String getWithRetries(String path, int attemptCount, long attemptDelay) throws Throwable {
        Throwable e = null;
        String url = baseURI + path;
        for (int i = 0; i < attemptCount; i++) {
            try {
                Request request = newAuthRequest().url(url).build();
                Response response = client.newCall(request).execute();
                //System.out.println("Got after " + (i + 1) + " response: " + response);
                String responseBody = response.body().string();
                Assert.assertEquals("Response msg: " + responseBody, 200, response.code());
                System.out.println("Got after " + (i + 1) + " retries: " + url);
                return responseBody;
            } catch (Throwable t) {
                /* some initial attempts may fail */
                e = t;
            }
            System.out.println("URL [" + url + "] not ready yet on " + (i + 1) + " of " + attemptCount
                    + " attempts, about to retry after " + attemptDelay + " ms");
            Thread.sleep(attemptDelay);
        }
        if (e != null) {
            throw e;
        } else {
            throw new AssertionError("Could not get [" + url + "]");
        }
    }

}
