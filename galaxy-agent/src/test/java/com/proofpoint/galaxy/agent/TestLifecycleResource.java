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

import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.io.File;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.InstallationHelper.APPLE_INSTALLATION;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestLifecycleResource
{
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/agent/slot/lifecycle");
    private LifecycleResource resource;
    private Slot slot;

    @BeforeMethod
    public void setup()
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        Agent agent = new Agent(
                new AgentConfig().setSlotsDir(new File(tempDir, "slots").getAbsolutePath()),
                new HttpServerInfo(new HttpServerConfig(), new NodeInfo("test")),
                new NodeInfo("test"),
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager()
        );

        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        slot = agent.getSlot(slotStatus.getName());

        resource = new LifecycleResource(agent);
    }

    @Test
    public void testStateMachine()
    {

        // default state is stopped
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.start => running
        assertOkResponse(resource.setState(null, slot.getName(), "running", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.start => running
        assertOkResponse(resource.setState(null, slot.getName(), "running", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.stop => stopped
        assertOkResponse(resource.setState(null, slot.getName(), "stopped", uriInfo), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.stop => stopped
        assertOkResponse(resource.setState(null, slot.getName(), "stopped", uriInfo), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.restart => running
        assertOkResponse(resource.setState(null, slot.getName(), "restarting", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.restart => running
        assertOkResponse(resource.setState(null, slot.getName(), "restarting", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);
    }

    @Test
    public void testStateMachineWithVersions()
    {
        // default state is stopped
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.start => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getName(), "running", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.start => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getName(), "running", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.stop => stopped
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getName(), "stopped", uriInfo), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.stop => stopped
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getName(), "stopped", uriInfo), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.restart => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getName(), "restarting", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.restart => running
        assertOkResponse(resource.setState(slot.status().getVersion(), slot.getName(), "restarting", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);
    }

    @Test
    public void testSetStateUnknown()
    {
        Response response = resource.setState(null, "unknown", "start", uriInfo);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testSetStateUnknownState()
    {
        Response response = resource.setState(null, slot.getName(), "unknown", uriInfo);
        assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetStateNullSlotName()
    {
        resource.setState(null, null, "start", uriInfo);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetStateNullState()
    {
        resource.setState(null, slot.getName(), null, uriInfo);
    }

    @Test
    public void testInvalidVersion()
    {
        SlotStatus slotStatus = slot.status();
        try {
            resource.setState("invalid-version", slot.getName(), "running", uriInfo);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), Status.CONFLICT.getStatusCode());
            SlotStatusRepresentation actualStatus = (SlotStatusRepresentation) e.getResponse().getEntity();
            assertEquals(actualStatus, SlotStatusRepresentation.from(slotStatus));

        }
    }

    private void assertOkResponse(Response response, SlotLifecycleState state)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Assert.assertEquals(response.getEntity(), SlotStatusRepresentation.from(slot.status().updateState(state)));
        Assert.assertEquals(slot.status().getAssignment(), APPLE_ASSIGNMENT);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
