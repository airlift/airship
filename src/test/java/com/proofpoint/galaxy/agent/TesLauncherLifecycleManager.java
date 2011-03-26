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

import com.google.common.io.Files;
import com.proofpoint.galaxy.AssignmentHelper;
import com.proofpoint.galaxy.DeploymentUtils;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TesLauncherLifecycleManager extends AbstractLifecycleManagerTest
{
    private File tempDir;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        File goodLauncher = new File("src/test/archives/good/bin/launcher");
        if (!goodLauncher.canExecute()) {
            throw new RuntimeException("Expected good launcher to be executable: " + goodLauncher.getAbsolutePath());
        }

        tempDir = Files.createTempDir().getCanonicalFile();
        manager = new LauncherLifecycleManager(new AgentConfig().setSlotsDir(tempDir.getAbsolutePath()).setLauncherTimeout(new Duration(5, TimeUnit.SECONDS)));

        apple = createDeploymentDir(goodLauncher, "apple");
        banana = createDeploymentDir(goodLauncher, "banana");
    }

    private Deployment createDeploymentDir(File goodLauncher, String name)
            throws IOException
    {
        File deploymentDir = new File(tempDir, name);
        File launcher = new File(deploymentDir, "bin/launcher");

        // copy launcher script
        launcher.getParentFile().mkdirs();
        Files.copy(goodLauncher, launcher);
        launcher.setExecutable(true, true);

        return new Deployment(name, deploymentDir, AssignmentHelper.createMockAssignment("food.fruit:" + name + ":1.0", "@prod:" + name + ":1.0"));
    }

    @AfterMethod
    public void tearDown()
    {
        if (tempDir != null) {
            DeploymentUtils.deleteRecursively(tempDir);
        }
    }
}
