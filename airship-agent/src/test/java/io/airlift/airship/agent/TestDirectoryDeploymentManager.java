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

import com.google.common.io.Files;
import io.airlift.airship.shared.InstallationHelper;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static io.airlift.airship.shared.FileUtils.deleteRecursively;
import static org.testng.Assert.assertEquals;

public class TestDirectoryDeploymentManager
        extends AbstractDeploymentManagerTest
{
    private File tempDir;
    private InstallationHelper installationHelper;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        installationHelper = new InstallationHelper();
        appleInstallation = installationHelper.getAppleInstallation();
        bananaInstallation = installationHelper.getBananaInstallation();
        tempDir = Files.createTempDir().getCanonicalFile();
        final AgentConfig config = new AgentConfig();
        manager = new DirectoryDeploymentManager(tempDir, "/location/test", config.getTarTimeout());
    }

    @AfterMethod
    public void tearDown()
    {
        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
        if (installationHelper != null) {
            installationHelper.destroy();
        }
    }

    @Test
    public void testPersistence()
    {
        // install apple and banana and activate apple
        Deployment appleDeployment = manager.install(appleInstallation);

        // replace the deployment manager with a new one, which will cause the persistent data to reload
        final AgentConfig config = new AgentConfig();
        manager = new DirectoryDeploymentManager(tempDir, appleDeployment.getLocation(), config.getTarTimeout());

        // active deployment should still be apple
        assertEquals(manager.getDeployment(), appleDeployment);
    }
}
