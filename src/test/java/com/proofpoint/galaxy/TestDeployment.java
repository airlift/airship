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

import java.io.File;

import static com.proofpoint.galaxy.RepositoryTestHelper.newAssignment;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDeployment
{
    @Test
    public void testConstructor()
    {
        Assignment assignment = newAssignment("fruit:apple:1.0", "@prod:apple:1.0");
        Deployment deployment = new Deployment("one", new File("one"), assignment);

        assertEquals(deployment.getDeploymentId(), "one");
        assertEquals(deployment.getAssignment(), assignment);
        assertEquals(deployment.getDeploymentDir(), new File("one"));
    }

    @Test
    public void testNullConstructorArgs()
    {
        Assignment assignment = newAssignment("fruit:apple:1.0", "@prod:apple:1.0");

        try {
            new Deployment(null, new File("one"), assignment);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment("one", null, assignment);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment("one", new File("one"), null);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
    }

    @Test
    public void testEquivalence()
    {
        // identity is only based on deploymentId
        EquivalenceTester.check(
                asList(
                        new Deployment("one", new File("one"), newAssignment("fruit:apple:1.0", "@prod:apple:1.0")),
                        new Deployment("one", new File("other"), newAssignment("fruit:apple:1.0", "@prod:apple:1.0")),
                        new Deployment("one", new File("one"), newAssignment("fruit:apple:2.0", "@prod:apple:2.0"))
                ),
                asList(
                        new Deployment("two", new File("one"), newAssignment("fruit:apple:1.0", "@prod:apple:1.0")),
                        new Deployment("two", new File("other"), newAssignment("fruit:apple:1.0", "@prod:apple:1.0")),
                        new Deployment("two", new File("one"), newAssignment("fruit:apple:2.0", "@prod:apple:2.0"))
                )
        );
    }
}
