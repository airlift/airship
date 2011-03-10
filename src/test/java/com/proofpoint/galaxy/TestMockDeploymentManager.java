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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestMockDeploymentManager
{
    @Test
    public void testStateMachine()
    {
        Assignment apple = new Assignment("pp:apple:1.0", "@prod:apple:1.0");
        Assignment banana = new Assignment("pp:banana:1.0", "@prod:banana:1.0");
        MockDeploymentManager manager = new MockDeploymentManager();

        // no active deployment by default
        assertNull(manager.getActiveDeployment());

        // install apple: no active deployment
        Deployment appleDeployment = manager.install(apple);
        assertNotNull(appleDeployment);
        assertNull(manager.getActiveDeployment());
        assertEquals(appleDeployment.getAssignment(), apple);

        // install banana: no active deployment
        Deployment bananaDeployment = manager.install(banana);
        assertNotNull(bananaDeployment);
        assertNull(manager.getActiveDeployment());
        assertEquals(bananaDeployment.getAssignment(), banana);

        // apple and banana should be in different deployments
        assertFalse(appleDeployment.equals(bananaDeployment));
        assertFalse(appleDeployment.getDeploymentId().equals(bananaDeployment.getDeploymentId()));
        assertFalse(appleDeployment.getDeploymentDir().equals(bananaDeployment.getDeploymentDir()));

        // activate apple
        manager.activate(appleDeployment.getDeploymentId());
        assertEquals(manager.getActiveDeployment(), appleDeployment);

        // activate banana
        manager.activate(bananaDeployment.getDeploymentId());
        assertEquals(manager.getActiveDeployment(), bananaDeployment);

        // remove banana while active: no active deployment
        manager.remove(bananaDeployment.getDeploymentId());
        assertNull(manager.getActiveDeployment());

        // activate banana: throws exception: no active deployment
        try {
            manager.activate(bananaDeployment.getDeploymentId());
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
        assertNull(manager.getActiveDeployment());

        // remove apple: no active deployment
        manager.remove(appleDeployment.getDeploymentId());
        assertNull(manager.getActiveDeployment());

        // activate apple: throws exception: no active deployment
        try {
            manager.activate(appleDeployment.getDeploymentId());
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
        assertNull(manager.getActiveDeployment());


        // remove apple again: no active deployment
        manager.remove(appleDeployment.getDeploymentId());
        assertNull(manager.getActiveDeployment());

    }
}
