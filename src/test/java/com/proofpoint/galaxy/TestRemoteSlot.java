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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Map;

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

    private Assignment appleAssignment;
    private File tempDir;
    private TestingBinaryRepository binaryRepository;
    private TestingConfigRepository configRepository;
    private RemoteSlot remoteSlot;
    private Slot slot;


    @BeforeClass
    public void startServer()
            throws Exception
    {
        tempDir = DeploymentUtils.createTempDir("agent");
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("agent.console-uri", "http://localhost:9999/")
                .put("agent.slots-dir", tempDir.getAbsolutePath())
                .build();

        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
                new AgentMainModule(),
                new ConfigurationModule(new ConfigurationFactory(properties)));

        server = injector.getInstance(TestingHttpServer.class);
        agent = injector.getInstance(Agent.class);

        server.start();
        client = new AsyncHttpClient();

        binaryRepository = new TestingBinaryRepository();
        configRepository = new TestingConfigRepository();
        appleAssignment = newAssignment("apple", "1.0");
    }

    private Assignment newAssignment(String name, String binaryVersion)
    {
        BinarySpec binarySpec = BinarySpec.valueOf("food.fruit:" + name + ":" + binaryVersion);
        ConfigSpec configSpec = ConfigSpec.valueOf("@prod:" + name + ":1.0");
        return new Assignment(binarySpec, binaryRepository, configSpec, configRepository);
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
        if (binaryRepository != null) {
            binaryRepository.destroy();
        }
        if (configRepository != null) {
            configRepository.destroy();
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
        assertNull(slot.status().getBinary());

        // test
        SlotStatus actual = remoteSlot.assign(appleAssignment);

        // verify
        SlotStatus expected = new SlotStatus(slot.getId(), slot.getName(), slot.getSelf(), appleAssignment.getBinary(), appleAssignment.getConfig(), STOPPED);
        assertEquals(actual, expected);
    }

    @Test
    public void testClear()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(appleAssignment).getBinary(), appleAssignment.getBinary());

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
        assertEquals(slot.assign(appleAssignment).getState(), STOPPED);

        // test
        SlotStatus actual = remoteSlot.start();

        // verify
        SlotStatus expected = new SlotStatus(slot.getId(), slot.getName(), slot.getSelf(), appleAssignment.getBinary(), appleAssignment.getConfig(), RUNNING);
        assertEquals(actual, expected);
    }

    @Test
    public void testStop()
            throws Exception
    {
        // setup
        slot.assign(appleAssignment);
        assertEquals(slot.start().getState(), RUNNING);

        // test
        SlotStatus actual = remoteSlot.stop();

        // verify
        SlotStatus expected = new SlotStatus(slot.getId(), slot.getName(), slot.getSelf(), appleAssignment.getBinary(), appleAssignment.getConfig(), STOPPED);
        assertEquals(actual, expected);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        // setup
        assertEquals(slot.assign(appleAssignment).getState(), STOPPED);

        // test
        SlotStatus actual = remoteSlot.restart();

        // verify
        SlotStatus expected = new SlotStatus(slot.getId(), slot.getName(), slot.getSelf(), appleAssignment.getBinary(), appleAssignment.getConfig(), RUNNING);
        assertEquals(actual, expected);
    }
}
