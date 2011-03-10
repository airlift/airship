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

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

public class TestAssignment
{
    @Test
    public void testStringConstructor()
    {
        Assignment assignment = new Assignment("fruit:apple:1.0", "@prod:apple:1.0");
        assertEquals(assignment.getBinary(), BinarySpec.valueOf("fruit:apple:1.0"));
        assertEquals(assignment.getConfig(), ConfigSpec.valueOf("@prod:apple:1.0"));
    }

    @Test
    public void testObjectConstructor()
    {
        Assignment assignment = new Assignment(BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:1.0"));
        assertEquals(assignment.getBinary(), BinarySpec.valueOf("fruit:apple:1.0"));
        assertEquals(assignment.getConfig(), ConfigSpec.valueOf("@prod:apple:1.0"));
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(
                        new Assignment(BinarySpec.valueOf("fruit:apple:1.0"), ConfigSpec.valueOf("@prod:apple:1.0")),
                        new Assignment("fruit:apple:1.0", "@prod:apple:1.0")

                ),
                asList(
                        new Assignment(BinarySpec.valueOf("fruit:banana:1.0"), ConfigSpec.valueOf("@prod:banana:1.0")),
                        new Assignment("fruit:banana:1.0", "@prod:banana:1.0")

                )
        );
    }

}
