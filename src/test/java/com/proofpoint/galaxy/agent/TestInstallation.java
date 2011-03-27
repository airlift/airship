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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.galaxy.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.InstallationHelper.APPLE_INSTALLATION;
import static com.proofpoint.galaxy.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.RepoHelper.MOCK_BINARY_REPO;
import static com.proofpoint.galaxy.RepoHelper.MOCK_CONFIG_REPO;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

public class TestInstallation
{
    @Test
    public void testRepositoryConstructor()
    {
        Installation installation = new Installation(
                APPLE_ASSIGNMENT,
                MOCK_BINARY_REPO,
                MOCK_CONFIG_REPO);

        assertEquals(installation.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(installation.getBinaryFile(), APPLE_INSTALLATION.getBinaryFile());
        assertEquals(installation.getConfigFiles(), APPLE_INSTALLATION.getConfigFiles());
    }

    @Test
    public void testExplicitConstructor()
    {
        Installation installation = new Installation(
                APPLE_ASSIGNMENT,
                APPLE_INSTALLATION.getBinaryFile(),
                APPLE_INSTALLATION.getConfigFiles());

        assertEquals(installation.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(installation.getBinaryFile(), APPLE_INSTALLATION.getBinaryFile());
        assertEquals(installation.getConfigFiles(), APPLE_INSTALLATION.getConfigFiles());
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(
                        new Installation(APPLE_ASSIGNMENT, URI.create("fetch://binary.tar.gz"), ImmutableMap.of("config", URI.create("fetch://config.txt"))),
                        new Installation(APPLE_ASSIGNMENT, URI.create("fetch://anything.tar.gz"), ImmutableMap.of("config", URI.create("fetch://anything.txt"))),
                        new Installation(APPLE_ASSIGNMENT, MOCK_BINARY_REPO, MOCK_CONFIG_REPO)

                ),
                asList(
                        new Installation(BANANA_ASSIGNMENT, URI.create("fetch://binary.tar.gz"), ImmutableMap.of("config", URI.create("fetch://config.txt"))),
                        new Installation(BANANA_ASSIGNMENT, URI.create("fetch://anything.tar.gz"), ImmutableMap.of("config", URI.create("fetch://anything.txt"))),
                        new Installation(BANANA_ASSIGNMENT, MOCK_BINARY_REPO, MOCK_CONFIG_REPO)

                )
        );
    }
}
