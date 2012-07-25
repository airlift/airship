package com.proofpoint.galaxy.agent;

import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.io.File;

import static com.proofpoint.galaxy.agent.ResourcesUtil.TEST_RESOURCES;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static org.testng.Assert.assertEquals;

public class TestAgentResource
{
    private Agent agent;
    private AgentResource agentResource;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        File resourcesFile = new File(tempDir, "slots/resources.properties");
        ResourcesUtil.writeResources(TEST_RESOURCES, resourcesFile);

        AgentConfig config = new AgentConfig()
                .setSlotsDir(new File(tempDir, "slots").getAbsolutePath())
                .setResourcesFile(resourcesFile.getAbsolutePath());

        agent = new Agent(
                config,
                new HttpServerInfo(new HttpServerConfig(), new NodeInfo("test")),
                new NodeInfo("test"),
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager()
        );
        agentResource = new AgentResource(agent);
    }

    @Test
    public void testGetAllSlotsStatus()
    {
        Response response = agentResource.getAllSlotsStatus();
        AgentStatusRepresentation actual = (AgentStatusRepresentation) response.getEntity();
        assertEquals(actual.getAgentId(), agent.getAgentId());
        assertEquals(actual.getState(), ONLINE);
        assertEquals(actual.getInstanceType(), null);
        assertEquals(actual.getResources(), TEST_RESOURCES);
        assertEquals(actual.getLocation(), agent.getLocation());
    }

}
