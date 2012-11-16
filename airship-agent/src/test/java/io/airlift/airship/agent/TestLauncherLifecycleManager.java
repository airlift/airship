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
package io.airlift.airship.agent;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.airlift.airship.shared.ArchiveHelper;
import io.airlift.airship.shared.Assignment;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.newInputStreamSupplier;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.FileUtils.deleteRecursively;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestLauncherLifecycleManager extends AbstractLifecycleManagerTest
{
    private File tempDir;
    private File slotDir;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        tempDir = Files.createTempDir().getCanonicalFile();
        slotDir = new File(tempDir, "slots");
        NodeInfo nodeInfo = new NodeInfo("prod");
        manager = new LauncherLifecycleManager(
                new AgentConfig()
                        .setSlotsDir(slotDir.getAbsolutePath())
                        .setLauncherTimeout(new Duration(5, TimeUnit.SECONDS)),
                nodeInfo,
                new HttpServerInfo(new HttpServerConfig(), nodeInfo));

        appleDeployment = createDeploymentDir("apple", APPLE_ASSIGNMENT);
        bananaDeployment = createDeploymentDir("banana", BANANA_ASSIGNMENT);
    }

    private Deployment createDeploymentDir(String name, Assignment assignment)
            throws IOException
    {
        File deploymentDir = new File(slotDir, name);
        File dataDir = new File(slotDir, "data");
        dataDir.mkdirs();
        File launcher = new File(deploymentDir, "bin/launcher");

        // copy launcher script
        launcher.getParentFile().mkdirs();
        Files.copy(newInputStreamSupplier(getResource(ArchiveHelper.class, "launcher")), launcher);
        launcher.setExecutable(true, true);

        return new Deployment(UUID.randomUUID(), "location", deploymentDir, dataDir, assignment, ImmutableMap.<String, Integer>of("memory", 512));
    }

    @AfterMethod
    public void tearDown()
    {
        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testUpdateNodeConfig()
            throws IOException
    {
        manager.updateNodeConfig(appleDeployment);
        verifyNodeConfig(appleDeployment);
    }

    @Test
    public void testNodeConfigAfterStart()
            throws IOException
    {
        assertEquals(manager.start(appleDeployment), RUNNING);
        verifyNodeConfig(appleDeployment);
    }

    @Test
    public void testNodeConfigAfterStop()
            throws IOException
    {
        assertEquals(manager.stop(appleDeployment), STOPPED);
        verifyNodeConfig(appleDeployment);
    }

    @Test
    public void testNodeConfigAfterRestart()
            throws IOException
    {
        assertEquals(manager.restart(appleDeployment), RUNNING);
        verifyNodeConfig(appleDeployment);
    }

    private static void verifyNodeConfig(Deployment deployment)
            throws IOException
    {
        File nodeConfig = new File(deployment.getDeploymentDir(), "etc/node.properties");
        assertTrue(nodeConfig.exists());

        String config = Files.toString(nodeConfig, Charsets.UTF_8);
        Properties properties = new Properties();
        properties.load(new StringReader(config));
        assertEquals(properties.getProperty("node.environment"), "prod");
        assertEquals(properties.getProperty("node.config-spec"), "@prod:apple:1.0");
        assertEquals(properties.getProperty("node.binary-spec"), "food.fruit:apple:1.0");
        assertEquals(properties.getProperty("node.location"), "location");
    }
}
