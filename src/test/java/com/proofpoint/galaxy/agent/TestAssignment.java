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
import com.proofpoint.galaxy.BinarySpec;
import com.proofpoint.galaxy.ConfigSpec;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

public class TestAssignment
{
    @Test
    public void testStringConstructor()
    {
        Assignment assignment = new Assignment(
                "fruit:apple:1.0",
                "fetch://binary.tar.gz",
                "@prod:apple:1.0",
                ImmutableMap.of("config", "fetch://config.txt"));

        Assert.assertEquals(assignment.getBinary(), BinarySpec.valueOf("fruit:apple:1.0"));
        assertEquals(assignment.getBinaryFile(), URI.create("fetch://binary.tar.gz"));
        Assert.assertEquals(assignment.getConfig(), ConfigSpec.valueOf("@prod:apple:1.0"));
        assertEquals(assignment.getConfigFiles(), ImmutableMap.of("config", URI.create("fetch://config.txt")));
    }

    @Test
    public void testObjectConstructor()
    {
        Assignment assignment = new Assignment(
                BinarySpec.valueOf("fruit:apple:1.0"),
                URI.create("fetch://binary.tar.gz"),
                ConfigSpec.valueOf("@prod:apple:1.0"),
                ImmutableMap.of("config", URI.create("fetch://config.txt")));

        assertEquals(assignment.getBinary(), BinarySpec.valueOf("fruit:apple:1.0"));
        assertEquals(assignment.getBinaryFile(), URI.create("fetch://binary.tar.gz"));
        assertEquals(assignment.getConfig(), ConfigSpec.valueOf("@prod:apple:1.0"));
        assertEquals(assignment.getConfigFiles(), ImmutableMap.of("config", URI.create("fetch://config.txt")));
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(
                        new Assignment(BinarySpec.valueOf("fruit:apple:1.0"), URI.create("fetch://binary.tar.gz"), ConfigSpec.valueOf("@prod:apple:1.0"), ImmutableMap.of("config", URI.create("fetch://config.txt"))),
                        new Assignment("fruit:apple:1.0", "fetch://binary.tar.gz", "@prod:apple:1.0", ImmutableMap.of("config", "fetch://config.txt")),
                        new Assignment("fruit:apple:1.0", "fetch://anything", "@prod:apple:1.0", ImmutableMap.of("config", "fetch://anything.txt"))

                ),
                asList(
                        new Assignment(BinarySpec.valueOf("fruit:banana:1.0"), URI.create("fetch://binary.tar.gz"), ConfigSpec.valueOf("@prod:banana:1.0"), ImmutableMap.of("config", URI.create("fetch://config.txt"))),
                        new Assignment("fruit:banana:1.0", "fetch://binary.tar.gz", "@prod:banana:1.0", ImmutableMap.of("config", "fetch://config.txt")),
                        new Assignment("fruit:banana:1.0", "fetch://anything.gz", "@prod:banana:1.0", ImmutableMap.of("config", "fetch://anything.txt"))

                )
        );
    }

}
