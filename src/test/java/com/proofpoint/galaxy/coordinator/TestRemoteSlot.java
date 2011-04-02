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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.galaxy.DeploymentUtils;
import com.proofpoint.galaxy.Slot;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.AgentMainModule;
import com.proofpoint.galaxy.agent.DeploymentManagerFactory;
import com.proofpoint.galaxy.agent.LifecycleManager;
import com.proofpoint.galaxy.agent.MockDeploymentManagerFactory;
import com.proofpoint.galaxy.agent.MockLifecycleManager;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.experimental.jaxrs.JaxrsModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Map;

import static com.proofpoint.galaxy.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.InstallationHelper.APPLE_INSTALLATION;
import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestRemoteSlot
{
    private AsyncHttpClient client;
    private TestingHttpServer server;

    private Agent agent;

    private File tempDir;
    private RemoteSlot remoteSlot;
    private Slot slot;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        tempDir = DeploymentUtils.createTempDir("agent");
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("agent.coordinator-uri", "http://localhost:9999/")
                .put("agent.slots-dir", tempDir.getAbsolutePath())
                .build();

        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
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
        client = new AsyncHttpClient();
    }

    @BeforeMethod
    public void resetState()
    {
        for (Slot slot : agent.getAllSlots()) {
            agent.deleteSlot(slot.getName());
        }
        assertTrue(agent.getAllSlots().isEmpty());

        slot = agent.addNewSlot();
        remoteSlot = new HttpRemoteSlot(slot.status(), new AsyncHttpClient());
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
            DeploymentUtils.deleteRecursively(tempDir);
        }
        remoteSlot = null;
    }

    @Test
    public void testGetSlotStatus()
            throws Exception
    {
        assertEquals(remoteSlot.status(), slot.status());
    }

    @Test
    public void testAssign()
            throws Exception
    {
        // setup
        assertNull(slot.status().getAssignment());

        // test
        SlotStatus actual = remoteSlot.assign(APPLE_INSTALLATION);

        // verify
        SlotStatus expected = new SlotStatus(slot.status(), STOPPED, APPLE_ASSIGNMENT);
        assertEquals(actual, expected);
    }

    @Test
    public void testClear()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(APPLE_INSTALLATION).getAssignment(), APPLE_ASSIGNMENT);

        // test
        SlotStatus actual = remoteSlot.clear();

        // verify
        SlotStatus expected = new SlotStatus(slot.getId(), slot.getName(), slot.getSelf());
        assertEquals(actual, expected);
    }

    @Test
    public void testStart()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(APPLE_INSTALLATION).getState(), STOPPED);

        // test
        SlotStatus actual = remoteSlot.start();

        // verify
        SlotStatus expected = new SlotStatus(slot.status(), RUNNING, APPLE_ASSIGNMENT);
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
        SlotStatus actual = remoteSlot.stop();

        // verify
        SlotStatus expected = new SlotStatus(slot.status(), STOPPED, APPLE_ASSIGNMENT);
        assertEquals(actual, expected);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(APPLE_INSTALLATION).getState(), STOPPED);

        // test
        SlotStatus actual = remoteSlot.restart();

        // verify
        SlotStatus expected = new SlotStatus(slot.status(), RUNNING, APPLE_ASSIGNMENT);
        assertEquals(actual, expected);
    }
}
