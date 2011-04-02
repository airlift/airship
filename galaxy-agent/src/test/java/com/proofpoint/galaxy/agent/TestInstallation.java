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
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

public class TestInstallation
{
    @Test
    public void testConstructor()
    {
        URI binaryFiles = URI.create("fake://localhost/binaryFile");
        ImmutableMap<String, URI> configFiles = ImmutableMap.of("config", URI.create("fake://localhost/configFile"));

        Installation installation = new Installation(APPLE_ASSIGNMENT, binaryFiles, configFiles);

        assertEquals(installation.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(installation.getBinaryFile(), binaryFiles);
        assertEquals(installation.getConfigFiles(), configFiles);
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(
                        new Installation(APPLE_ASSIGNMENT, URI.create("fetch://binary.tar.gz"), ImmutableMap.of("config", URI.create("fetch://config.txt"))),
                        new Installation(APPLE_ASSIGNMENT, URI.create("fetch://anything.tar.gz"), ImmutableMap.of("config", URI.create("fetch://anything.txt")))

                ),
                asList(
                        new Installation(BANANA_ASSIGNMENT, URI.create("fetch://binary.tar.gz"), ImmutableMap.of("config", URI.create("fetch://config.txt"))),
                        new Installation(BANANA_ASSIGNMENT, URI.create("fetch://anything.tar.gz"), ImmutableMap.of("config", URI.create("fetch://anything.txt")))

                )
        );
    }
}
