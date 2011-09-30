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
package com.proofpoint.galaxy.shared;

import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class TestSlotStatus
{
    @Test
    public void testEquivalence()
    {
        UUID appleId = UUID.randomUUID();
        String appleSlotName = "apple";
        URI appleSelf = URI.create("fake://apple");
        String applePath = "/apple";

        UUID bananaId = UUID.randomUUID();
        String bananaSlotName = "banana";
        URI bananaSelf = URI.create("fake://banana");
        String bananaPath = "/banana";

        equivalenceTester()
                .addEquivalentGroup(
                        new SlotStatus(appleId, appleSlotName, appleSelf, TERMINATED, null, applePath),
                        new SlotStatus(appleId, appleSlotName, appleSelf, TERMINATED, null, applePath))
                .addEquivalentGroup(
                        new SlotStatus(bananaId, appleSlotName, appleSelf, TERMINATED, null, applePath),
                        new SlotStatus(bananaId, appleSlotName, appleSelf, TERMINATED, null, applePath))
                .addEquivalentGroup(
                        new SlotStatus(appleId, appleSlotName, appleSelf, RUNNING, APPLE_ASSIGNMENT, applePath),
                        new SlotStatus(appleId, appleSlotName, appleSelf, RUNNING, APPLE_ASSIGNMENT, applePath))
                .addEquivalentGroup(
                        new SlotStatus(bananaId, bananaSlotName, bananaSelf, RUNNING, APPLE_ASSIGNMENT, bananaPath),
                        new SlotStatus(bananaId, bananaSlotName, bananaSelf, RUNNING, APPLE_ASSIGNMENT, bananaPath))
                .addEquivalentGroup(
                        new SlotStatus(appleId, appleSlotName, appleSelf, RUNNING, BANANA_ASSIGNMENT, bananaPath),
                        new SlotStatus(appleId, appleSlotName, appleSelf, RUNNING, BANANA_ASSIGNMENT, bananaPath))
                .addEquivalentGroup(
                        new SlotStatus(appleId, appleSlotName, appleSelf, STOPPED, APPLE_ASSIGNMENT, applePath),
                        new SlotStatus(appleId, appleSlotName, appleSelf, STOPPED, APPLE_ASSIGNMENT, applePath))
                .addEquivalentGroup(
                        new SlotStatus(appleId, appleSlotName, appleSelf, UNKNOWN, APPLE_ASSIGNMENT, applePath),
                        new SlotStatus(appleId, appleSlotName, appleSelf, UNKNOWN, APPLE_ASSIGNMENT, applePath))
                .check();
    }

}
