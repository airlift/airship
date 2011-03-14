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

import static com.proofpoint.galaxy.RepositoryTestHelper.newAssignment;
import static java.util.Arrays.asList;

public class TestSlotStatus
{
    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(
                        new SlotStatus("apple"),
                        new SlotStatus("apple")

                ),
                asList(
                        new SlotStatus("apple", newAssignment("fruit:apple:1.0", "@prod:apple:1.0"), LifecycleState.RUNNING),
                        new SlotStatus("apple", newAssignment("fruit:apple:1.0", "@prod:apple:1.0"), LifecycleState.RUNNING)

                ),
                asList(
                        new SlotStatus("banana", newAssignment("fruit:apple:1.0", "@prod:apple:1.0"), LifecycleState.RUNNING),
                        new SlotStatus("banana", newAssignment("fruit:apple:1.0", "@prod:apple:1.0"), LifecycleState.RUNNING)

                ),
                asList(
                        new SlotStatus("apple", newAssignment("fruit:apple:2.0", "@prod:apple:1.0"), LifecycleState.RUNNING),
                        new SlotStatus("apple", newAssignment("fruit:apple:2.0", "@prod:apple:1.0"), LifecycleState.RUNNING)

                ),
                asList(
                        new SlotStatus("apple", newAssignment("fruit:apple:1.0", "@prod:apple:2.0"), LifecycleState.RUNNING),
                        new SlotStatus("apple", newAssignment("fruit:apple:1.0", "@prod:apple:2.0"), LifecycleState.RUNNING)

                ),
                asList(
                        new SlotStatus("apple", newAssignment("fruit:apple:1.0", "@prod:apple:1.0"), LifecycleState.STOPPED),
                        new SlotStatus("apple", newAssignment("fruit:apple:1.0", "@prod:apple:1.0"), LifecycleState.STOPPED)

                )
        );
    }
}
