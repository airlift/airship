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

import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static java.util.Arrays.asList;

public class TestSlotStatus
{
    @Test
    public void testEquivalence()
    {
        UUID appleId = UUID.randomUUID();
        UUID bananaId = UUID.randomUUID();
        String binary = "fruit:apple:1.0";
        String config = "@prod:apple:1.0";
        EquivalenceTester.check(
                asList(
                        new SlotStatus(appleId, "apple", URI.create("fake://apple")),
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"))

                ),
                asList(
                        new SlotStatus(bananaId, "apple", URI.create("fake://apple")),
                        new SlotStatus(bananaId, "apple", URI.create("fake://apple"))

                ),
                asList(
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf(binary), ConfigSpec.valueOf(config), RUNNING),
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:1.0"), RUNNING)

                ),
                asList(
                        new SlotStatus(bananaId, "banana", URI.create("fake://banana"), BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:1.0"), RUNNING),
                        new SlotStatus(bananaId, "banana", URI.create("fake://banana"), BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:1.0"), RUNNING)

                ),
                asList(
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf("fruit:apple:2.0"), ConfigSpec.valueOf("@prod:apple:1.0"), RUNNING),
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf("fruit:apple:2.0"), ConfigSpec.valueOf("@prod:apple:1.0"), RUNNING)

                ),
                asList(
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:2.0"), RUNNING),
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:2.0"), RUNNING)

                ),
                asList(
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:1.0"), STOPPED),
                        new SlotStatus(appleId, "apple", URI.create("fake://apple"), BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:1.0"), STOPPED)
                )
        );
    }

}
