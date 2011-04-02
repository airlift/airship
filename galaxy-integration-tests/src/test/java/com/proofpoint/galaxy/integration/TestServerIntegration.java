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
package com.proofpoint.galaxy.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.AgentMainModule;
import com.proofpoint.galaxy.agent.AnnouncementService;
import com.proofpoint.galaxy.agent.Slot;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.coordinator.BinaryRepository;
import com.proofpoint.galaxy.coordinator.ConfigRepository;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.CoordinatorMainModule;
import com.proofpoint.galaxy.coordinator.TestingBinaryRepository;
import com.proofpoint.galaxy.coordinator.TestingConfigRepository;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.experimental.jaxrs.JaxrsModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.util.List;
import java.util.Map;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.LifecycleState.UNASSIGNED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestServerIntegration
{
    private AsyncHttpClient client;
    private TestingHttpServer agentServer;
    private TestingHttpServer coordinatorServer;

    private Agent agent;
    private AnnouncementService announcementService;
    private Coordinator coordinator;

    private Slot appleSlot1;
    private Slot appleSlot2;
    private Slot bananaSlot;
    private File tempDir;

    private final JsonCodec<AssignmentRepresentation> assignmentCodec = new JsonCodecBuilder().build(AssignmentRepresentation.class);
    private final JsonCodec<List<SlotStatusRepresentation>> agentStatusRepresentationsCodec = new JsonCodecBuilder().build(new TypeLiteral<List<SlotStatusRepresentation>>(){});

    private File binaryRepoDir;
    private File configRepoDir;
    private BinaryRepository binaryRepository;
    private ConfigRepository configRepository;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        try {
            binaryRepoDir = TestingBinaryRepository.createBinaryRepoDir();
            configRepoDir = TestingConfigRepository.createConfigRepoDir();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        Map<String, String> coordinatorProperties = ImmutableMap.<String, String>builder()
                .put("coordinator.binary-repo", binaryRepoDir.toURI().toString())
                .put("coordinator.config-repo", configRepoDir.toURI().toString())
                .put("coordinator.status.expiration", "100d")
                .build();

        Injector coordinatorInjector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
                new CoordinatorMainModule(),
                new ConfigurationModule(new ConfigurationFactory(coordinatorProperties)));

        coordinatorServer = coordinatorInjector.getInstance(TestingHttpServer.class);
        coordinator = coordinatorInjector.getInstance(Coordinator.class);
        binaryRepository = coordinatorInjector.getInstance(BinaryRepository.class);
        configRepository = coordinatorInjector.getInstance(ConfigRepository.class);

        coordinatorServer.start();
        client = new AsyncHttpClient();

        tempDir = createTempDir("agent");
        Map<String, String> agentProperties = ImmutableMap.<String, String>builder()
                .put("agent.coordinator-uri", coordinatorServer.getBaseUrl().toString())
                .put("agent.slots-dir", tempDir.getAbsolutePath())
                .build();

        Injector agentInjector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
                new AgentMainModule(),
                new ConfigurationModule(new ConfigurationFactory(agentProperties)));

        agentServer = agentInjector.getInstance(TestingHttpServer.class);
        agent = agentInjector.getInstance(Agent.class);
        announcementService = agentInjector.getInstance(AnnouncementService.class);

        agentServer.start();
        client = new AsyncHttpClient();
    }

    @BeforeMethod
    public void resetState()
            throws Exception
    {
        for (Slot slot : agent.getAllSlots()) {
            agent.deleteSlot(slot.getName());
        }
        for (AgentStatus agentStatus : coordinator.getAllAgentStatus()) {
            coordinator.removeAgentStatus(agentStatus.getAgentId());
        }
        assertTrue(agent.getAllSlots().isEmpty());
        assertTrue(coordinator.getAllAgentStatus().isEmpty());


        appleSlot1 = agent.addNewSlot();
        appleSlot1.assign(new Installation(APPLE_ASSIGNMENT, binaryRepository.getBinaryUri(APPLE_ASSIGNMENT.getBinary()), configRepository.getConfigMap(APPLE_ASSIGNMENT.getConfig())));
        appleSlot2 = agent.addNewSlot();
        appleSlot2.assign(new Installation(APPLE_ASSIGNMENT, binaryRepository.getBinaryUri(APPLE_ASSIGNMENT.getBinary()), configRepository.getConfigMap(APPLE_ASSIGNMENT.getConfig())));
        bananaSlot = agent.addNewSlot();
        bananaSlot.assign(new Installation(BANANA_ASSIGNMENT, binaryRepository.getBinaryUri(BANANA_ASSIGNMENT.getBinary()), configRepository.getConfigMap(BANANA_ASSIGNMENT.getConfig())));
        announcementService.announce();
    }

    @AfterClass
    public void stopServer()
            throws Exception
    {
        if (agentServer != null) {
            agentServer.stop();
        }

        if (coordinatorServer != null) {
            coordinatorServer.stop();
        }

        if (client != null) {
            client.close();
        }
        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
        if (binaryRepoDir != null) {
            deleteRecursively(binaryRepoDir);
        }
        if (configRepoDir != null) {
            deleteRecursively(configRepoDir);
        }
    }

    @Test
    public void testAnnounce()
            throws Exception
    {
        AgentStatus agentStatus = coordinator.getAgentStatus(agent.getAgentId());
        assertEquals(agentStatus, agent.getAgentStatus());
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
    public void testAssign()
            throws Exception
    {
        appleSlot1.clear();
        appleSlot2.clear();
        announcementService.announce();

        String json = assignmentCodec.toJson(AssignmentRepresentation.from(APPLE_ASSIGNMENT));
        Response response = client.preparePut(urlFor("/v1/slot/assignment?state=unassigned"))
                .setBody(json)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status()), SlotStatusRepresentation.from(appleSlot2.status()));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot1.status().getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(bananaSlot.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getAssignment(), BANANA_ASSIGNMENT);
    }


    @Test
    public void testClear()
            throws Exception
    {
        Response response = client.prepareDelete(urlFor("/v1/slot/assignment?binary=*:apple:*"))
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

    private String urlFor(String path)
    {
        return coordinatorServer.getBaseUrl().resolve(path).toString();
    }
}
