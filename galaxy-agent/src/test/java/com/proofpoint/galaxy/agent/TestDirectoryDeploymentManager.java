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
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestDirectoryDeploymentManager
        extends AbstractDeploymentManagerTest
{
    private static final String DEPLOY_SCRIPT_SUCCESS = "#!/bin/sh\nexit 0\n";
    private static final String DEPLOY_SCRIPT_FAILURE = "#!/bin/sh\nexit 1\n";
    private static final String DEPLOY_SCRIPT_TIMEOUT = "#!/bin/sh\nsleep 5\n";
    private static final String DEPLOY_SCRIPT_INVALID = "\0invalid executable";

    private File tempDir;
    private InstallationHelper installationHelper;

    @BeforeClass
    public void createRepository()
            throws Exception
    {
        installationHelper = new InstallationHelper();
        appleInstallation = installationHelper.getAppleInstallation();
        bananaInstallation = installationHelper.getBananaInstallation();
    }

    @AfterClass
    public void removeRepository()
    {
        if (installationHelper != null) {
            installationHelper.destroy();
        }
    }

    @BeforeMethod
    public void setUp()
            throws IOException
    {
        AgentConfig config = new AgentConfig();
        // set a timeout shorter than DEPLOY_SCRIPT_TIMEOUT
        config.setDeployScriptTimeout(new Duration(1, TimeUnit.SECONDS));
        tempDir = Files.createTempDir().getCanonicalFile();
        manager = new DirectoryDeploymentManager(config, "slot", tempDir);
    }

    @AfterMethod
    public void tearDown()
    {
        if (tempDir != null) {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testPersistence()
    {
        // install apple and banana and activate apple
        Deployment appleDeployment = manager.install(appleInstallation);

        // replace the deployment manager with a new one, which will cause the persistent data to reload
        manager = new DirectoryDeploymentManager(new AgentConfig(), tempDir.getName(), tempDir);

        // active deployment should still be apple
        assertEquals(manager.getDeployment(), appleDeployment);

        // remove apple while active: no active deployment
        manager.clear();
        assertNull(manager.getDeployment());

        // replace the deployment manager again: this time no deployments are active
        manager = new DirectoryDeploymentManager(new AgentConfig(), "slot", tempDir);

        // no deployment should be active
        assertNull(manager.getDeployment());
    }

    @Test()
    public void testDeployScriptSuccess()
            throws Exception
    {
        manager.install(installationHelper.createInstallation(DEPLOY_SCRIPT_SUCCESS));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testDeployScriptFailure()
            throws Exception
    {
        manager.install(installationHelper.createInstallation(DEPLOY_SCRIPT_FAILURE));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testDeployScriptTimeout()
            throws Exception
    {
        manager.install(installationHelper.createInstallation(DEPLOY_SCRIPT_TIMEOUT));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testDeployScriptInvalid()
            throws Exception
    {
        manager.install(installationHelper.createInstallation(DEPLOY_SCRIPT_INVALID));
    }
}
