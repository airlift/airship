package io.airlift.airship.agent;

import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.InetAddress;

import static io.airlift.airship.agent.ResourcesUtil.TEST_RESOURCES;
import static org.testng.Assert.assertEquals;

public class TestAgent
{
    private Agent agent;
    private NodeInfo nodeInfo;

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

        nodeInfo = new NodeInfo("test", "pool", "nodeId", InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), null, null, "location", "binarySpec", "configSpec");

        agent = new Agent(
                config,
                new HttpServerInfo(new HttpServerConfig(), nodeInfo),
                nodeInfo,
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager()
        );
    }

    @Test
    public void test()
    {
        assertEquals(agent.getAgentId(), nodeInfo.getNodeId());
        assertEquals(agent.getLocation(), agent.getLocation());
        assertEquals(agent.getResources(), TEST_RESOURCES);
    }

}
