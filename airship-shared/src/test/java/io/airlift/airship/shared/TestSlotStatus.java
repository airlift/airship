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
package io.airlift.airship.shared;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static io.airlift.airship.shared.SlotLifecycleState.UNKNOWN;
import static io.airlift.airship.shared.SlotStatus.createSlotStatus;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;

public class TestSlotStatus
{
    @Test
    public void testEquivalence()
    {
        UUID appleId = UUID.randomUUID();
        URI appleSelf = URI.create("internal://apple");
        URI appleExternalUri = URI.create("external://apple");
        String applePath = "/apple";

        UUID bananaId = UUID.randomUUID();
        URI bananaSelf = URI.create("internal://banana");
        URI bananaExternalUri = URI.create("external://banana");
        String bananaPath = "/banana";

        equivalenceTester()
                .addEquivalentGroup(
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", TERMINATED, null, applePath, ImmutableMap.<String, Integer>of()),
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", TERMINATED, null, applePath, ImmutableMap.<String, Integer>of()))
                .addEquivalentGroup(
                        createSlotStatus(bananaId, appleSelf, appleExternalUri, "instance", "/location", TERMINATED, null, applePath, ImmutableMap.<String, Integer>of()),
                        createSlotStatus(bananaId, appleSelf, appleExternalUri, "instance", "/location", TERMINATED, null, applePath, ImmutableMap.<String, Integer>of()))
                .addEquivalentGroup(
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", RUNNING, APPLE_ASSIGNMENT, applePath, ImmutableMap.<String, Integer>of()),
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", RUNNING, APPLE_ASSIGNMENT, applePath, ImmutableMap.<String, Integer>of()))
                .addEquivalentGroup(
                        createSlotStatus(bananaId, bananaSelf, bananaExternalUri, "instance", "/location", RUNNING, APPLE_ASSIGNMENT, bananaPath, ImmutableMap.<String, Integer>of()),
                        createSlotStatus(bananaId, bananaSelf, bananaExternalUri, "instance", "/location", RUNNING, APPLE_ASSIGNMENT, bananaPath, ImmutableMap.<String, Integer>of()))
                .addEquivalentGroup(
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", RUNNING, BANANA_ASSIGNMENT, bananaPath, ImmutableMap.<String, Integer>of()),
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", RUNNING, BANANA_ASSIGNMENT, bananaPath, ImmutableMap.<String, Integer>of()))
                .addEquivalentGroup(
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", STOPPED, APPLE_ASSIGNMENT, applePath, ImmutableMap.<String, Integer>of()),
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", STOPPED, APPLE_ASSIGNMENT, applePath, ImmutableMap.<String, Integer>of()))
                .addEquivalentGroup(
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", UNKNOWN, APPLE_ASSIGNMENT, applePath, ImmutableMap.<String, Integer>of()),
                        createSlotStatus(appleId, appleSelf, appleExternalUri, "instance", "/location", UNKNOWN, APPLE_ASSIGNMENT, applePath, ImmutableMap.<String, Integer>of()))
                .check();
    }
}
