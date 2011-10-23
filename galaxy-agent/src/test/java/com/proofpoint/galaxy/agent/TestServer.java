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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.galaxy.shared.InstallationHelper;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static javax.ws.rs.core.Response.Status;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestServer
{
    private static final int NOT_ALLOWED = 405;

    private AsyncHttpClient client;
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
                new AgentMainModule(),
                new ConfigurationModule(new ConfigurationFactory(properties)));

        server = injector.getInstance(TestingHttpServer.class);
        agent = injector.getInstance(Agent.class);

        server.start();
        client = new AsyncHttpClient();

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
            agent.terminateSlot(slot.getName());
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

        if (client != null) {
            client.close();
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

        Response response = client.prepareGet(urlFor("/v1/agent/slot/" + slotStatus.getName())).execute().get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<String, Object> expected = mapCodec.fromJson(Resources.toString(Resources.getResource("slot-status.json"), Charsets.UTF_8));
        expected.put("id", slotStatus.getId().toString());
        expected.put("shortId", slotStatus.getId().toString());
        expected.put("name", slotStatus.getName());
        expected.put("self", urlFor(slotStatus));
        expected.put("version", slotStatus.getVersion());
        expected.put("location", slotStatus.getLocation());
        expected.put("installPath", slotStatus.getInstallPath());
        // agent does not return expected status
        expected.remove("expectedBinary");
        expected.remove("expectedConfig");
        expected.remove("expectedStatus");

        Map<String, Object> actual = mapCodec.fromJson(response.getResponseBody());
        assertEquals(actual, expected);
    }

    @Test
    public void testGetAllSlotStatusEmpty()
            throws Exception
    {
        Response response = client.prepareGet(urlFor("/v1/agent/slot")).execute().get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);
        assertEquals(listCodec.fromJson(response.getResponseBody()), Collections.<Object>emptyList());
    }

    @Test
    public void testGetAllSlotStatus()
            throws Exception
    {
        SlotStatus appleSlotStatus = agent.install(appleInstallation);
        SlotStatus bananaSlotStatus = agent.install(bananaInstallation);

        Response response = client.prepareGet(urlFor("/v1/agent/slot")).execute().get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<Map<String, Object>> expected = listCodec.fromJson(Resources.toString(Resources.getResource("slot-status-list.json"), Charsets.UTF_8));
        expected.get(0).put("id", appleSlotStatus.getId().toString());
        expected.get(0).put("shortId", appleSlotStatus.getId().toString());
        expected.get(0).put("name", appleSlotStatus.getName());
        expected.get(0).put("version", appleSlotStatus.getVersion());
        expected.get(0).put("self", urlFor(appleSlotStatus));
        expected.get(0).put("location", appleSlotStatus.getLocation());
        expected.get(0).put("installPath", appleSlotStatus.getInstallPath());
        expected.get(0).put("resources", ImmutableMap.<String,Integer>of("memory", 512));
        expected.get(1).put("id", bananaSlotStatus.getId().toString());
        expected.get(1).put("shortId", bananaSlotStatus.getId().toString());
        expected.get(1).put("name", bananaSlotStatus.getName());
        expected.get(1).put("version", bananaSlotStatus.getVersion());
        expected.get(1).put("self", urlFor(bananaSlotStatus));
        expected.get(1).put("location", bananaSlotStatus.getLocation());
        expected.get(1).put("installPath", bananaSlotStatus.getInstallPath());
        expected.get(1).put("resources", ImmutableMap.<String,Integer>of("cpu", 1));

        List<Map<String, Object>> actual = listCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
    }

    @Test
    public void testInstallSlot()
            throws Exception
    {
        String json = installationCodec.toJson(InstallationRepresentation.from(appleInstallation));
        Response response = client.preparePost(urlFor("/v1/agent/slot"))
                .setBody(json)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
        Slot slot = agent.getAllSlots().iterator().next();
        assertEquals(response.getHeader(HttpHeaders.LOCATION), server.getBaseUrl().resolve("/v1/agent/slot/").resolve(slot.getName()).toString());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slot.getId().toString())
                .put("shortId", slot.getId().toString())
                .put("name", slot.getName())
                .put("binary", appleInstallation.getAssignment().getBinary().toString())
                .put("config", appleInstallation.getAssignment().getConfig().toString())
                .put("self", urlFor(slot))
                .put("location", slot.status().getLocation())
                .put("status", STOPPED.toString())
                .put("version", slot.status().getVersion())
                .put("installPath", slot.status().getInstallPath())
                .put("resources", ImmutableMap.<String,Integer>of("memory", 512))
                .build();

        Map<String, Object> actual = mapCodec.fromJson(response.getResponseBody());
        assertEquals(actual, expected);
    }


    @Test
    public void testTerminateSlot()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Response response = client.prepareDelete(urlFor("/v1/agent/slot/" + slotStatus.getName())).execute().get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        assertNull(agent.getSlot(slotStatus.getName()));

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("name", slotStatus.getName())
                .put("self", urlFor(slotStatus))
                .put("location", slotStatus.getLocation())
                .put("status", TERMINATED.toString())
                .put("version", SlotStatus.createVersion(slotStatus.getId(), TERMINATED, null))
                .put("resources", ImmutableMap.<String,Integer>of())
                .build();

        Map<String, Object> actual = mapCodec.fromJson(response.getResponseBody());
        assertEquals(actual, expected);
    }

    @Test
    public void testTerminateUnknownSlot()
            throws Exception
    {
        Response response = client.prepareDelete(urlFor("/v1/agent/slot/unknown"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testPutNotAllowed()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("slot-status.json"), Charsets.UTF_8);
        Response response = client.preparePut(urlFor("/v1/agent/slot"))
                .setBody(json)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), NOT_ALLOWED);

        assertNull(agent.getSlot("slot1"));
    }

    @Test
    public void testAssign()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        String json = installationCodec.toJson(InstallationRepresentation.from(appleInstallation));
        Response response = client.preparePut(urlFor(slotStatus) + "/assignment")
                .setBody(json)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("name", slotStatus.getName())
                .put("binary", appleInstallation.getAssignment().getBinary().toString())
                .put("config", appleInstallation.getAssignment().getConfig().toString())
                .put("self", urlFor(slotStatus))
                .put("location", slotStatus.getLocation())
                .put("status", STOPPED.toString())
                .put("version", slotStatus.getVersion())
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String,Integer>of("memory", 512))
                .build();

        Map<String, Object> actual = mapCodec.fromJson(response.getResponseBody());
        assertEquals(actual, expected);
    }

    @Test
    public void testStart()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Response response = client.preparePut(urlFor(slotStatus) + "/lifecycle")
                .setBody("running")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("name", slotStatus.getName())
                .put("binary", appleInstallation.getAssignment().getBinary().toString())
                .put("config", appleInstallation.getAssignment().getConfig().toString())
                .put("self", urlFor(slotStatus))
                .put("location", slotStatus.getLocation())
                .put("status", RUNNING.toString())
                .put("version", SlotStatus.createVersion(slotStatus.getId(), RUNNING, appleInstallation.getAssignment()))
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String,Integer>of("memory", 512))
                .build();

        Map<String, Object> actual = mapCodec.fromJson(response.getResponseBody());
        assertEquals(actual, expected);
    }

    @Test
    public void testStop()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);
        agent.getSlot(slotStatus.getName()).start();

        Response response = client.preparePut(urlFor(slotStatus) + "/lifecycle")
                .setBody("stopped")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("name", slotStatus.getName())
                .put("binary", appleInstallation.getAssignment().getBinary().toString())
                .put("config", appleInstallation.getAssignment().getConfig().toString())
                .put("self", urlFor(slotStatus))
                .put("location", slotStatus.getLocation())
                .put("status", STOPPED.toString())
                .put("version", slotStatus.getVersion())
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String,Integer>of("memory", 512))
                .build();

        Map<String, Object> actual = mapCodec.fromJson(response.getResponseBody());
        assertEquals(actual, expected);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Response response = client.preparePut(urlFor(slotStatus) + "/lifecycle")
                .setBody("restarting")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<String, Object> expected = ImmutableMap.<String, Object>builder()
                .put("id", slotStatus.getId().toString())
                .put("shortId", slotStatus.getId().toString())
                .put("name", slotStatus.getName())
                .put("binary", appleInstallation.getAssignment().getBinary().toString())
                .put("config", appleInstallation.getAssignment().getConfig().toString())
                .put("self", urlFor(slotStatus))
                .put("location", slotStatus.getLocation())
                .put("status", RUNNING.toString())
                .put("version", SlotStatus.createVersion(slotStatus.getId(), RUNNING, appleInstallation.getAssignment()))
                .put("installPath", slotStatus.getInstallPath())
                .put("resources", ImmutableMap.<String,Integer>of("memory", 512))
                .build();

        Map<String, Object> actual = mapCodec.fromJson(response.getResponseBody());
        assertEquals(actual, expected);
    }

    @Test
    public void testLifecycleUnknown()
            throws Exception
    {
        SlotStatus slotStatus = agent.install(appleInstallation);

        Response response = client.preparePut(urlFor(slotStatus) + "/lifecycle")
                .setBody("unknown")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
    }

    private String urlFor(String path)
    {
        return server.getBaseUrl().resolve(path).toString();
    }

    private String urlFor(Slot slot)
    {
        return urlFor(slot.status());
    }

    private String urlFor(SlotStatus slotStatus)
    {
        return server.getBaseUrl().resolve("/v1/agent/slot/").resolve(slotStatus.getName()).toString();
    }
}
