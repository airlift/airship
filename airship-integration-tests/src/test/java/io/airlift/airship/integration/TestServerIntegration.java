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
package io.airlift.airship.integration;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import io.airlift.airship.agent.Agent;
import io.airlift.airship.agent.Slot;
import io.airlift.airship.coordinator.AgentProvisioningRepresentation;
import io.airlift.airship.coordinator.Coordinator;
import io.airlift.airship.coordinator.CoordinatorMainModule;
import io.airlift.airship.coordinator.CoordinatorProvisioningRepresentation;
import io.airlift.airship.coordinator.InMemoryStateManager;
import io.airlift.airship.coordinator.Instance;
import io.airlift.airship.coordinator.Provisioner;
import io.airlift.airship.coordinator.StateManager;
import io.airlift.airship.coordinator.StaticProvisionerModule;
import io.airlift.airship.coordinator.TestingMavenRepository;
import io.airlift.airship.integration.MockLocalProvisioner.AgentServer;
import io.airlift.airship.integration.MockLocalProvisioner.CoordinatorServer;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.SlotStatusRepresentation.SlotStatusRepresentationFactory;
import io.airlift.airship.shared.UpgradeVersions;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.event.client.EventModule;
import io.airlift.http.client.ApacheHttpClient;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
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
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.ExtraAssertions.assertEqualsNoOrder;
import static io.airlift.airship.shared.FileUtils.createTempDir;
import static io.airlift.airship.shared.FileUtils.deleteRecursively;
import static io.airlift.airship.shared.FileUtils.newFile;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static io.airlift.testing.Assertions.assertNotEquals;
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

    private final JsonCodec<List<CoordinatorStatusRepresentation>> coordinatorStatusesCodec = listJsonCodec(CoordinatorStatusRepresentation.class);
    private final JsonCodec<List<AgentStatusRepresentation>> agentStatusesCodec = listJsonCodec(AgentStatusRepresentation.class);
    private final JsonCodec<List<SlotStatusRepresentation>> slotStatusesCodec = listJsonCodec(SlotStatusRepresentation.class);
    private final JsonCodec<CoordinatorProvisioningRepresentation> coordinatorProvisioningCodec = jsonCodec(CoordinatorProvisioningRepresentation.class);
    private final JsonCodec<AgentProvisioningRepresentation> agentProvisioningCodec = jsonCodec(AgentProvisioningRepresentation.class);
    private final JsonCodec<UpgradeVersions> upgradeVersionsCodec = jsonCodec(UpgradeVersions.class);

    private File binaryRepoDir;
    private File localBinaryRepoDir;
    private File expectedStateDir;
    private File serviceInventoryCacheDir;
    private Repository repository;

    private String agentInstanceId;
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
                .put("airship.version", "123")
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
                new EventModule(),
                new CoordinatorMainModule(),
                Modules.override(new StaticProvisionerModule()).with(new Module()
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
        provisioner.clearCoordinators();
        coordinator.updateAllCoordinatorsAndWait();
        assertEquals(coordinator.getCoordinators().size(), 1);

        provisioner.clearAgents();
        coordinator.updateAllAgentsAndWait();
        assertTrue(coordinator.getAgents().isEmpty());

        stateManager.clearAll();
        assertTrue(coordinator.getAllSlotStatus().isEmpty());
    }

    @AfterClass
    public void stopServer()
            throws Exception
    {
        provisioner.clearAgents();
        provisioner.clearCoordinators();

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
        List<Instance> instances = provisioner.provisionAgents("agent:config:1", 1, "instance-type", null, null, null, null, null);
        assertEquals(instances.size(), 1);
        AgentServer agentServer = provisioner.getAgent(instances.get(0).getInstanceId());
        assertNotNull(agentServer);
        agentServer.start();
        agentInstanceId = agentServer.getInstanceId();

        Agent agent = agentServer.getAgent();
        appleSlot1 = agent.getSlot(agent.install(new Installation("apple",
                APPLE_ASSIGNMENT,
                repository.binaryToHttpUri(APPLE_ASSIGNMENT.getBinary()),
                repository.configToHttpUri(APPLE_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getId());
        appleSlot2 = agent.getSlot(agent.install(new Installation("apple",
                APPLE_ASSIGNMENT,
                repository.binaryToHttpUri(APPLE_ASSIGNMENT.getBinary()),
                repository.configToHttpUri(APPLE_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getId());
        bananaSlot = agent.getSlot(agent.install(new Installation("banana",
                BANANA_ASSIGNMENT,
                repository.binaryToHttpUri(BANANA_ASSIGNMENT.getBinary()),
                repository.configToHttpUri(BANANA_ASSIGNMENT.getConfig()),
                ImmutableMap.of("memory", 512))).getId());

        coordinator.updateAllAgentsAndWait();
        assertEquals(coordinator.getAgents().size(), 1);
        assertNotNull(coordinator.getAgent(agentServer.getInstanceId()));
        assertEquals(coordinator.getAgent(agentServer.getInstanceId()).getState(), AgentLifecycleState.ONLINE);
        assertNotNull(coordinator.getAgent(agentServer.getInstanceId()).getInternalUri());
        assertNotNull(coordinator.getAgent(agentServer.getInstanceId()).getExternalUri());

        slotStatusRepresentationFactory = new SlotStatusRepresentationFactory(ImmutableList.of(appleSlot1.status(), appleSlot2.status(), bananaSlot.status()), repository);
    }

    @Test
    public void testGetAllCoordinatorsSingle()
            throws Exception
    {
        // directly add a new coordinator and start it
        List<Instance> instances = provisioner.provisionCoordinators("coordinator:config:1", 1, "instance-type", null, null, null, null, null);
        assertEquals(instances.size(), 1);
        CoordinatorServer coordinatorServer = provisioner.getCoordinator(instances.get(0).getInstanceId());
        coordinatorServer.start();
        coordinator.updateAllCoordinatorsAndWait();

        // verify coordinator appears
        Request request = Request.Builder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/coordinator").build())
                .build();

        List<CoordinatorStatusRepresentation> coordinators = httpClient.execute(request, createJsonResponseHandler(coordinatorStatusesCodec, Status.OK.getStatusCode()));
        CoordinatorStatusRepresentation actual = getNonMainCoordinator(coordinators);

        assertEquals(actual.getCoordinatorId(), coordinatorServer.getCoordinator().status().getCoordinatorId());
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), coordinatorServer.getInstance().getInstanceId());
        assertEquals(actual.getLocation(), coordinatorServer.getInstance().getLocation());
        assertEquals(actual.getInstanceType(), coordinatorServer.getInstance().getInstanceType());
        assertEquals(actual.getSelf(), coordinatorServer.getInstance().getInternalUri());
        assertEquals(actual.getExternalUri(), coordinatorServer.getInstance().getExternalUri());
    }

    @Test
    public void testCoordinatorProvision()
            throws Exception
    {
        // provision the coordinator and verify
        String instanceType = "instance-type";
        CoordinatorProvisioningRepresentation coordinatorProvisioningRepresentation = new CoordinatorProvisioningRepresentation("coordinator:config:1", 1, instanceType, null, null, null, null, null);
        Request request = Request.Builder.preparePost()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/coordinator").build())
                .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(coordinatorProvisioningCodec, coordinatorProvisioningRepresentation))
                .build();
        List<CoordinatorStatusRepresentation> coordinators = httpClient.execute(request, createJsonResponseHandler(coordinatorStatusesCodec, Status.OK.getStatusCode()));

        assertEquals(coordinators.size(), 1);
        String instanceId = coordinators.get(0).getInstanceId();
        assertNotNull(instanceId);
        String location = coordinators.get(0).getLocation();
        assertNotNull(location);
        assertEquals(coordinators.get(0).getInstanceType(), instanceType);
        assertNull(coordinators.get(0).getCoordinatorId());
        assertNull(coordinators.get(0).getSelf());
        assertNull(coordinators.get(0).getExternalUri());
        assertEquals(coordinators.get(0).getState(), CoordinatorLifecycleState.PROVISIONING);

        // start the coordinator and verify
        CoordinatorServer coordinatorServer = provisioner.getCoordinator(instanceId);
        coordinatorServer.start();
        coordinator.updateAllCoordinatorsAndWait();
        assertEquals(coordinator.getCoordinators().size(), 2);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getCoordinator(instanceId).getLocation(), location);
        assertEquals(coordinator.getCoordinator(instanceId).getCoordinatorId(), coordinatorServer.getCoordinator().status().getCoordinatorId());
        assertEquals(coordinator.getCoordinator(instanceId).getInternalUri(), coordinatorServer.getInstance().getInternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getExternalUri(), coordinatorServer.getInstance().getExternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getState(), CoordinatorLifecycleState.ONLINE);


        request = Request.Builder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/coordinator").build())
                .build();

        coordinators = httpClient.execute(request, createJsonResponseHandler(coordinatorStatusesCodec, Status.OK.getStatusCode()));
        CoordinatorStatusRepresentation actual = getNonMainCoordinator(coordinators);

        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getCoordinatorId(), coordinatorServer.getCoordinator().status().getCoordinatorId());
        assertEquals(actual.getSelf(), coordinatorServer.getInstance().getInternalUri());
        assertEquals(actual.getExternalUri(), coordinatorServer.getInstance().getExternalUri());
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
    }

    private CoordinatorStatusRepresentation getNonMainCoordinator(List<CoordinatorStatusRepresentation> coordinators)
    {
        assertEquals(coordinators.size(), 2);
        CoordinatorStatusRepresentation actual;
        if (coordinators.get(0).getInstanceId().equals(coordinator.status().getInstanceId())) {
            actual = coordinators.get(1);
        }
        else {
            actual = coordinators.get(0);
            assertEquals(coordinators.get(1).getInstanceId(), coordinator.status().getInstanceId());
        }
        return actual;
    }

    @Test
    public void testGetAllAgentsEmpty()
    {
        Request request = Request.Builder.prepareGet()
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
        List<Instance> instances = provisioner.provisionAgents("agent:config:1", 1, "instance-type", null, null, null, null, null);
        assertEquals(instances.size(), 1);
        AgentServer agentServer = provisioner.getAgent(instances.get(0).getInstanceId());
        agentServer.start();
        coordinator.updateAllAgentsAndWait();

        // get list of all agents
        Request request = Request.Builder.prepareGet()
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
        AgentProvisioningRepresentation agentProvisioningRepresentation = new AgentProvisioningRepresentation("agent:config:1", 1, instanceType, null, null, null, null, null);
        Request request = Request.Builder.preparePost()
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
        coordinator.updateAllAgentsAndWait();
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

        request = Request.Builder.prepareGet()
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

        Request request = Request.Builder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "*:apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("running", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(agentInstanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(agentInstanceId)));

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
        Request request = Request.Builder.preparePost()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/assignment").addParameter("binary", "*:apple:*").build())
                .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(upgradeVersionsCodec, upgradeVersions))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(agentInstanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(agentInstanceId)));

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

        Request request = Request.Builder.prepareDelete()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot").addParameter("binary", "*:apple:*").build())
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(agentInstanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(agentInstanceId)));

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
        coordinator.updateAllAgentsAndWait();
        assertEquals(appleSlot1.status().getState(), RUNNING);

        File pidFile = newFile(appleSlot1.status().getInstallPath(), "..", "installation", "launcher.pid").getCanonicalFile();
        String pidBeforeRestart = Files.readFirstLine(pidFile, Charsets.UTF_8);

        Request request = Request.Builder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "*:apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("restarting", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(agentInstanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(agentInstanceId)));

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
        coordinator.updateAllAgentsAndWait();

        Request request = Request.Builder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "*:apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("stopped", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(agentInstanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(agentInstanceId)));

        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), RUNNING);
    }


    @Test
    public void testKill()
            throws Exception
    {
        initializeOneAgent();

        appleSlot1.start();
        appleSlot2.start();
        bananaSlot.start();
        coordinator.updateAllAgentsAndWait();

        Request request = Request.Builder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "*:apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("killing", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(appleSlot1.status().changeInstanceId(agentInstanceId)),
                slotStatusRepresentationFactory.create(appleSlot2.status().changeInstanceId(agentInstanceId)));

        assertEqualsNoOrder(actual, expected);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), RUNNING);
    }


    @Test
    public void testShow()
            throws Exception
    {
        initializeOneAgent();
        coordinator.updateAllAgentsAndWait();

        Request request = Request.Builder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot").addParameter("!binary", "*:apple:*").build())
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(bananaSlot.status().changeInstanceId(agentInstanceId)));
        assertEqualsNoOrder(actual, expected);
    }

    private HttpUriBuilder coordinatorUriBuilder()
    {
        return uriBuilderFrom(coordinatorServer.getBaseUrl());
    }
}
