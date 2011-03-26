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

import com.proofpoint.galaxy.LifecycleState;
import com.proofpoint.galaxy.MockUriInfo;
import com.proofpoint.galaxy.Slot;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.SlotStatusRepresentation;
import com.proofpoint.http.server.testing.MockHttpServerInfo;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static com.proofpoint.galaxy.AssignmentHelper.MOCK_APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestLifecycleResource
{
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/lifecycle");
    private LifecycleResource resource;
    private Slot slot;

    @BeforeMethod
    public void setup()
    {
        Agent agent = new Agent(new AgentConfig().setSlotsDir(System.getProperty("java.io.tmpdir")),
                new MockHttpServerInfo("fake://localhost"),
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager());
        slot = agent.addNewSlot();
        slot.assign(MOCK_APPLE_ASSIGNMENT);
        resource = new LifecycleResource(agent);
    }

    @Test
    public void testStateMachine()
    {

        // default state is stopped
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.start => running
        assertOkResponse(resource.setState(slot.getName(), "start", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.start => running
        assertOkResponse(resource.setState(slot.getName(), "start", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.stop => stopped
        assertOkResponse(resource.setState(slot.getName(), "stop", uriInfo), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.stop => stopped
        assertOkResponse(resource.setState(slot.getName(), "stop", uriInfo), STOPPED);
        assertEquals(slot.status().getState(), STOPPED);

        // stopped.restart => running
        assertOkResponse(resource.setState(slot.getName(), "restart", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);

        // running.restart => running
        assertOkResponse(resource.setState(slot.getName(), "restart", uriInfo), RUNNING);
        assertEquals(slot.status().getState(), RUNNING);
    }

    @Test
    public void testSetStateUnknown()
    {
        Response response = resource.setState("unknown", "start", uriInfo);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testSetStateUnknownState()
    {
        Response response = resource.setState(slot.getName(), "unknown", uriInfo);
        assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetStateNullSlotName()
    {
        resource.setState(null, "start", uriInfo);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetStateNullState()
    {
        resource.setState(slot.getName(), null, uriInfo);
    }

    private void assertOkResponse(Response response, LifecycleState state)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Assert.assertEquals(response.getEntity(),
                SlotStatusRepresentation.from(new SlotStatus(slot.getId(), slot.getName(), slot.getSelf(), MOCK_APPLE_ASSIGNMENT.getBinary(), MOCK_APPLE_ASSIGNMENT.getConfig(), state)
                ));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
