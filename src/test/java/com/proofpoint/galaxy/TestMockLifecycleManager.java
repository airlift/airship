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

import java.io.File;

import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;

public class TestMockLifecycleManager
{
    @Test
    public void testStateMachine()
    {
        Deployment deployment = new Deployment("apple", new File("apple"), new Assignment("pp:apple:1.0", "@prod:apple:1.0"));
        MockLifecycleManager manager = new MockLifecycleManager();

        // default state is stopped
        assertEquals(manager.status(deployment), STOPPED);

        // stopped.start => running
        assertEquals(manager.start(deployment), RUNNING);
        assertEquals(manager.status(deployment), RUNNING);

        // running.start => running
        assertEquals(manager.start(deployment), RUNNING);
        assertEquals(manager.status(deployment), RUNNING);

        // running.stop => stopped
        assertEquals(manager.stop(deployment), STOPPED);
        assertEquals(manager.status(deployment), STOPPED);

        // stopped.stop => stopped
        assertEquals(manager.stop(deployment), STOPPED);
        assertEquals(manager.status(deployment), STOPPED);

        // stopped.restart => running
        assertEquals(manager.restart(deployment), RUNNING);
        assertEquals(manager.status(deployment), RUNNING);

        // running.restart => running
        assertEquals(manager.restart(deployment), RUNNING);
        assertEquals(manager.status(deployment), RUNNING);
    }

    @Test
    public void testIsolation()
    {
        Deployment apple = new Deployment("apple", new File("apple"), new Assignment("pp:apple:1.0", "@prod:apple:1.0"));
        Deployment banana = new Deployment("banana", new File("banana"), new Assignment("pp:banana:1.0", "@prod:banana:1.0"));
        MockLifecycleManager manager = new MockLifecycleManager();

        // default state is stopped
        assertEquals(manager.status(apple), STOPPED);
        assertEquals(manager.status(banana), STOPPED);

        // start 1 doesn't effect 2
        assertEquals(manager.start(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);
        assertEquals(manager.status(banana), STOPPED);

        // now start 2
        assertEquals(manager.start(banana), RUNNING);
        assertEquals(manager.status(banana), RUNNING);

        // stop 1 doesn't effect 2
        assertEquals(manager.stop(apple), STOPPED);
        assertEquals(manager.status(apple), STOPPED);
        assertEquals(manager.status(banana), RUNNING);

        // restart 2 doesn't effect 1
        assertEquals(manager.restart(banana), RUNNING);
        assertEquals(manager.status(banana), RUNNING);
        assertEquals(manager.status(apple), STOPPED);

        // restart 1 doesn't effect 2
        assertEquals(manager.restart(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);
        assertEquals(manager.status(banana), RUNNING);
    }
}
