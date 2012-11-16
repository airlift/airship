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

import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.VersionConflictException;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.UUID;

import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENT_VERSION_HEADER;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.InstallationHelper.APPLE_INSTALLATION;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_SLOT_VERSION_HEADER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestLifecycleResource
{
    private LifecycleResource resource;
    private Slot slot;
    private Agent agent;

    @BeforeMethod
    public void setup()
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        agent = new Agent(
                new AgentConfig().setSlotsDir(new File(tempDir, "slots").getAbsolutePath()),
                new HttpServerInfo(new HttpServerConfig(), new NodeInfo("test")),
                new NodeInfo("test"),
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager()
        );

        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        slot = agent.getSlot(slotStatus.getId());

        resource = new LifecycleResource(agent);
    }

    @Test
    public void testStateMachine()
    {

        // default state is stopped
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.start => running
        assertOkResponse(resource.setState(null, slot.getId(), "running"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.start => running
        assertOkResponse(resource.setState(null, slot.getId(), "running"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.stop => stopped
        assertOkResponse(resource.setState(null, slot.getId(), "stopped"), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.stop => stopped
        assertOkResponse(resource.setState(null, slot.getId(), "stopped"), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.restart => running
        assertOkResponse(resource.setState(null, slot.getId(), "restarting"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.restart => running
        assertOkResponse(resource.setState(null, slot.getId(), "restarting"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);
    }

    @Test
    public void testStateMachineWithVersions()
    {
        // default state is stopped
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.start => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getId(), "running"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.start => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getId(), "running"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.stop => stopped
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getId(), "stopped"), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.stop => stopped
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getId(), "stopped"), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.restart => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getId(), "restarting"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.restart => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getId(), "restarting"), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);
    }

    @Test
    public void testSetStateUnknown()
    {
        Response response = resource.setState(null, UUID.randomUUID(), "start");
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testSetStateUnknownState()
    {
        Response response = resource.setState(null, slot.getId(), "unknown");
        assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetStateNullSlotId()
    {
        resource.setState(null, null, "start");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetStateNullState()
    {
        resource.setState(null, slot.getId(), null);
    }

    @Test
    public void testInvalidVersion()
    {
        try {
            resource.setState("invalid-version", slot.getId(), "running");
            fail("Expected VersionConflictException");
        }
        catch (VersionConflictException e) {
            assertEquals(e.getName(), AIRSHIP_SLOT_VERSION_HEADER);
            assertEquals(e.getVersion(), slot.status().getVersion());
        }
    }

    private void assertOkResponse(Response response, SlotLifecycleState state)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        SlotStatusRepresentation expectedStatus = SlotStatusRepresentation.from(slot.status().changeState(state));
        assertEquals(response.getEntity(), expectedStatus);
        assertEquals(slot.status().getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(response.getMetadata().get(AIRSHIP_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
        assertEquals(response.getMetadata().get(AIRSHIP_SLOT_VERSION_HEADER).get(0), expectedStatus.getVersion());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
