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

import com.proofpoint.galaxy.Slot;
import com.proofpoint.galaxy.SlotStatus;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.galaxy.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.InstallationHelper.APPLE_INSTALLATION;
import static com.proofpoint.galaxy.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.InstallationHelper.BANANA_INSTALLATION;
import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.LifecycleState.UNASSIGNED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestSlot
{

    @Test
    public void testInitialState()
            throws Exception
    {
        Slot manager = new DeploymentSlot("slot", new AgentConfig(), URI.create("fake://localhost"), new MockDeploymentManager(), new MockLifecycleManager());
        assertEquals(manager.getName(), "slot");

        // should start unassigned
        SlotStatus status = manager.status();
        assertEquals(status.getName(), "slot");
        assertEquals(status.getAssignment(), null);
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

        Slot manager = new DeploymentSlot("slot", new AgentConfig(), URI.create("fake://localhost"), new MockDeploymentManager(), new MockLifecycleManager());
        assertEquals(manager.getName(), "slot");

        // assign apple and verify state
        SlotStatus status = manager.assign(APPLE_INSTALLATION);
        assertNotNull(status);
        assertEquals(status.getName(), "slot");
        assertEquals(status.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(status.getState(), STOPPED);
        assertEquals(manager.status(), status);

        // assign banana and verify state
        status = manager.assign(BANANA_INSTALLATION);
        assertNotNull(status);
        assertEquals(status.getName(), "slot");
        assertEquals(status.getAssignment(), BANANA_ASSIGNMENT);
        assertEquals(status.getState(), STOPPED);
        assertEquals(manager.status(), status);

        // clear and verify unassigned
        status = manager.clear();
        assertNotNull(status);
        assertEquals(status.getName(), "slot");
        assertEquals(status.getAssignment(), null);
        assertEquals(status.getState(), UNASSIGNED);
        assertEquals(manager.status(), status);
    }

    @Test
    public void testLifecycle()
            throws Exception
    {

        Slot slot = new DeploymentSlot("slot", new AgentConfig(), URI.create("fake://localhost"), new MockDeploymentManager(), new MockLifecycleManager());
        SlotStatus running = new SlotStatus(slot.status(), RUNNING, APPLE_ASSIGNMENT);
        SlotStatus stopped = new SlotStatus(slot.status(), STOPPED, APPLE_ASSIGNMENT);
        SlotStatus unassigned = new SlotStatus(slot.getId(), slot.getName(), slot.getSelf());

        // default state is unassigned
        assertEquals(slot.status(), unassigned);

        // assign => stopped
        assertEquals(slot.assign(APPLE_INSTALLATION), stopped);

        // stopped.start => running
        assertEquals(slot.start(), running);
        assertEquals(slot.status(), running);

        // running.start => running
        assertEquals(slot.start(), running);
        assertEquals(slot.status(), running);

        // running.stop => stopped
        assertEquals(slot.stop(), stopped);
        assertEquals(slot.status(), stopped);

        // stopped.stop => stopped
        assertEquals(slot.stop(), stopped);
        assertEquals(slot.status(), stopped);

        // stopped.restart => running
        assertEquals(slot.restart(), running);
        assertEquals(slot.status(), running);

        // running.restart => running
        assertEquals(slot.restart(), running);
        assertEquals(slot.status(), running);
    }
}
