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
import com.proofpoint.galaxy.shared.ArchiveHelper;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.newInputStreamSupplier;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;

public class TestLauncherLifecycleManager extends AbstractLifecycleManagerTest
{
    private File tempDir;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        tempDir = Files.createTempDir().getCanonicalFile();
        manager = new LauncherLifecycleManager(new AgentConfig().setSlotsDir(tempDir.getAbsolutePath()).setLauncherTimeout(new Duration(5, TimeUnit.SECONDS)));

        appleDeployment = createDeploymentDir(APPLE_ASSIGNMENT);
        bananaDeployment = createDeploymentDir(BANANA_ASSIGNMENT);
    }

    private Deployment createDeploymentDir(Assignment assignment)
            throws IOException
    {
        String name = assignment.getConfig().getComponent();
        File deploymentDir = new File(tempDir, name);
        File launcher = new File(deploymentDir, "bin/launcher");

        // copy launcher script
        launcher.getParentFile().mkdirs();
        Files.copy(newInputStreamSupplier(getResource(ArchiveHelper.class, "launcher")), launcher);
        launcher.setExecutable(true, true);

        return new Deployment(name, deploymentDir, assignment);
    }

    @AfterMethod
    public void tearDown()
    {
        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
    }
}
