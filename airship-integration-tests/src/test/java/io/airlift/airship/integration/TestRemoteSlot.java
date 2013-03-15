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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import io.airlift.airship.agent.Agent;
import io.airlift.airship.agent.AgentMainModule;
import io.airlift.airship.agent.DeploymentManagerFactory;
import io.airlift.airship.agent.LifecycleManager;
import io.airlift.airship.agent.MockDeploymentManagerFactory;
import io.airlift.airship.agent.MockLifecycleManager;
import io.airlift.airship.agent.Slot;
import io.airlift.airship.coordinator.HttpRemoteAgent;
import io.airlift.airship.coordinator.HttpRemoteSlot;
import io.airlift.airship.coordinator.RemoteSlot;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.discovery.client.ServiceDescriptorsRepresentation;
import io.airlift.event.client.EventModule;
import io.airlift.http.client.AsyncHttpClient;
import io.airlift.http.client.netty.NettyAsyncHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.FileUtils.createTempDir;
import static io.airlift.airship.shared.FileUtils.deleteRecursively;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static io.airlift.airship.shared.SlotStatus.createSlotStatus;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestRemoteSlot
{
    private static final Installation APPLE_INSTALLATION = new Installation("apple",
            APPLE_ASSIGNMENT,
            URI.create("fake://localhost/apple.tar.gz"),
            URI.create("fake://localhost/apple.config"),
            ImmutableMap.of("memory", 512));

    private static final Installation BANANA_INSTALLATION = new Installation("banana",
            BANANA_ASSIGNMENT,
            URI.create("fake://localhost/banana.tar.gz"),
            URI.create("fake://localhost/banana.config"),
            ImmutableMap.of("cpu", 1));

    private AsyncHttpClient client;
    private TestingHttpServer server;

    private Agent agent;

    private File tempDir;
    private Slot slot;
    private HttpRemoteAgent remoteAgent;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        tempDir = createTempDir("agent");
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("agent.id", UUID.randomUUID().toString())
                .put("agent.coordinator-uri", "http://localhost:9999/")
                .put("agent.slots-dir", tempDir.getAbsolutePath())
                .build();

        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new TestingNodeModule(),
                new JsonModule(),
                new JaxrsModule(),
                new EventModule(),
                new ConfigurationModule(new ConfigurationFactory(properties)),
                Modules.override(new AgentMainModule()).with(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(DeploymentManagerFactory.class).to(MockDeploymentManagerFactory.class).in(Scopes.SINGLETON);
                        binder.bind(LifecycleManager.class).to(MockLifecycleManager.class).in(Scopes.SINGLETON);
                    }
                }));

        server = injector.getInstance(TestingHttpServer.class);
        agent = injector.getInstance(Agent.class);

        server.start();
        client = new NettyAsyncHttpClient();
        remoteAgent = new HttpRemoteAgent(
                agent.getAgentStatus(),
                "test",
                client,
                JsonCodec.jsonCodec(InstallationRepresentation.class),
                JsonCodec.jsonCodec(AgentStatusRepresentation.class),
                JsonCodec.jsonCodec(SlotStatusRepresentation.class),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class));
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

        slot = agent.getSlot(agent.install(APPLE_INSTALLATION).getId());
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
    }

    @Test
    public void testGetSlotStatus()
            throws Exception
    {
        RemoteSlot remoteSlot = new HttpRemoteSlot(slot.status(), client, remoteAgent);
        assertEquals(remoteSlot.status(), slot.status());
    }

    @Test
    public void testAssign()
            throws Exception
    {
        // setup
        SlotStatus status1 = slot.status();
        assertEquals(slot.status(), status1.changeAssignment(STOPPED, APPLE_ASSIGNMENT, status1.getResources()));

        // test
        remoteAgent.setStatus(agent.getAgentStatus());
        RemoteSlot remoteSlot = new HttpRemoteSlot(slot.status(), client, remoteAgent);
        SlotStatus actual = remoteSlot.assign(BANANA_INSTALLATION);

        // verify
        SlotStatus status = slot.status();
        SlotStatus expected = status.changeAssignment(STOPPED, BANANA_ASSIGNMENT, status.getResources());
        assertEquals(actual, expected);
    }

    @Test
    public void testTerminate()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(APPLE_INSTALLATION).getAssignment(), APPLE_ASSIGNMENT);

        // test
        remoteAgent.setStatus(agent.getAgentStatus());
        RemoteSlot remoteSlot = new HttpRemoteSlot(slot.status(), client, remoteAgent);
        SlotStatus actual = remoteSlot.terminate();

        // verify
        SlotStatus expected = createSlotStatus(slot.getId(),
                slot.getSelf(),
                slot.getExternalUri(),
                slot.status().getInstanceId(),
                slot.status().getLocation(),
                TERMINATED,
                null,
                null,
                ImmutableMap.<String, Integer>of());
        assertEquals(actual, expected);
    }

    @Test
    public void testStart()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(APPLE_INSTALLATION).getState(), STOPPED);

        // test
        remoteAgent.setStatus(agent.getAgentStatus());
        RemoteSlot remoteSlot = new HttpRemoteSlot(slot.status(), client, remoteAgent);
        SlotStatus actual = remoteSlot.start();

        // verify
        SlotStatus status = slot.status();
        SlotStatus expected = status.changeAssignment(RUNNING, APPLE_ASSIGNMENT, status.getResources());
        assertEquals(actual, expected);
    }

    @Test
    public void testStop()
            throws Exception
    {
        // setup
        slot.assign(APPLE_INSTALLATION);
        assertEquals(slot.start().getState(), RUNNING);

        // test
        remoteAgent.setStatus(agent.getAgentStatus());
        RemoteSlot remoteSlot = new HttpRemoteSlot(slot.status(), client, remoteAgent);
        SlotStatus actual = remoteSlot.stop();

        // verify
        SlotStatus status = slot.status();
        SlotStatus expected = status.changeAssignment(STOPPED, APPLE_ASSIGNMENT, status.getResources());
        assertEquals(actual, expected);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(APPLE_INSTALLATION).getState(), STOPPED);

        // test
        remoteAgent.setStatus(agent.getAgentStatus());
        RemoteSlot remoteSlot = new HttpRemoteSlot(slot.status(), client, remoteAgent);
        SlotStatus actual = remoteSlot.restart();

        // verify
        SlotStatus status = slot.status();
        SlotStatus expected = status.changeAssignment(RUNNING, APPLE_ASSIGNMENT, status.getResources());
        assertEquals(actual, expected);
    }
}
