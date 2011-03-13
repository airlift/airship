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

import com.google.common.io.Files;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;

import static com.proofpoint.galaxy.RepositoryTestHelper.createTestRepository;

public class TestDirectoryDeploymentManager extends AbstractDeploymentManagerTest
{
    private File tempDir;
    private File testRepository;

    @BeforeClass
    public void createRepository()
            throws Exception
    {
        testRepository = createTestRepository();
    }

    @AfterClass
    public void removeRepository()
    {
        DeploymentUtils.deleteRecursively(testRepository);
    }

    @BeforeMethod
    public void setUp()
            throws IOException
    {
        tempDir = Files.createTempDir().getCanonicalFile();
        manager = new DirectoryDeploymentManager(tempDir, testRepository.toURI());
    }

    @AfterMethod
    public void tearDown()
    {
        DeploymentUtils.deleteRecursively(tempDir);
    }
}
