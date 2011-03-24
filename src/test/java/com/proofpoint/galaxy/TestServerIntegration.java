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
import java.net.URI;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestServerIntegration
{
    private AsyncHttpClient client;
    private TestingHttpServer agentServer;
    private TestingHttpServer consoleServer;

    private Agent agent;
    private AnnouncementService announcementService;
    private Console console;

    private Assignment appleAssignment;
    private File tempDir;
    private File testRepository;

    @BeforeClass
    public void startServer()
            throws Exception
    {
        tempDir = DeploymentUtils.createTempDir("agent");

        Map<String, String> consoleProperties = ImmutableMap.<String, String>builder()
                .put("console.binary-repo", "fake://binary,repo/")
                .put("console.config-repo", "fake://config.repo/")
                .put("console.status.expiration", "100d")
                .build();

        Injector consoleInjector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
                new ConsoleMainModule(),
                new ConfigurationModule(new ConfigurationFactory(consoleProperties)));

        consoleServer = consoleInjector.getInstance(TestingHttpServer.class);
        console = consoleInjector.getInstance(Console.class);

        consoleServer.start();
        client = new AsyncHttpClient();

        Map<String, String> agentProperties = ImmutableMap.<String, String>builder()
                .put("agent.console-uri", consoleServer.getBaseUrl().toString())
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

        testRepository = RepositoryTestHelper.createTestRepository();
        appleAssignment = newAssignment("apple", "1.0");
    }

    private Assignment newAssignment(String name, String binaryVersion)
    {
        BinarySpec binarySpec = BinarySpec.valueOf("food.fruit:" + name + ":" + binaryVersion);
        ConfigSpec configSpec = ConfigSpec.valueOf("@prod:" + name + ":1.0");
        return new Assignment(binarySpec, DeploymentUtils.toMavenRepositoryPath(testRepository.toURI(), binarySpec), configSpec, ImmutableMap.<String, URI>of());
    }

    @BeforeMethod
    public void resetState()
    {
        for (Slot slot : agent.getAllSlots()) {
            agent.deleteSlot(slot.getName());
        }
        for (AgentStatus agentStatus : console.getAllAgentStatus()) {
            console.removeAgentStatus(agentStatus.getAgentId());
        }
        assertTrue(agent.getAllSlots().isEmpty());
        assertTrue(console.getAllAgentStatus().isEmpty());
    }

    @AfterClass
    public void stopServer()
            throws Exception
    {
        if (agentServer != null) {
            agentServer.stop();
        }

        if (consoleServer != null) {
            consoleServer.stop();
        }

        if (client != null) {
            client.close();
        }
        if (tempDir != null) {
            DeploymentUtils.deleteRecursively(tempDir);
        }
        if (testRepository != null) {
            DeploymentUtils.deleteRecursively(testRepository);
        }
    }

    @Test
    public void testAnnounce()
            throws Exception
    {
        Slot slot = agent.addNewSlot();
        slot.assign(appleAssignment);
        announcementService.announce();

        AgentStatus agentStatus = console.getAgentStatus(agent.getAgentId());

        assertEquals(agentStatus, agent.getAgentStatus());
    }
}
