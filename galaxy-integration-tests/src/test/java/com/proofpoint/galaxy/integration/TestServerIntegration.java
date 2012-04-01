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
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.event.client.NullEventModule;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.Slot;
import com.proofpoint.galaxy.coordinator.AgentProvisioningRepresentation;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.CoordinatorMainModule;
import com.proofpoint.galaxy.coordinator.InMemoryStateManager;
import com.proofpoint.galaxy.coordinator.Instance;
import com.proofpoint.galaxy.coordinator.LocalProvisionerModule;
import com.proofpoint.galaxy.coordinator.Provisioner;
import com.proofpoint.galaxy.coordinator.StateManager;
import com.proofpoint.galaxy.coordinator.TestingMavenRepository;
import com.proofpoint.galaxy.integration.MockLocalProvisioner.AgentServer;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.HttpUriBuilder;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation.SlotStatusRepresentationFactory;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.NodeModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.FileUtils.newFile;
import static com.proofpoint.galaxy.shared.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static com.proofpoint.testing.Assertions.assertNotEquals;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestServerIntegration
{
    private HttpClient httpClient;

    private TestingHttpServer coordinatorServer;
    private Coordinator coordinator;
    private InMemoryStateManager stateManager;
    private MockLocalProvisioner provisioner;

    private Slot appleSlot1;
    private Slot appleSlot2;
    private Slot bananaSlot;

    private final JsonCodec<List<AgentStatusRepresentation>> agentStatusesCodec = listJsonCodec(AgentStatusRepresentation.class);
    private final JsonCodec<AgentProvisioningRepresentation> agentProvisioningCodec = jsonCodec(AgentProvisioningRepresentation.class);
    private final JsonCodec<List<SlotStatusRepresentation>> slotStatusesCodec = listJsonCodec(SlotStatusRepresentation.class);
    private final JsonCodec<UpgradeVersions> upgradeVersionsCodec = jsonCodec(UpgradeVersions.class);

    private File binaryRepoDir;
    private File localBinaryRepoDir;
    private File expectedStateDir;
    private File serviceInventoryCacheDir;
    private Repository repository;

    // initial agent created during test setup
    private Agent agent;
    private String instanceId;
    private SlotStatusRepresentationFactory slotStatusRepresentationFactory;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        try {
            binaryRepoDir = TestingMavenRepository.createBinaryRepoDir();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        localBinaryRepoDir = createTempDir("localBinaryRepoDir");
        expectedStateDir = createTempDir("expected-state");
        serviceInventoryCacheDir = createTempDir("service-inventory-cache");

        Map<String, String> coordinatorProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "prod")
                .put("galaxy.version", "123")
                .put("coordinator.binary-repo", binaryRepoDir.toURI().toString())
                .put("coordinator.default-group-id", "prod")
                .put("coordinator.binary-repo.local", localBinaryRepoDir.toString())
                .put("coordinator.status.expiration", "100d")
                .put("coordinator.agent.default-config", "@agent.config")
                .put("coordinator.aws.access-key", "my-access-key")
                .put("coordinator.aws.secret-key", "my-secret-key")
                .put("coordinator.aws.agent.ami", "ami-0123abcd")
                .put("coordinator.aws.agent.keypair", "keypair")
                .put("coordinator.aws.agent.security-group", "default")
                .put("coordinator.aws.agent.default-instance-type", "t1.micro")
                .put("coordinator.expected-state.dir", expectedStateDir.getAbsolutePath())
                .put("coordinator.service-inventory.cache-dir", serviceInventoryCacheDir.getAbsolutePath())
                .build();

        Injector coordinatorInjector = Guice.createInjector(new TestingHttpServerModule(),
                new NodeModule(),
                new JsonModule(),
                new JaxrsModule(),
                new NullEventModule(),
                new CoordinatorMainModule(),
                Modules.override(new LocalProvisionerModule()).with(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(StateManager.class).to(InMemoryStateManager.class).in(SINGLETON);
                        binder.bind(Provisioner.class).to(MockLocalProvisioner.class).in(SINGLETON);
                    }
                }),
                new ConfigurationModule(new ConfigurationFactory(coordinatorProperties)));

        coordinatorServer = coordinatorInjector.getInstance(TestingHttpServer.class);
        coordinator = coordinatorInjector.getInstance(Coordinator.class);
        repository = coordinatorInjector.getInstance(Repository.class);
        stateManager = (InMemoryStateManager) coordinatorInjector.getInstance(StateManager.class);
        provisioner = (MockLocalProvisioner) coordinatorInjector.getInstance(Provisioner.class);

        coordinatorServer.start();

        httpClient = new ApacheHttpClient();
    }

    @BeforeMethod
    public void resetState()
            throws Exception
    {
        provisioner.clearAgents();
        coordinator.updateAllAgents();
        stateManager.clearAll();
        assertTrue(provisioner.getAllAgents().isEmpty());
        assertTrue(coordinator.getAgents().isEmpty());
        assertTrue(coordinator.getAllSlotStatus().isEmpty());
    }

    @AfterClass
    public void stopServer()
            throws Exception
    {
        provisioner.clearAgents();

        if (coordinatorServer != null) {
            coordinatorServer.stop();
        }

        if (binaryRepoDir != null) {
            deleteRecursively(binaryRepoDir);
        }
        if (expectedStateDir != null) {
            deleteRecursively(expectedStateDir);
        }
        if (serviceInventoryCacheDir != null) {
            deleteRecursively(serviceInventoryCacheDir);
        }
        if (localBinaryRepoDir != null) {
            deleteRecursively(localBinaryRepoDir);
        }
    }

    private void initializeOneAgent()
            throws Exception
    {
        List<Instance> instances = provisioner.provisionAgents("agent:config:1", 1, "instance-type", null, null, null, null);
        assertEquals(instances.size(), 1);
        AgentServer agentServer = provisioner.getAgent(instances.get(0).getInstanceId());
        assertNotNull(agentServer);
        agentServer.start();
        instanceId = agentServer.getInstanceId();
        agent = agentServer.getAgent();

        appleSlot1 = agent.getSlot(agent.install(new Installation("apple",
                APPLE_ASSIGNMENT,
                repository.binaryToHttpUri(APPLE_ASSIGNMENT.getBinary()),
                repository.configToHttpUri(APPLE_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getName());
        appleSlot2 = agent.getSlot(agent.install(new Installation("apple",
                APPLE_ASSIGNMENT,
                repository.binaryToHttpUri(APPLE_ASSIGNMENT.getBinary()),
                repository.configToHttpUri(APPLE_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getName());
        bananaSlot = agent.getSlot(agent.install(new Installation("banana",
                BANANA_ASSIGNMENT,
                repository.binaryToHttpUri(BANANA_ASSIGNMENT.getBinary()),
                repository.configToHttpUri(BANANA_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getName());

        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgents().size(), 1);
        assertNotNull(coordinator.getAgent(agentServer.getInstanceId()));
        assertEquals(coordinator.getAgent(agentServer.getInstanceId()).getState(), AgentLifecycleState.ONLINE);
        assertNotNull(coordinator.getAgent(agentServer.getInstanceId()).getInternalUri());
        assertNotNull(coordinator.getAgent(agentServer.getInstanceId()).getExternalUri());

        slotStatusRepresentationFactory = new SlotStatusRepresentationFactory(ImmutableList.of(appleSlot1.status(), appleSlot2.status(), bananaSlot.status()), repository);
    }

    @Test
    public void testGetAllAgentsEmpty()
    {
        Request request = RequestBuilder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/agent").build())
                .build();

        List<AgentStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(agentStatusesCodec, Status.OK.getStatusCode()));
        assertEquals(actual.size(), 0);
    }

    @Test
    public void testGetAllAgentsSingle()
            throws Exception
    {
        // directly add a new agent and start it
        List<Instance> instances = provisioner.provisionAgents("agent:config:1", 1, "instance-type", null, null, null, null);
        assertEquals(instances.size(), 1);
        AgentServer agentServer = provisioner.getAgent(instances.get(0).getInstanceId());
        agentServer.start();
        coordinator.updateAllAgents();

        // get list of all agents
        Request request = RequestBuilder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/agent").build())
                .build();

        // verify agents list contains only the agent provisioned above
        List<AgentStatusRepresentation> agents = httpClient.execute(request, createJsonResponseHandler(agentStatusesCodec, Status.OK.getStatusCode()));
        assertEquals(agents.size(), 1);
        AgentStatusRepresentation actual = agents.get(0);
        assertEquals(actual.getAgentId(), agentServer.getAgent().getAgentId());
        assertEquals(actual.getState(), AgentLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), agentServer.getInstance().getInstanceId());
        assertEquals(actual.getLocation(), agentServer.getInstance().getLocation());
        assertEquals(actual.getInstanceType(), agentServer.getInstance().getInstanceType());
        assertNotNull(actual.getSelf());
        assertEquals(actual.getSelf(), agentServer.getInstance().getInternalUri());
        assertNotNull(actual.getExternalUri());
        assertEquals(actual.getExternalUri(), agentServer.getInstance().getExternalUri());
        assertEquals(actual.getResources(), agentServer.getAgent().getResources());
    }

    @Test
    public void testAgentProvision()
            throws Exception
    {
        // provision the agent and verify
        String instanceType = "instance-type";
        AgentProvisioningRepresentation agentProvisioningRepresentation = new AgentProvisioningRepresentation("agent:config:1", 1, instanceType, null, null, null, null);
        Request request = RequestBuilder.preparePost()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/agent").build())
                .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(agentProvisioningCodec, agentProvisioningRepresentation))
                .build();
        List<AgentStatusRepresentation> agents = httpClient.execute(request, createJsonResponseHandler(agentStatusesCodec, Status.OK.getStatusCode()));

        assertEquals(agents.size(), 1);
        String instanceId = agents.get(0).getInstanceId();
        assertNotNull(instanceId);
        String location = agents.get(0).getLocation();
        assertNotNull(location);
        assertEquals(agents.get(0).getInstanceType(), instanceType);
        assertNull(agents.get(0).getAgentId());
        assertNull(agents.get(0).getSelf());
        assertNull(agents.get(0).getExternalUri());
        assertEquals(agents.get(0).getState(), AgentLifecycleState.PROVISIONING);

        // start the agent and verify
        AgentServer agentServer = provisioner.getAgent(agents.get(0).getInstanceId());
        agentServer.start();
        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgents().size(), 1);
        assertEquals(coordinator.getAgent(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getAgent(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getAgent(instanceId).getLocation(), location);
        assertEquals(coordinator.getAgent(instanceId).getAgentId(), agentServer.getAgent().getAgentId());
        assertNotNull(coordinator.getAgent(instanceId).getInternalUri());
        assertEquals(coordinator.getAgent(instanceId).getInternalUri(), agentServer.getInstance().getInternalUri());
        assertNotNull(coordinator.getAgent(instanceId).getExternalUri());
        assertEquals(coordinator.getAgent(instanceId).getExternalUri(), agentServer.getInstance().getExternalUri());
        assertEquals(coordinator.getAgent(instanceId).getState(), AgentLifecycleState.ONLINE);

        request = RequestBuilder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/agent").build())
                .build();

        agents = httpClient.execute(request, createJsonResponseHandler(agentStatusesCodec, Status.OK.getStatusCode()));
        assertEquals(agents.size(), 1);

        AgentStatusRepresentation actual = agents.get(0);
        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getAgentId(), agentServer.getAgent().getAgentId());
        assertNotNull(actual.getSelf());
        assertEquals(actual.getSelf(), agentServer.getInstance().getInternalUri());
        assertNotNull(actual.getExternalUri());
        assertEquals(actual.getExternalUri(), agentServer.getInstance().getExternalUri());
        assertEquals(actual.getState(), AgentLifecycleState.ONLINE);
    }

    @Test
    public void testStart()
            throws Exception
    {
        initializeOneAgent();

        Request request = RequestBuilder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "*:apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("running", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(instanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(instanceId)));

        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);
        assertEqualsNoOrder(actual, expected);
    }

    @Test
    public void testUpgrade()
            throws Exception
    {
        initializeOneAgent();

        UpgradeVersions upgradeVersions = new UpgradeVersions("2.0", "@2.0");
        Request request = RequestBuilder.preparePost()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/assignment").addParameter("binary", "*:apple:*").build())
                .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(upgradeVersionsCodec, upgradeVersions))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(instanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(instanceId)));

        assertEqualsNoOrder(actual, expected);

        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        assertEquals(appleSlot1.status().getAssignment(), upgradeVersions.upgradeAssignment(repository, APPLE_ASSIGNMENT));
        assertEquals(appleSlot2.status().getAssignment(), upgradeVersions.upgradeAssignment(repository, APPLE_ASSIGNMENT));
    }

    @Test
    public void testTerminate()
            throws Exception
    {
        initializeOneAgent();

        Request request = RequestBuilder.prepareDelete()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot").addParameter("binary", "*:apple:*").build())
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(instanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(instanceId)));

        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), TERMINATED);
        assertEquals(appleSlot2.status().getState(), TERMINATED);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        initializeOneAgent();

        appleSlot1.start();
        coordinator.updateAllAgents();
        assertEquals(appleSlot1.status().getState(), RUNNING);

        File pidFile = newFile(appleSlot1.status().getInstallPath(), "..", "deployment", "launcher.pid").getCanonicalFile();
        String pidBeforeRestart = Files.readFirstLine(pidFile, Charsets.UTF_8);

        Request request = RequestBuilder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "*:apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("restarting", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(instanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(instanceId)));

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
        initializeOneAgent();

        appleSlot1.start();
        appleSlot2.start();
        bananaSlot.start();
        coordinator.updateAllAgents();

        Request request = RequestBuilder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "*:apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("stopped", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(instanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(instanceId)));

        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), RUNNING);
    }

    private HttpUriBuilder coordinatorUriBuilder()
    {
        return uriBuilderFrom(coordinatorServer.getBaseUrl());
    }
}
