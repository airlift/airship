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
package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import org.eclipse.jetty.http.HttpHeaders;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.galaxy.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.LifecycleState.UNASSIGNED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestConsoleServer
{
    private AsyncHttpClient client;
    private TestingHttpServer server;

    private Console console;

    private final JsonCodec<AssignmentRepresentation> assignmentCodec = new JsonCodecBuilder().build(AssignmentRepresentation.class);
    private final JsonCodec<AgentStatusRepresentation> agentStatusRepresentationCodec = new JsonCodecBuilder().build(AgentStatusRepresentation.class);
    private final JsonCodec<List<SlotStatusRepresentation>> agentStatusRepresentationsCodec = new JsonCodecBuilder().build(new TypeLiteral<List<SlotStatusRepresentation>>() { });

    private AgentStatus agentStatus;
    private Slot appleSlot1;
    private Slot appleSlot2;
    private Slot bananaSlot;

    private Assignment appleAssignment;
    private Assignment bananaAssignment;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("console.binary-repo", "http://localhost:9999/")
                .put("console.config-repo", "http://localhost:8888/")
                .build();

        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
                Modules.override(new ConsoleMainModule()).with(new Module() {
                    public void configure(Binder binder)
                    {
                        binder.bind(RemoteSlotFactory.class).to(MockRemoteSlotFactory.class).in(Scopes.SINGLETON);
                        binder.bind(BinaryRepository.class).to(MockBinaryRepository.class) .in(Scopes.SINGLETON);
                        binder.bind(ConfigRepository.class).to(MockConfigRepository.class).in(Scopes.SINGLETON);
                    }
                }),
                new ConfigurationModule(new ConfigurationFactory(properties)));

        server = injector.getInstance(TestingHttpServer.class);
        console = injector.getInstance(Console.class);
        BinaryRepository binaryRepository = injector.getInstance(BinaryRepository.class);
        ConfigRepository configRepository = injector.getInstance(ConfigRepository.class);

        server.start();
        client = new AsyncHttpClient();

        appleAssignment = new Assignment("food.fruit:apple:1.0", binaryRepository, "@prod:apple:1.0", configRepository);
        bananaAssignment = new Assignment("food.fruit:banana:2.0-SNAPSHOT", binaryRepository, "@prod:banana:2.0-SNAPSHOT", configRepository);
    }

    @BeforeMethod
    public void resetState()
    {
        for (AgentStatus agentStatus : console.getAllAgentStatus()) {
            console.removeAgentStatus(agentStatus.getAgentId());
        }
        assertTrue(console.getAllAgentStatus().isEmpty());


        SlotStatus appleSlotStatus1 = new SlotStatus(UUID.randomUUID(),
                "apple1",
                URI.create("fake://appleServer1/v1/slot/apple1"),
                appleAssignment.getBinary(),
                appleAssignment.getConfig(),
                STOPPED);
        SlotStatus appleSlotStatus2 = new SlotStatus(UUID.randomUUID(),
                "apple2",
                URI.create("fake://appleServer2/v1/slot/apple1"),
                appleAssignment.getBinary(),
                appleAssignment.getConfig(),
                STOPPED);
        SlotStatus bananaSlotStatus = new SlotStatus(UUID.randomUUID(),
                "banana",
                URI.create("fake://bananaServer/v1/slot/banana"),
                bananaAssignment.getBinary(),
                bananaAssignment.getConfig(),
                STOPPED);

        agentStatus = new AgentStatus(UUID.randomUUID(), ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus));

        console.updateAgentStatus(agentStatus);

        appleSlot1 = console.getSlot(appleSlotStatus1.getId());
        appleSlot2 = console.getSlot(appleSlotStatus2.getId());
        bananaSlot = console.getSlot(bananaSlotStatus.getId());
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
    }

    @Test
    public void testGetAllSlots()
            throws Exception
    {
        Response response = client.prepareGet(urlFor("/v1/slot/"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, ImmutableList.of(
                SlotStatusRepresentation.from(appleSlot1.status()),
                SlotStatusRepresentation.from(appleSlot2.status()),
                SlotStatusRepresentation.from(bananaSlot.status())));
    }


    @Test
    public void testAssign()
            throws Exception
    {
        appleSlot1.clear();
        appleSlot2.clear();
        bananaSlot.clear();

        String json = assignmentCodec.toJson(AssignmentRepresentation.from(appleAssignment));
        Response response = client.preparePut(urlFor("/v1/slot/assignment?host=apple*"))
                .setBody(json)
                .setHeader(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status()), SlotStatusRepresentation.from(appleSlot2.status()));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), UNASSIGNED);
    }

    @Test
    public void testClear()
            throws Exception
    {
        Response response = client.prepareDelete(urlFor("/v1/slot/assignment?host=apple*"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status()), SlotStatusRepresentation.from(appleSlot2.status()));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), UNASSIGNED);
        assertEquals(appleSlot2.status().getState(), UNASSIGNED);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test
    public void testStart()
            throws Exception
    {
        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("start")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status()), SlotStatusRepresentation.from(appleSlot2.status()));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("restart")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status()), SlotStatusRepresentation.from(appleSlot2.status()));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test
    public void testStop()
            throws Exception
    {
        appleSlot1.start();
        appleSlot2.start();
        bananaSlot.start();

        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("stop")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status()), SlotStatusRepresentation.from(appleSlot2.status()));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), RUNNING);
    }

    @Test
    public void testLifecycleUnknown()
            throws Exception
    {
        Response response = client.preparePut(urlFor("/v1/slot/lifecycle"))
                .setBody("unknown")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test
    public void testInitialAgentStatus()
            throws Exception
    {
        String json = agentStatusRepresentationCodec.toJson(AgentStatusRepresentation.from(agentStatus, server.getBaseUrl()));
        Response response = client.preparePut(urlFor("/v1/announce/" + agentStatus.getAgentId()))
                .setBody(json)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());

        assertEquals(console.getAgentStatus(agentStatus.getAgentId()), agentStatus);
    }

    @Test
    public void testUpdateAgentStatus()
            throws Exception
    {
        AgentStatus newAgentStatus = new AgentStatus(agentStatus.getAgentId(), ImmutableList.of(new SlotStatus(UUID.randomUUID(), "foo", URI.create("fake://foo"))));

        String json = agentStatusRepresentationCodec.toJson(AgentStatusRepresentation.from(newAgentStatus, server.getBaseUrl()));
        Response response = client.preparePut(urlFor("/v1/announce/" + agentStatus.getAgentId()))
                .setBody(json)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());

        assertEquals(console.getAgentStatus(agentStatus.getAgentId()), newAgentStatus);
    }

    @Test
    public void testRemoveAgentStatus()
            throws Exception
    {
        Response response = client.prepareDelete(urlFor("/v1/announce/" + agentStatus.getAgentId())).execute().get();
        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());

        assertNull(console.getAgentStatus(agentStatus.getAgentId()));
    }

    @Test
    public void testRemoveAgentStatusMissing()
            throws Exception
    {
        Response response = client.prepareDelete(urlFor("/v1/announce/" + UUID.randomUUID())).execute().get();
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    private String urlFor(String path)
    {
        return server.getBaseUrl().resolve(path).toString();
    }
}
