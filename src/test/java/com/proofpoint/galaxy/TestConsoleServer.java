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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import org.eclipse.jetty.http.HttpHeaders;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestConsoleServer
{
    private AsyncHttpClient client;
    private TestingHttpServer server;

    private Console console;

    private final JsonCodec<AgentStatusRepresentation> agentStatusRepresentationCodec = new JsonCodecBuilder().build(AgentStatusRepresentation.class);

    private AgentStatus agentStatus;
    private File tempDir;
    private File testRepository;


    @BeforeClass
    public void startServer()
            throws Exception
    {
        tempDir = DeploymentUtils.createTempDir("agent");
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("console.binary-repo", "http://localhost:9999/")
                .put("console.config-repo", "http://localhost:8888/")
                .build();

        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
                new ConsoleMainModule(),
                new ConfigurationModule(new ConfigurationFactory(properties)));

        server = injector.getInstance(TestingHttpServer.class);
        console = injector.getInstance(Console.class);

        server.start();
        client = new AsyncHttpClient();

        testRepository = RepositoryTestHelper.createTestRepository();
        agentStatus = new AgentStatus(UUID.randomUUID(),
                ImmutableList.of(
                        new SlotStatus(UUID.randomUUID(),
                                "slot1",
                                URI.create("fake://foo"),
                                BinarySpec.valueOf("food.fruit:apple:1.0"),
                                ConfigSpec.valueOf("@prod:apple:1.0"),
                                STOPPED),
                        new SlotStatus(UUID.randomUUID(),
                                "slot2",
                                URI.create("fake://foo"),
                                BinarySpec.valueOf("food.fruit:banana:2.0-SNAPSHOT"),
                                ConfigSpec.valueOf("@prod:banana:1.0"),
                                STOPPED)));

    }

    @BeforeMethod
    public void resetState()
    {
        for (AgentStatus agentStatus : console.getAllAgentStatus()) {
            console.removeAgentStatus(agentStatus.getAgentId());
        }
        assertTrue(console.getAllAgentStatus().isEmpty());
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
        if (testRepository != null) {
            DeploymentUtils.deleteRecursively(testRepository);
        }
    }

    @Test
    public void testInitialAgentStatus()
            throws Exception
    {
        console.updateAgentStatus(agentStatus);
        String json = agentStatusRepresentationCodec.toJson(AgentStatusRepresentation.from(agentStatus, server.getBaseUrl()));
        Response response = client.preparePut(urlFor("/v1/announce/" + agentStatus.getAgentId()))
                .setBody(json)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());

        assertEquals(console.getAgentStatus(agentStatus.getAgentId()), agentStatus);
    }

    @Test
    public void testUpdateAgentStatus()
            throws Exception
    {
        console.updateAgentStatus(agentStatus);
        AgentStatus newAgentStatus = new AgentStatus(agentStatus.getAgentId(), ImmutableList.of(new SlotStatus(UUID.randomUUID(), "foo", URI.create("fake://foo"))));

        String json = agentStatusRepresentationCodec.toJson(AgentStatusRepresentation.from(newAgentStatus, server.getBaseUrl()));
        Response response = client.preparePut(urlFor("/v1/announce/" + agentStatus.getAgentId()))
                .setBody(json)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());

        assertEquals(console.getAgentStatus(agentStatus.getAgentId()), newAgentStatus);
    }

    @Test
    public void testRemoveAgentStatus()
            throws Exception
    {
        console.updateAgentStatus(agentStatus);

        Response response = client.prepareDelete(urlFor("/v1/announce/" + agentStatus.getAgentId())).execute().get();
        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());

        assertNull(console.getAgentStatus(agentStatus.getAgentId()));
    }

    @Test
    public void testRemoveAgentStatusMissing()
            throws Exception
    {
        Response response = client.prepareDelete(urlFor("/v1/announce/" + UUID.randomUUID())).execute().get();
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    private String urlFor(String path)
    {
        return server.getBaseUrl().resolve(path).toString();
    }
}
