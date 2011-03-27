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

import com.proofpoint.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.io.File;

import static com.proofpoint.galaxy.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.AssignmentHelper.BANANA_ASSIGNMENT;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDeployment
{
    @Test
    public void testConstructor()
    {
        Deployment deployment = new Deployment("one", new File("one"), APPLE_ASSIGNMENT);

        assertEquals(deployment.getDeploymentId(), "one");
        assertEquals(deployment.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(deployment.getDeploymentDir(), new File("one"));
    }

    @Test
    public void testNullConstructorArgs()
    {
        try {
            new Deployment(null, new File("one"), APPLE_ASSIGNMENT);
            fail("expected NullPointerException");
        }
        catch (NullPointerException expected) {
        }
        try {
            new Deployment("one", null, APPLE_ASSIGNMENT);
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
                        new Deployment("one", new File("one"), APPLE_ASSIGNMENT),
                        new Deployment("one", new File("other"), APPLE_ASSIGNMENT),
                        new Deployment("one", new File("one"), BANANA_ASSIGNMENT)
                ),
                asList(
                        new Deployment("two", new File("one"), APPLE_ASSIGNMENT),
                        new Deployment("two", new File("other"), APPLE_ASSIGNMENT),
                        new Deployment("two", new File("one"), BANANA_ASSIGNMENT)
                )
        );
    }
}
