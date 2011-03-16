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

import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.LifecycleState.UNASSIGNED;
import static com.proofpoint.galaxy.RepositoryTestHelper.newAssignment;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestSlot
{
    @Test
    public void testInitialState()
            throws Exception
    {
        Slot manager = new Slot("slot", new AgentConfig(), new MockDeploymentManager(), new MockLifecycleManager());
        assertEquals(manager.getName(), "slot");

        // should start unassigned
        SlotStatus status = manager.status();
        assertEquals(status.getName(), "slot");
        assertEquals(status.getBinary(), null);
        assertEquals(status.getConfig(), null);
        assertEquals(status.getState(), UNASSIGNED);

        // lifecycle should fail when unassigned
        try {
            manager.start();
            fail("expected IllegalStateException");
        }
        catch (IllegalStateException expected) {
        }
        try {
            manager.restart();
            fail("expected IllegalStateException");
        }
        catch (IllegalStateException expected) {
        }
        try {
            manager.stop();
            fail("expected IllegalStateException");
        }
        catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testAssignment()
            throws Exception
    {
        Assignment apple = newAssignment("pp:apple:1.0", "@prod:apple:1.0");
        Assignment banana = newAssignment("pp:banana:1.0", "@prod:banana:1.0");

        Slot manager = new Slot("slot", new AgentConfig(), new MockDeploymentManager(), new MockLifecycleManager());
        assertEquals(manager.getName(), "slot");

        // assign apple and verify state
        SlotStatus status = manager.assign(apple);
        assertNotNull(status);
        assertEquals(status.getName(), "slot");
        assertEquals(status.getBinary(), apple.getBinary());
        assertEquals(status.getConfig(), apple.getConfig());
        assertEquals(status.getState(), STOPPED);
        assertEquals(manager.status(), status);

        // assign banana and verify state
        status = manager.assign(banana);
        assertNotNull(status);
        assertEquals(status.getName(), "slot");
        assertEquals(status.getBinary(), banana.getBinary());
        assertEquals(status.getConfig(), banana.getConfig());
        assertEquals(status.getState(), STOPPED);
        assertEquals(manager.status(), status);

        // clear and verify unassigned
        status = manager.clear();
        assertNotNull(status);
        assertEquals(status.getName(), "slot");
        assertEquals(status.getBinary(), null);
        assertEquals(status.getConfig(), null);
        assertEquals(status.getState(), UNASSIGNED);
        assertEquals(manager.status(), status);
    }

    @Test
    public void testLifecycle()
            throws Exception
    {
        Assignment apple = newAssignment("pp:apple:1.0", "@prod:apple:1.0");
        SlotStatus running = new SlotStatus("slot", apple.getBinary(), apple.getConfig(), RUNNING);
        SlotStatus stopped = new SlotStatus("slot", apple.getBinary(), apple.getConfig(), STOPPED);
        SlotStatus unassigned = new SlotStatus("slot");

        Slot manager = new Slot("slot", new AgentConfig(), new MockDeploymentManager(), new MockLifecycleManager());

        // default state is unassigned
        assertEquals(manager.status(), unassigned);

        // assign => stopped
        assertEquals(manager.assign(apple), stopped);

        // stopped.start => running
        assertEquals(manager.start(), running);
        assertEquals(manager.status(), running);

        // running.start => running
        assertEquals(manager.start(), running);
        assertEquals(manager.status(), running);

        // running.stop => stopped
        assertEquals(manager.stop(), stopped);
        assertEquals(manager.status(), stopped);

        // stopped.stop => stopped
        assertEquals(manager.stop(), stopped);
        assertEquals(manager.status(), stopped);

        // stopped.restart => running
        assertEquals(manager.restart(), running);
        assertEquals(manager.status(), running);

        // running.restart => running
        assertEquals(manager.restart(), running);
        assertEquals(manager.status(), running);
    }
}
