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
package com.proofpoint.galaxy.agent;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.proofpoint.galaxy.shared.ArchiveHelper;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.newInputStreamSupplier;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
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

        appleDeployment = createDeploymentDir(APPLE_ASSIGNMENT);
        bananaDeployment = createDeploymentDir(BANANA_ASSIGNMENT);
    }

    private Deployment createDeploymentDir(Assignment assignment)
            throws IOException
    {
        String name = assignment.getConfig().getComponent();
        File deploymentDir = new File(slotDir, name);
        File dataDir = new File(slotDir, "data");
        dataDir.mkdirs();
        File launcher = new File(deploymentDir, "bin/launcher");

        // copy launcher script
        launcher.getParentFile().mkdirs();
        Files.copy(newInputStreamSupplier(getResource(ArchiveHelper.class, "launcher")), launcher);
        launcher.setExecutable(true, true);

        return new Deployment(name, "slot", UUID.randomUUID(), "location", deploymentDir, dataDir, assignment);
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

        String conf = Files.toString(nodeConfig, Charsets.UTF_8);
        assertTrue(conf.contains("node.pool=general"));
    }
}
