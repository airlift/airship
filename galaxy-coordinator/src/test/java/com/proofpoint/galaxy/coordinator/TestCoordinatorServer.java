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
package com.proofpoint.galaxy.coordinator;

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
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.event.client.NullEventModule;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.json.JsonModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.collect.Lists.transform;
import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.galaxy.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static com.proofpoint.galaxy.shared.Strings.shortestUniquePrefix;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotStatus.createSlotStatus;
import static com.proofpoint.galaxy.shared.SlotStatus.uuidGetter;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static com.proofpoint.galaxy.coordinator.TestingMavenRepository.MOCK_REPO;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestCoordinatorServer
{
    private AsyncHttpClient client;
    private TestingHttpServer server;

    private int prefixSize;
    private Coordinator coordinator;

    private final JsonCodec<List<SlotStatusRepresentation>> agentStatusRepresentationsCodec = listJsonCodec(SlotStatusRepresentation.class);
    private final JsonCodec<UpgradeVersions> upgradeVersionsCodec = jsonCodec(UpgradeVersions.class);
    private String agentId;
    private InMemoryStateManager stateManager;
    private UUID apple1SotId;
    private UUID apple2SlotId;
    private UUID bananaSlotId;

    private MockProvisioner provisioner;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("galaxy.version", "123")
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

                    @Provides @Singleton
                    public RemoteAgentFactory getRemoteAgentFactory(MockProvisioner provisioner) {
                        return provisioner.getAgentFactory();
                    }
                }),
                new ConfigurationModule(new ConfigurationFactory(properties)));

        server = injector.getInstance(TestingHttpServer.class);
        coordinator = injector.getInstance(Coordinator.class);
        stateManager = (InMemoryStateManager) injector.getInstance(StateManager.class);
        provisioner = (MockProvisioner) injector.getInstance(Provisioner.class);

        server.start();
        client = new AsyncHttpClient();
    }

    @BeforeMethod
    public void resetState()
    {
        provisioner.clearAgents();
        coordinator.updateAllAgents();
        assertTrue(coordinator.getAgents().isEmpty());


        apple1SotId = UUID.randomUUID();
        SlotStatus appleSlotStatus1 = createSlotStatus(apple1SotId,
                "apple1",
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
                "apple2",
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
                "banana",
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

        provisioner.addAgent(agentStatus);
        coordinator.updateAllAgents();

        prefixSize = shortestUniquePrefix(transform(transform(asList(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus), uuidGetter()), toStringFunction()), MIN_PREFIX_SIZE);
        stateManager.clearAll();
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
        Response response = client.prepareGet(urlFor("/v1/slot/?name=*"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);

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
        UpgradeVersions upgradeVersions = new UpgradeVersions("2.0", "2.0");
        String json = upgradeVersionsCodec.toJson(upgradeVersions);
        Response response = client.preparePost(urlFor("/v1/slot/assignment?host=apple*"))
                .setBody(json)
                .setHeader(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                SlotStatusRepresentation.from(apple1Status, prefixSize, MOCK_REPO),
                SlotStatusRepresentation.from(apple2Status, prefixSize, MOCK_REPO));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
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
        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);

        Response response = client.prepareDelete(urlFor("/v1/slot?host=apple*"))
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        apple1Status = apple1Status.changeState(TERMINATED);
        apple2Status = apple2Status.changeState(TERMINATED);
        SlotStatus bananaStatus = coordinator.getAgentStatus(agentId).getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                SlotStatusRepresentation.from(apple1Status, prefixSize, MOCK_REPO),
                SlotStatusRepresentation.from(apple2Status.changeState(TERMINATED), prefixSize, MOCK_REPO));
        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);

        assertEquals(apple1Status.getState(), TERMINATED);
        assertEquals(apple2Status.getState(), TERMINATED);
        assertEquals(bananaStatus.getState(), STOPPED);
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


        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                SlotStatusRepresentation.from(apple1Status, prefixSize, MOCK_REPO),
                SlotStatusRepresentation.from(apple2Status, prefixSize, MOCK_REPO));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(apple1Status.getState(), RUNNING);
        assertEquals(apple2Status.getState(), RUNNING);
        assertEquals(bananaStatus.getState(), STOPPED);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("restarting")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                SlotStatusRepresentation.from(apple1Status, prefixSize, MOCK_REPO),
                SlotStatusRepresentation.from(apple2Status, prefixSize, MOCK_REPO));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(apple1Status.getState(), RUNNING);
        assertEquals(apple2Status.getState(), RUNNING);
        assertEquals(bananaStatus.getState(), STOPPED);
    }

    @Test
    public void testStop()
            throws Exception
    {
        coordinator.setState(RUNNING, Predicates.<SlotStatus>alwaysTrue(), null);

        Response response = client.preparePut(urlFor("/v1/slot/lifecycle?binary=*:apple:*"))
                .setBody("stopped")
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        List<SlotStatusRepresentation> expected = ImmutableList.of(
                SlotStatusRepresentation.from(apple1Status, prefixSize, MOCK_REPO),
                SlotStatusRepresentation.from(apple2Status, prefixSize, MOCK_REPO));

        List<SlotStatusRepresentation> actual = agentStatusRepresentationsCodec.fromJson(response.getResponseBody());
        assertEqualsNoOrder(actual, expected);
        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), RUNNING);
    }

    @Test
    public void testLifecycleUnknown()
            throws Exception
    {
        Response response = client.preparePut(urlFor("/v1/slot/lifecycle"))
                .setBody("unknown")
                .execute()
                .get();

        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), STOPPED);
    }

    private String urlFor(String path)
    {
        return server.getBaseUrl().resolve(path).toString();
    }
}
