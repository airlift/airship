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

import com.google.common.collect.ImmutableMap;
import io.airlift.airship.shared.Installation;
import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.assertEquals;

public class TestInstallation
{
    @Test
    public void testConstructor()
    {
        URI binaryFile = URI.create("fake://localhost/binaryFile");
        URI configFile = URI.create("fake://localhost/configFile");

        Installation installation = new Installation("apple", APPLE_ASSIGNMENT, binaryFile, configFile, ImmutableMap.of("memory", 512));

        assertEquals(installation.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(installation.getBinaryFile(), binaryFile);
        assertEquals(installation.getConfigFile(), configFile);
    }

    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(
                        new Installation("apple", APPLE_ASSIGNMENT, URI.create("fetch://binary.tar.gz"), URI.create("fetch://config.txt"), ImmutableMap.of("memory", 512)),
                        new Installation("apple", APPLE_ASSIGNMENT, URI.create("fetch://anything.tar.gz"), URI.create("fetch://anything.txt"), ImmutableMap.of("memory", 512)))
                .addEquivalentGroup(
                        new Installation("banana", BANANA_ASSIGNMENT, URI.create("fetch://binary.tar.gz"), URI.create("fetch://config.txt"), ImmutableMap.of("memory", 512)),
                        new Installation("banana", BANANA_ASSIGNMENT, URI.create("fetch://anything.tar.gz"), URI.create("fetch://anything.txt"), ImmutableMap.of("memory", 512)))
                .check();
    }
}
