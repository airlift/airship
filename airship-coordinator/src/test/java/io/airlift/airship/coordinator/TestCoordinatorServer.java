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
package io.airlift.airship.coordinator;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.event.client.NullEventModule;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.SlotStatusRepresentation.SlotStatusRepresentationFactory;
import io.airlift.airship.shared.UpgradeVersions;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.HttpClient;
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

import javax.inject.Singleton;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.airship.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static io.airlift.airship.coordinator.TestingMavenRepository.MOCK_REPO;
import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.ExtraAssertions.assertEqualsNoOrder;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static io.airlift.airship.shared.SlotStatus.createSlotStatus;
import static io.airlift.airship.shared.Strings.shortestUniquePrefix;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestCoordinatorServer
{
    private HttpClient httpClient;
    private TestingHttpServer server;

    private Coordinator coordinator;
    private MockProvisioner provisioner;
    private InMemoryStateManager stateManager;
    private Repository repository;

    private final JsonCodec<List<CoordinatorStatusRepresentation>> coordinatorStatusesCodec = listJsonCodec(CoordinatorStatusRepresentation.class);
    private final JsonCodec<List<AgentStatusRepresentation>> agentStatusesCodec = listJsonCodec(AgentStatusRepresentation.class);
    private final JsonCodec<List<SlotStatusRepresentation>> slotStatusesCodec = listJsonCodec(SlotStatusRepresentation.class);
    private final JsonCodec<CoordinatorProvisioningRepresentation> coordinatorProvisioningCodec = jsonCodec(CoordinatorProvisioningRepresentation.class);
    private final JsonCodec<AgentProvisioningRepresentation> agentProvisioningCodec = jsonCodec(AgentProvisioningRepresentation.class);
    private final JsonCodec<UpgradeVersions> upgradeVersionsCodec = jsonCodec(UpgradeVersions.class);

    private String agentId;
    private UUID apple1SotId;
    private UUID apple2SlotId;
    private UUID bananaSlotId;
    private SlotStatusRepresentationFactory slotStatusRepresentationFactory;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("airship.version", "123")
                .put("node.id", "this-coordinator-instance-id")
                .put("node.location", "/test/location")
                .put("coordinator.binary-repo", "http://localhost:9999/")
                .put("coordinator.default-group-id", "prod")
                .put("coordinator.agent.default-config", "@agent.config")
                .put("coordinator.aws.access-key", "my-access-key")
                .put("coordinator.aws.secret-key", "my-secret-key")
                .put("coordinator.aws.agent.ami", "ami-0123abcd")
                .put("coordinator.aws.agent.keypair", "keypair")
                .put("coordinator.aws.agent.security-group", "default")
                .put("coordinator.aws.agent.default-instance-type", "t1.micro")
                .build();

        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new TestingNodeModule(),
                new JsonModule(),
                new JaxrsModule(),
                new NullEventModule(),
                Modules.override(new LocalProvisionerModule()).with(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(StateManager.class).to(InMemoryStateManager.class).in(SINGLETON);
                        binder.bind(MockProvisioner.class).in(SINGLETON);
                        binder.bind(Provisioner.class).to(Key.get(MockProvisioner.class)).in(SINGLETON);
                    }
                }),
                Modules.override(new CoordinatorMainModule()).with(new Module()
                {
                    public void configure(Binder binder)
                    {
                        binder.bind(Repository.class).toInstance(MOCK_REPO);
                        binder.bind(ServiceInventory.class).to(MockServiceInventory.class).in(Scopes.SINGLETON);
                    }

                    @Provides
                    @Singleton
                    public RemoteAgentFactory getRemoteAgentFactory(MockProvisioner provisioner)
                    {
                        return provisioner.getAgentFactory();
                    }
                }),
                new ConfigurationModule(new ConfigurationFactory(properties)));

        server = injector.getInstance(TestingHttpServer.class);
        coordinator = injector.getInstance(Coordinator.class);
        stateManager = (InMemoryStateManager) injector.getInstance(StateManager.class);
        provisioner = (MockProvisioner) injector.getInstance(Provisioner.class);
        repository = injector.getInstance(Repository.class);

        server.start();
        httpClient = new ApacheHttpClient();
    }

    @BeforeMethod
    public void resetState()
    {
        provisioner.clearCoordinators();
        coordinator.updateAllCoordinators();
        assertEquals(coordinator.getCoordinators().size(), 1);

        provisioner.clearAgents();
        coordinator.updateAllAgents();
        assertTrue(coordinator.getAgents().isEmpty());

        stateManager.clearAll();
        assertTrue(coordinator.getAllSlotStatus().isEmpty());
    }

    private void initializeOneAgent()
    {
        apple1SotId = UUID.randomUUID();
        SlotStatus appleSlotStatus1 = createSlotStatus(apple1SotId,
                URI.create("fake://appleServer1/v1/agent/slot/apple1"),
                URI.create("fake://appleServer1/v1/agent/slot/apple1"),
                "instance",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/apple1",
                ImmutableMap.<String, Integer>of());
        apple2SlotId = UUID.randomUUID();
        SlotStatus appleSlotStatus2 = createSlotStatus(apple2SlotId,
                URI.create("fake://appleServer2/v1/agent/slot/apple1"),
                URI.create("fake://appleServer2/v1/agent/slot/apple1"),
                "instance",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/apple2",
                ImmutableMap.<String, Integer>of());
        bananaSlotId = UUID.randomUUID();
        SlotStatus bananaSlotStatus = createSlotStatus(bananaSlotId,
                URI.create("fake://bananaServer/v1/agent/slot/banana"),
                URI.create("fake://bananaServer/v1/agent/slot/banana"),
                "instance",
                "/location",
                STOPPED,
                BANANA_ASSIGNMENT,
                "/banana",
                ImmutableMap.<String, Integer>of());

        agentId = UUID.randomUUID().toString();
        AgentStatus agentStatus = new AgentStatus(agentId,
                ONLINE,
                "instance-id",
                URI.create("fake://foo/"),
                URI.create("fake://foo/"),
                "/unknown/location",
                "instance.type",
                ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus),
                ImmutableMap.of("cpu", 8, "memory", 1024));

        provisioner.addAgents(agentStatus);
        coordinator.updateAllAgents();

        stateManager.clearAll();

        slotStatusRepresentationFactory = new SlotStatusRepresentationFactory(ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus), repository);
    }

    @AfterClass
    public void stopServer()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testGetAllCoordinatorsSingle()
            throws Exception
    {
        // add a coordinator directly to the provisioner
        String instanceId = "instance-id";
        URI internalUri = URI.create("fake://coordinator/" + instanceId + "/internal");
        URI externalUri = URI.create("fake://coordinator/" + instanceId + "/external");
        String location = "/unknown/location";
        String instanceType = "instance.type";

        Instance instance = new Instance(instanceId, instanceType, location, internalUri, externalUri);
        provisioner.addCoordinators(instance);
        coordinator.updateAllCoordinators();

        // verify coordinator appears
        Request request = RequestBuilder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/coordinator").build())
                .build();

        List<CoordinatorStatusRepresentation> coordinators = httpClient.execute(request, createJsonResponseHandler(coordinatorStatusesCodec, Status.OK.getStatusCode()));
        CoordinatorStatusRepresentation actual = getNonMainCoordinator(coordinators);

        assertEquals(actual.getCoordinatorId(), instanceId); // for now coordinator id is instance id
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getSelf(), internalUri);
        assertEquals(actual.getExternalUri(), externalUri);
    }

    @Test
    public void testCoordinatorProvision()
            throws Exception
    {
        // provision the coordinator and verify
        String instanceType = "instance-type";
        CoordinatorProvisioningRepresentation coordinatorProvisioningRepresentation = new CoordinatorProvisioningRepresentation("coordinator:config:1", 1, instanceType, null, null, null, null);
        Request request = RequestBuilder.preparePost()
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
        assertEquals(coordinators.get(0).getCoordinatorId(), instanceId);
        assertNull(coordinators.get(0).getSelf());
        assertNull(coordinators.get(0).getExternalUri());
        assertEquals(coordinators.get(0).getState(), CoordinatorLifecycleState.PROVISIONING);

        // start the coordinator and verify
        Instance expectedCoordinatorInstance = provisioner.startCoordinator(instanceId);
        coordinator.updateAllCoordinators();
        assertEquals(coordinator.getCoordinators().size(), 2);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getCoordinator(instanceId).getLocation(), location);
        assertEquals(coordinator.getCoordinator(instanceId).getCoordinatorId(), expectedCoordinatorInstance.getInstanceId());
        assertEquals(coordinator.getCoordinator(instanceId).getInternalUri(), expectedCoordinatorInstance.getInternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getExternalUri(), expectedCoordinatorInstance.getExternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getState(), CoordinatorLifecycleState.ONLINE);


        request = RequestBuilder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/coordinator").build())
                .build();

        coordinators = httpClient.execute(request, createJsonResponseHandler(coordinatorStatusesCodec, Status.OK.getStatusCode()));
        CoordinatorStatusRepresentation actual = getNonMainCoordinator(coordinators);

        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getCoordinatorId(), expectedCoordinatorInstance.getInstanceId());
        assertEquals(actual.getSelf(), expectedCoordinatorInstance.getInternalUri());
        assertEquals(actual.getExternalUri(), expectedCoordinatorInstance.getExternalUri());
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
        String agentId = UUID.randomUUID().toString();
        URI internalUri = URI.create("fake://agent/" + agentId + "/internal");
        URI externalUri = URI.create("fake://agent/" + agentId + "/external");
        String instanceId = "instance-id";
        String location = "/unknown/location";
        String instanceType = "instance.type";
        Map<String, Integer> resources = ImmutableMap.of("cpu", 8, "memory", 1024);

        AgentStatus status = new AgentStatus(agentId,
                AgentLifecycleState.ONLINE,
                instanceId,
                internalUri,
                externalUri,
                location,
                instanceType,
                ImmutableList.<SlotStatus>of(),
                resources);

        // add the agent
        provisioner.addAgents(status);
        coordinator.updateAllAgents();

        Request request = RequestBuilder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/admin/agent").build())
                .build();

        List<AgentStatusRepresentation> agents = httpClient.execute(request, createJsonResponseHandler(agentStatusesCodec, Status.OK.getStatusCode()));
        assertEquals(agents.size(), 1);

        AgentStatusRepresentation actual = agents.get(0);
        assertEquals(actual.getAgentId(), agentId);
        assertEquals(actual.getState(), AgentLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getSelf(), internalUri);
        assertEquals(actual.getExternalUri(), externalUri);
        assertEquals(actual.getResources(), resources);
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
        AgentStatus expectedAgentStatus = provisioner.startAgent(instanceId);
        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgents().size(), 1);
        assertEquals(coordinator.getAgent(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getAgent(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getAgent(instanceId).getLocation(), location);
        assertEquals(coordinator.getAgent(instanceId).getAgentId(), expectedAgentStatus.getAgentId());
        assertEquals(coordinator.getAgent(instanceId).getInternalUri(), expectedAgentStatus.getInternalUri());
        assertEquals(coordinator.getAgent(instanceId).getExternalUri(), expectedAgentStatus.getExternalUri());
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
        assertEquals(actual.getAgentId(), expectedAgentStatus.getAgentId());
        assertEquals(actual.getSelf(), expectedAgentStatus.getInternalUri());
        assertEquals(actual.getExternalUri(), expectedAgentStatus.getExternalUri());
        assertEquals(actual.getState(), AgentLifecycleState.ONLINE);
    }

    @Test
    public void testGetAllSlots()
            throws Exception
    {
        initializeOneAgent();

        Request request = RequestBuilder.prepareGet()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot").addParameter("name", "*").build())
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);

        int prefixSize = shortestUniquePrefix(asList(
                agentStatus.getSlotStatus(apple1SotId).getId().toString(),
                agentStatus.getSlotStatus(apple2SlotId).getId().toString(),
                agentStatus.getSlotStatus(bananaSlotId).getId().toString()),
                MIN_PREFIX_SIZE);

        assertEqualsNoOrder(actual, ImmutableList.of(
                SlotStatusRepresentation.from(agentStatus.getSlotStatus(apple1SotId), prefixSize, MOCK_REPO),
                SlotStatusRepresentation.from(agentStatus.getSlotStatus(apple2SlotId), prefixSize, MOCK_REPO),
                SlotStatusRepresentation.from(agentStatus.getSlotStatus(bananaSlotId), prefixSize, MOCK_REPO)));
    }

    @Test
    public void testUpgrade()
            throws Exception
    {
        initializeOneAgent();

        UpgradeVersions upgradeVersions = new UpgradeVersions("2.0", "2.0");
        Request request = RequestBuilder.preparePost()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/assignment").addParameter("host", "apple*").build())
                .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(upgradeVersionsCodec, upgradeVersions))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(apple1Status),
                slotStatusRepresentationFactory.create(apple2Status));

        assertEqualsNoOrder(actual, expected);

        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), STOPPED);

        assertEquals(apple1Status.getAssignment(), upgradeVersions.upgradeAssignment(MOCK_REPO, APPLE_ASSIGNMENT));
        assertEquals(apple2Status.getAssignment(), upgradeVersions.upgradeAssignment(MOCK_REPO, APPLE_ASSIGNMENT));
        assertEquals(bananaStatus.getAssignment(), BANANA_ASSIGNMENT);
    }

    @Test
    public void testTerminate()
            throws Exception
    {
        initializeOneAgent();

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);

        Request request = RequestBuilder.prepareDelete()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot").addParameter("host", "apple*").build())
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        apple1Status = apple1Status.changeState(TERMINATED);
        apple2Status = apple2Status.changeState(TERMINATED);
        SlotStatus bananaStatus = coordinator.getAgentByAgentId(agentId).getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(apple1Status),
                slotStatusRepresentationFactory.create(apple2Status.changeState(TERMINATED)));

        assertEqualsNoOrder(actual, expected);

        assertEquals(apple1Status.getState(), TERMINATED);
        assertEquals(apple2Status.getState(), TERMINATED);
        assertEquals(bananaStatus.getState(), STOPPED);
    }

    @Test
    public void testStart()
            throws Exception
    {
        initializeOneAgent();

        Request request = RequestBuilder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("running", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(apple1Status),
                slotStatusRepresentationFactory.create(apple2Status));

        assertEqualsNoOrder(actual, expected);
        assertEquals(apple1Status.getState(), RUNNING);
        assertEquals(apple2Status.getState(), RUNNING);
        assertEquals(bananaStatus.getState(), STOPPED);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        initializeOneAgent();

        Request request = RequestBuilder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("restarting", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(apple1Status),
                slotStatusRepresentationFactory.create(apple2Status));

        assertEqualsNoOrder(actual, expected);
        assertEquals(apple1Status.getState(), RUNNING);
        assertEquals(apple2Status.getState(), RUNNING);
        assertEquals(bananaStatus.getState(), STOPPED);
    }

    @Test
    public void testStop()
            throws Exception
    {
        initializeOneAgent();

        coordinator.setState(RUNNING, Predicates.<SlotStatus>alwaysTrue(), null);

        Request request = RequestBuilder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("stopped", UTF_8))
                .build();
        List<SlotStatusRepresentation> actual = httpClient.execute(request, createJsonResponseHandler(slotStatusesCodec, Status.OK.getStatusCode()));

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                slotStatusRepresentationFactory.create(apple1Status),
                slotStatusRepresentationFactory.create(apple2Status));

        assertEqualsNoOrder(actual, expected);
        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), RUNNING);
    }

    @Test
    public void testLifecycleUnknown()
            throws Exception
    {
        initializeOneAgent();

        Request request = RequestBuilder.preparePut()
                .setUri(coordinatorUriBuilder().appendPath("/v1/slot/lifecycle").addParameter("binary", "apple:*").build())
                .setBodyGenerator(createStaticBodyGenerator("unknown", UTF_8))
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), STOPPED);
    }

    private HttpUriBuilder coordinatorUriBuilder()
    {
        return uriBuilderFrom(server.getBaseUrl());
    }
}
