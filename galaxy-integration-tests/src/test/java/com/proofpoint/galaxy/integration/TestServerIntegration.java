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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.AgentMainModule;
import com.proofpoint.galaxy.agent.Slot;
import com.proofpoint.galaxy.coordinator.TestingRepository;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.CoordinatorMainModule;
import com.proofpoint.galaxy.coordinator.CoordinatorSlotResource;
import com.proofpoint.galaxy.coordinator.InMemoryStateManager;
import com.proofpoint.galaxy.coordinator.Instance;
import com.proofpoint.galaxy.coordinator.LocalProvisioner;
import com.proofpoint.galaxy.coordinator.LocalProvisionerModule;
import com.proofpoint.galaxy.coordinator.Provisioner;
import com.proofpoint.galaxy.coordinator.StateManager;
import com.proofpoint.galaxy.coordinator.Strings;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.NodeModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;

import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.FileUtils.newFile;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static com.proofpoint.testing.Assertions.assertNotEquals;
import static java.lang.Math.max;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestServerIntegration
{
    private AsyncHttpClient client;
    private TestingHttpServer agentServer;
    private TestingHttpServer coordinatorServer;

    private Agent agent;
    private Coordinator coordinator;

    private Slot appleSlot1;
    private Slot appleSlot2;
    private Slot bananaSlot;
    private File tempDir;

    private final JsonCodec<List<SlotStatusRepresentation>> agentStatusRepresentationsCodec = listJsonCodec(SlotStatusRepresentation.class);
    private final JsonCodec<UpgradeVersions> upgradeVersionsCodec = jsonCodec(UpgradeVersions.class);

    private File binaryRepoDir;
    private File localBinaryRepoDir;
    private Repository repository;

    private int prefixSize;
    private Instance agentInstance;
    private LocalProvisioner provisioner;

    public static Map<String,Integer> AGENT_RESOURCES = ImmutableMap.<String,Integer>builder()
            .put("cpu", 8)
            .put("memory", 1024)
            .build();

    @BeforeClass
    public void startServer()
            throws Exception
    {
        try {
            binaryRepoDir = TestingRepository.createBinaryRepoDir();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        localBinaryRepoDir = createTempDir("localBinaryRepoDir");

        Map<String, String> coordinatorProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "prod")
                .put("galaxy.version", "123")
                .put("coordinator.binary-repo", binaryRepoDir.toURI().toString())
                .put("coordinator.default-group-id", "prod")
                .put("coordinator.binary-repo.local", localBinaryRepoDir.toString())
                .put("coordinator.status.expiration", "100d")
                .put("coordinator.aws.access-key", "my-access-key")
                .put("coordinator.aws.secret-key", "my-secret-key")
                .put("coordinator.aws.agent.ami", "ami-0123abcd")
                .put("coordinator.aws.agent.keypair", "keypair")
                .put("coordinator.aws.agent.security-group", "default")
                .put("coordinator.aws.agent.default-instance-type", "t1.micro")
                .put("coordinator.expected-state.dir", createTempDir("expected-state").getAbsolutePath())
                .build();

        Injector coordinatorInjector = Guice.createInjector(new TestingHttpServerModule(),
                new NodeModule(),
                new JsonModule(),
                new JaxrsModule(),
                new CoordinatorMainModule(),
                Modules.override(new LocalProvisionerModule()).with(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(StateManager.class).to(InMemoryStateManager.class).in(SINGLETON);
                        binder.bind(Provisioner.class).to(LocalProvisioner.class).in(SINGLETON);
                    }
                }),
                new ConfigurationModule(new ConfigurationFactory(coordinatorProperties)));

        coordinatorServer = coordinatorInjector.getInstance(TestingHttpServer.class);
        coordinator = coordinatorInjector.getInstance(Coordinator.class);
        repository = coordinatorInjector.getInstance(Repository.class);
        provisioner = (LocalProvisioner) coordinatorInjector.getInstance(Provisioner.class);

        coordinatorServer.start();
        client = new AsyncHttpClient();

        tempDir = createTempDir("agent");
        File resourcesFile = new File(tempDir, "slots/galaxy-resources.properties");
        writeResources(AGENT_RESOURCES, resourcesFile);

        Map<String, String> agentProperties = ImmutableMap.<String, String>builder()
                .put("agent.id", UUID.randomUUID().toString())
                .put("agent.coordinator-uri", coordinatorServer.getBaseUrl().toString())
                .put("agent.slots-dir", new File(tempDir, "slots").getAbsolutePath())
                .put("agent.resources-file", resourcesFile.getAbsolutePath())
                .put("discovery.uri", "fake://server")
                .build();

        Injector agentInjector = Guice.createInjector(new TestingHttpServerModule(),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new JsonModule(),
                new JaxrsModule(),
                new AgentMainModule(),
                new ConfigurationModule(new ConfigurationFactory(agentProperties)));

        agentServer = agentInjector.getInstance(TestingHttpServer.class);
        agent = agentInjector.getInstance(Agent.class);
        agentServer.start();
        client = new AsyncHttpClient();
    }

    public static void writeResources(Map<String, Integer> resources, File resourcesFile)
            throws IOException
    {
        Properties properties = new Properties();
        for (Entry<String, Integer> entry : resources.entrySet()) {
            properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        resourcesFile.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(resourcesFile);
        try {
            properties.store(out, "");
        }
        finally {
            out.close();
        }
    }

    @BeforeMethod
    public void resetState()
            throws Exception
    {
        for (Slot slot : agent.getAllSlots()) {
            if (slot.status().getAssignment() != null) {
                slot.stop();
            }
            agent.terminateSlot(slot.getName());
        }
        for (AgentStatus agentStatus : coordinator.getAllAgentStatus()) {
            coordinator.removeAgent(agentStatus.getAgentId());
        }
        assertTrue(agent.getAllSlots().isEmpty());
        assertTrue(coordinator.getAllAgentStatus().isEmpty());


        appleSlot1 = agent.getSlot(agent.install(new Installation(APPLE_ASSIGNMENT,
                repository.getUri(APPLE_ASSIGNMENT.getBinary()),
                repository.getUri(APPLE_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getName());
        appleSlot2 = agent.getSlot(agent.install(new Installation(APPLE_ASSIGNMENT,
                repository.getUri(APPLE_ASSIGNMENT.getBinary()),
                repository.getUri(APPLE_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getName());
        bananaSlot = agent.getSlot(agent.install(new Installation(BANANA_ASSIGNMENT,
                repository.getUri(BANANA_ASSIGNMENT.getBinary()),
                repository.getUri(BANANA_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getName());

        agentInstance = new Instance(agent.getAgentId(), "test.type", "location", agentServer.getBaseUrl());
        provisioner.addAgent(agentInstance);
        coordinator.updateAllAgents();

        prefixSize = max(CoordinatorSlotResource.MIN_PREFIX_SIZE, Strings.shortestUniquePrefix(asList(
                appleSlot1.getId().toString(),
                appleSlot2.getId().toString(),
                bananaSlot.getId().toString())));
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
        if (localBinaryRepoDir != null) {
            deleteRecursively(localBinaryRepoDir);
        }
    }

    @Test
    public void testStart()
            throws Exception
    {
        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("running")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status(), prefixSize),
                SlotStatusRepresentation.from(appleSlot2.status(), prefixSize));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);
        assertEqualsNoOrder(actual, expected);
    }

    @Test
    public void testUpgrade()
            throws Exception
    {
        UpgradeVersions upgradeVersions = new UpgradeVersions("2.0", "2.0");
        String json = upgradeVersionsCodec.toJson(upgradeVersions);
        Response response = client.preparePost(urlFor("/v1/slot/assignment?binary=*:apple:*"))
                .setBody(json)
                .setHeader(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status(), prefixSize),
                SlotStatusRepresentation.from(appleSlot2.status(), prefixSize));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);

        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        assertEquals(appleSlot1.status().getAssignment(), upgradeVersions.upgradeAssignment(APPLE_ASSIGNMENT));
        assertEquals(appleSlot2.status().getAssignment(), upgradeVersions.upgradeAssignment(APPLE_ASSIGNMENT));
    }

    @Test
    public void testTerminate()
            throws Exception
    {
        Response response = client.prepareDelete(urlFor("/v1/slot?binary=*:apple:*"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status(), prefixSize),
                SlotStatusRepresentation.from(appleSlot2.status(), prefixSize));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), TERMINATED);
        assertEquals(appleSlot2.status().getState(), TERMINATED);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        appleSlot1.start();
        coordinator.updateAllAgents();
        assertEquals(appleSlot1.status().getState(), RUNNING);

        File pidFile = newFile(appleSlot1.status().getInstallPath(), "..", "deployment", "launcher.pid").getCanonicalFile();
        String pidBeforeRestart = Files.readFirstLine(pidFile, Charsets.UTF_8);

        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("restarting")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status(), prefixSize),
                SlotStatusRepresentation.from(appleSlot2.status(), prefixSize));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        String pidAfterRestart = Files.readFirstLine(pidFile, Charsets.UTF_8);
        assertNotEquals(pidAfterRestart, pidBeforeRestart);
    }

    @Test
    public void testStop()
            throws Exception
    {
        appleSlot1.start();
        appleSlot2.start();
        bananaSlot.start();
        coordinator.updateAllAgents();

        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("stopped")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);


        List<SlotStatusRepresentation> expected = ImmutableList.of(SlotStatusRepresentation.from(appleSlot1.status(), prefixSize),
                SlotStatusRepresentation.from(appleSlot2.status(), prefixSize));

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
