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
package io.airlift.airship.agent;

import io.airlift.airship.shared.SlotStatus;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.InstallationHelper.APPLE_INSTALLATION;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.InstallationHelper.BANANA_INSTALLATION;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestSlot
{
    @Test
    public void testAssignment()
            throws Exception
    {
        MockLifecycleManager lifecycleManager = new MockLifecycleManager();
        MockDeploymentManager deploymentManager = new MockDeploymentManager();

        // create slot with initial apple assignment
        Slot slot = new DeploymentSlot(URI.create("fake://localhost"),
                URI.create("fake://localhost"),
                deploymentManager,
                lifecycleManager,
                APPLE_INSTALLATION,
                new Duration(1, SECONDS));
        SlotStatus status = slot.status();
        assertNotNull(status);
        assertEquals(status.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(status.getState(), STOPPED);
        assertEquals(slot.status(), status);
        assertTrue(lifecycleManager.getNodeConfigUpdated().contains(deploymentManager.getDeployment().getNodeId()));

        // assign banana and verify state
        status = slot.assign(BANANA_INSTALLATION);
        assertNotNull(status);
        assertEquals(status.getAssignment(), BANANA_ASSIGNMENT);
        assertEquals(status.getState(), STOPPED);
        assertEquals(slot.status(), status);
        assertTrue(lifecycleManager.getNodeConfigUpdated().contains(deploymentManager.getDeployment().getNodeId()));

        // terminate and verify terminated
        status = slot.terminate();
        assertNotNull(status);
        assertEquals(status.getAssignment(), null);
        assertEquals(status.getState(), TERMINATED);
        assertEquals(slot.status(), status);
    }

    @Test
    public void testLifecycle()
            throws Exception
    {
        Slot slot = new DeploymentSlot(URI.create("fake://localhost"),
                URI.create("fake://localhost"),
                new MockDeploymentManager(),
                new MockLifecycleManager(),
                APPLE_INSTALLATION,
                new Duration(1, SECONDS));
        SlotStatus status1 = slot.status();
        SlotStatus running = status1.changeAssignment(RUNNING, APPLE_ASSIGNMENT, status1.getResources());
        SlotStatus status = slot.status();
        SlotStatus stopped = status.changeAssignment(STOPPED, APPLE_ASSIGNMENT, status.getResources());

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

        // running.kill => stopped
        assertEquals(slot.kill(), stopped);
        assertEquals(slot.status(), stopped);

        // stopped.kill => stopped
        assertEquals(slot.kill(), stopped);
        assertEquals(slot.status(), stopped);

    }
}
