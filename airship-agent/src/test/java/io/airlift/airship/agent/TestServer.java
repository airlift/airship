/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.galaxy.agent;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.event.client.NullEventModule;
import com.proofpoint.galaxy.shared.HttpUriBuilder;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationHelper;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.VersionsUtil;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.FullJsonResponseHandler.JsonResponse;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.JsonBodyGenerator;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static javax.ws.rs.core.Response.Status;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestServer
{
    private static final int NOT_ALLOWED = 405;

    private HttpClient client;
    private TestingHttpServer server;

    private Agent agent;

    private final JsonCodec<InstallationRepresentation> installationCodec = jsonCodec(InstallationRepresentation.class);
    private final JsonCodec<Map<String, Object>> mapCodec = mapJsonCodec(String.class, Object.class);
    private final JsonCodec<List<Map<String, Object>>> listCodec = listJsonCodec(mapCodec);

    private InstallationHelper installationHelper;
    private Installation appleInstallation;
    private Installation bananaInstallation;
    private File tempDir;


    @BeforeClass
    public void startServer()
            throws Exception
    {
        tempDir = createTempDir("agent");
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("agent.id", UUID.randomUUID().toString())
                .put("agent.coordinator-uri", "http://localhost:9999/")
                .put("agent.slots-dir", tempDir.getAbsolutePath())
                .put("discovery.uri", "fake://server")
                .build();

        Injector injector = Guice.createInjector(
                new TestingDiscoveryModule(),
                new TestingNodeModule(),
                new JsonModule(),
                new TestingHttpServerModule(),
                new JaxrsModule(),
                new NullEventModule(),
                new AgentMainModule(),
                new ConfigurationModule(new ConfigurationFactory(properties)));

        server = injector.getInstance(TestingHttpServer.class);
        agent = injector.getInstance(Agent.class);

        server.start();
        client = new ApacheHttpClient();

        installationHelper = new InstallationHelper();
        appleInstallation = installationHelper.getAppleInstallation();
        bananaInstallation = installationHelper.getBananaInstallation();
    }

    @BeforeMethod
    public void resetState()
    {
        for (Slot slot : agent.getAllSlots()) {
            if (slot.status().getAssignment() != null) {
                slot.stop();
            }
            agent.terminateSlot(slot.getId());
        }
        assertTrue(agent.getAllSlots().isEmpty());
    }

    @AfterClass
    public void stopServer()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }

        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
        if (installationHelper != null) {
            installationHelper.destroy();
        }
    }

    @Test
    public void testGetSlotStatus()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Request request = RequestBuilder.prepareGet().setUri(urlFor("/v1/agent/slot", slotStatus.getId().toString())).build();
        Map<String, Object> response = client.execute(request, createJsonResponseHandler(mapCodec, Status.OK.getStatusCode()));

        Map<String, Object> expected = mapCodec.fromJson(Resources.toString(Resources.getResource("slot-status.json"), UTF_8));
        expected.put("id", slotStatus.getId().toString());
        expected.put("shortId", slotStatus.getId().toString());
        expected.put("self", urlFor(slotStatus).toASCIIString());
        expected.put("externalUri", urlFor(slotStatus).toASCIIString());
        expected.put("version", slotStatus.getVersion());
        expected.put("location", slotStatus.getLocation());
        expected.put("shortLocation", slotStatus.getLocation());
        expected.put("installPath", slotStatus.getInstallPath());
        // agent does not return instance id or expected status
        expected.remove("instanceId");
        expected.remove("expectedBinary");
        expected.remove("expectedConfig");
        expected.remove("expectedStatus");

        assertEquals(response, expected);
    }

    @Test
    public void testGetAllSlotStatusEmpty()
            throws Exception
    {
        Request request = RequestBuilder.prepareGet().setUri(urlFor("/v1/agent/slot")).build();
        List<Map<String, Object>> response = client.execute(request, createJsonResponseHandler(listCodec, Status.OK.getStatusCode()));
        assertEquals(response, Collections.<Object>emptyList());
    }

    @Test
    public void testGetAllSlotStatus()
            throws Exception
    {
        SlotStatus appleSlotStatus = agent.install(appleInstallation);
        SlotStatus bananaSlotStatus = agent.install(bananaInstallation);

        Request request = RequestBuilder.prepareGet().setUri(urlFor("/v1/agent/slot")).build();
        List<Map<String, Object>> response = client.execute(request, createJsonResponseHandler(listCodec, Status.OK.getStatusCode()));

        List<Map<String, Object>> expected = listCodec.fromJson(Resources.toString(Resources.getResource("slot-status-list.json"), UTF_8));
        expected.get(0).put("id", appleSlotStatus.getId().toString());
        expected.get(0).put("shortId", appleSlotStatus.getId().toString());
        expected.get(0).put("version", appleSlotStatus.getVersion());
        expected.get(0).put("self", urlFor(appleSlotStatus).toASCIIString());
        expected.get(0).put("externalUri", urlFor(appleSlotStatus).toASCIIString());
        expected.get(0).put("location", appleSlotStatus.getLocation());
        expected.get(0).put("shortLocation", appleSlotStatus.getLocation());
        expected.get(0).put("installPath", appleSlotStatus.getInstallPath());
        expected.get(0).put("resources", ImmutableMap.<String, Integer>of("memory", 512));
        expected.get(1).put("id", bananaSlotStatus.getId().toString());
        expected.get(1).put("shortId", bananaSlotStatus.getId().toString());
        expected.get(1).put("version", bananaSlotStatus.getVersion());
        expected.get(1).put("self", urlFor(bananaSlotStatus).toASCIIString());
        expected.get(1).put("externalUri", urlFor(bananaSlotStatus).toASCIIString());
        expected.get(1).put("location", bananaSlotStatus.getLocation());
        expected.get(1).put("shortLocation", bananaSlotStatus.getLocation());
        expected.get(1).put("installPath", bananaSlotStatus.getInstallPath());
        expected.get(1).put("resources", ImmutableMap.<String, Integer>of("cpu", 1));

        assertEqualsNoOrder(response, expected);
    }

    @Test
    public void testInstallSlot()
            throws Exception
    {
        Request request = RequestBuilder.preparePost()
                .setUri(urlFor("/v1/agent/slot"))
                .setHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(installationCodec, InstallationRepresentation.from(appleInstallation)))
                .build();
        JsonResponse<Map<String, Object>> response = client.execute(request, createFullJsonResponseHandler(mapCodec));

        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
        Slot slot = agent.getAllSlots().iterator().next();
        assertEquals(response.getHeader(HttpHeaders.LOCATION), uriBuilderFrom(server.getBaseUrl()).appendPath("/v1/agent/slot/").appendPath(slot.getId().toString()).toString());
        assertEquals(response.getHeader(CONTENT_TYPE), MediaType.APPLICATION_JSON);

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slot.getId().toString())
                .put("shortId", slot.getId().toString())
                .put("binary", appleInstallation.getAssignment().getBinary())
                .put("shortBinary", appleInstallation.getAssignment().getBinary())
                .put("config", appleInstallation.getAssignment().getConfig())
                .put("shortConfig", appleInstallation.getAssignment().getConfig())
                .put("self", urlFor(slot).toASCIIString())
                .put("externalUri", urlFor(slot).toASCIIString())
                .put("location", slot.status().getLocation())
                .put("shortLocation", slot.status().getLocation())
                .put("status", STOPPED.toString())
                .put("version", slot.status().getVersion())
                .put("installPath", slot.status().getInstallPath())
                .put("resources", ImmutableMap.<String, Integer>of("memory", 512))
                .build();

        assertEquals(response.getValue(), expected);
    }


    @Test
    public void testTerminateSlot()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Request request = RequestBuilder.prepareDelete()
                .setUri(urlFor("/v1/agent/slot", slotStatus.getId().toString()))
                .build();
        Map<String, Object> response = client.execute(request, createJsonResponseHandler(mapCodec, Status.OK.getStatusCode()));

        assertNull(agent.getSlot(slotStatus.getId()));

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("self", urlFor(slotStatus).toASCIIString())
                .put("externalUri", urlFor(slotStatus).toASCIIString())
                .put("location", slotStatus.getLocation())
                .put("shortLocation", slotStatus.getLocation())
                .put("status", TERMINATED.toString())
                .put("version", VersionsUtil.createSlotVersion(slotStatus.getId(), TERMINATED, null))
                .put("resources", ImmutableMap.<String, Integer>of())
                .build();

        assertEquals(response, expected);
    }

    @Test
    public void testTerminateUnknownSlot()
            throws Exception
    {
        Request request = RequestBuilder.prepareDelete()
                .setUri(urlFor("/v1/agent/slot/unknown"))
                .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());

        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testPutNotAllowed()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("slot-status.json"), UTF_8);
        Request request = RequestBuilder.preparePut()
                .setUri(urlFor("/v1/agent/slot"))
                .setHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(json, UTF_8))
                .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NOT_ALLOWED);

        assertNull(agent.getSlot(UUID.randomUUID()));
    }

    @Test
    public void testAssign()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        String json = installationCodec.toJson(InstallationRepresentation.from(appleInstallation));
        Request request = RequestBuilder.preparePut()
                .setUri(urlFor(slotStatus, "assignment"))
                .setHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(json, UTF_8))
                .build();
        Map<String, Object> response = client.execute(request, createJsonResponseHandler(mapCodec, Status.OK.getStatusCode()));

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("binary", appleInstallation.getAssignment().getBinary())
                .put("shortBinary", appleInstallation.getAssignment().getBinary())
                .put("config", appleInstallation.getAssignment().getConfig())
                .put("shortConfig", appleInstallation.getAssignment().getConfig())
                .put("self", urlFor(slotStatus).toASCIIString())
                .put("externalUri", urlFor(slotStatus).toASCIIString())
                .put("location", slotStatus.getLocation())
                .put("shortLocation", slotStatus.getLocation())
                .put("status", STOPPED.toString())
                .put("version", slotStatus.getVersion())
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String, Integer>of("memory", 512))
                .build();

        assertEquals(response, expected);
    }

    @Test
    public void testStart()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Request request = RequestBuilder.preparePut()
                .setUri(urlFor(slotStatus, "lifecycle"))
                .setBodyGenerator(createStaticBodyGenerator("running", UTF_8))
                .build();
        Map<String, Object> response = client.execute(request, createJsonResponseHandler(mapCodec, Status.OK.getStatusCode()));

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("binary", appleInstallation.getAssignment().getBinary())
                .put("shortBinary", appleInstallation.getAssignment().getBinary())
                .put("config", appleInstallation.getAssignment().getConfig())
                .put("shortConfig", appleInstallation.getAssignment().getConfig())
                .put("self", urlFor(slotStatus).toASCIIString())
                .put("externalUri", urlFor(slotStatus).toASCIIString())
                .put("location", slotStatus.getLocation())
                .put("shortLocation", slotStatus.getLocation())
                .put("status", RUNNING.toString())
                .put("version", VersionsUtil.createSlotVersion(slotStatus.getId(), RUNNING, appleInstallation.getAssignment()))
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String, Integer>of("memory", 512))
                .build();

        assertEquals(response, expected);
    }

    @Test
    public void testStop()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);
        agent.getSlot(slotStatus.getId()).start();

        Request request = RequestBuilder.preparePut()
                .setUri(urlFor(slotStatus, "lifecycle"))
                .setBodyGenerator(createStaticBodyGenerator("stopped", UTF_8))
                .build();
        Map<String, Object> response = client.execute(request, createJsonResponseHandler(mapCodec, Status.OK.getStatusCode()));

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("binary", appleInstallation.getAssignment().getBinary())
                .put("shortBinary", appleInstallation.getAssignment().getBinary())
                .put("config", appleInstallation.getAssignment().getConfig())
                .put("shortConfig", appleInstallation.getAssignment().getConfig())
                .put("self", urlFor(slotStatus).toASCIIString())
                .put("externalUri", urlFor(slotStatus).toASCIIString())
                .put("location", slotStatus.getLocation())
                .put("shortLocation", slotStatus.getLocation())
                .put("status", STOPPED.toString())
                .put("version", slotStatus.getVersion())
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String, Integer>of("memory", 512))
                .build();

        assertEquals(response, expected);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Request request = RequestBuilder.preparePut()
                .setUri(urlFor(slotStatus, "lifecycle"))
                .setBodyGenerator(createStaticBodyGenerator("restarting", UTF_8))
                .build();
        Map<String, Object> response = client.execute(request, createJsonResponseHandler(mapCodec, Status.OK.getStatusCode()));

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("binary", appleInstallation.getAssignment().getBinary())
                .put("shortBinary", appleInstallation.getAssignment().getBinary())
                .put("config", appleInstallation.getAssignment().getConfig())
                .put("shortConfig", appleInstallation.getAssignment().getConfig())
                .put("self", urlFor(slotStatus).toASCIIString())
                .put("externalUri", urlFor(slotStatus).toASCIIString())
                .put("location", slotStatus.getLocation())
                .put("shortLocation", slotStatus.getLocation())
                .put("status", RUNNING.toString())
                .put("version", VersionsUtil.createSlotVersion(slotStatus.getId(), RUNNING, appleInstallation.getAssignment()))
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String, Integer>of("memory", 512))
                .build();

        assertEquals(response, expected);
    }

    @Test
    public void testLifecycleUnknown()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Request request = RequestBuilder.preparePut()
                .setUri(urlFor(slotStatus, "lifecycle"))
                .setBodyGenerator(createStaticBodyGenerator("unknown", UTF_8))
                .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
    }

    private URI urlFor(String... pathParts)
    {
        HttpUriBuilder builder = uriBuilderFrom(server.getBaseUrl());
        for (String pathPart : pathParts) {
            builder.appendPath(pathPart);
        }
        return builder.build();
    }

    private URI urlFor(Slot slot, String... pathParts)
    {
        return urlFor(slot.status(), pathParts);
    }

    private URI urlFor(SlotStatus slotStatus, String... pathParts)
    {
        HttpUriBuilder builder = uriBuilderFrom(server.getBaseUrl())
                .appendPath("/v1/agent/slot/")
                .appendPath(slotStatus.getId().toString());
        for (String pathPart : pathParts) {
            builder.appendPath(pathPart);
        }
        return builder.build();
    }
}
