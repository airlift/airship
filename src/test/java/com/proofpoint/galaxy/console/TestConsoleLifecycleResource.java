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
package com.proofpoint.galaxy.console;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.proofpoint.galaxy.AgentStatus;
import com.proofpoint.galaxy.LifecycleState;
import com.proofpoint.galaxy.MockUriInfo;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.SlotStatusRepresentation;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestConsoleLifecycleResource
{
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/lifecycle");
    private ConsoleLifecycleResource resource;

    private RemoteSlot appleSlot1;
    private RemoteSlot appleSlot2;
    private RemoteSlot bananaSlot;

    @BeforeMethod
    public void setup()
    {
        Console console = new Console(new MockRemoteSlotFactory(), new ConsoleConfig().setStatusExpiration(new Duration(100, TimeUnit.DAYS)));
        resource = new ConsoleLifecycleResource(console);

        SlotStatus appleSlotStatus1 = new SlotStatus(UUID.randomUUID(),
                "apple1",
                URI.create("fake://foo/v1/slot/apple1"),
                STOPPED,
                APPLE_ASSIGNMENT);
        SlotStatus appleSlotStatus2 = new SlotStatus(UUID.randomUUID(),
                "apple2",
                URI.create("fake://foo/v1/slot/apple1"),
                STOPPED,
                APPLE_ASSIGNMENT);
        SlotStatus bananaSlotStatus = new SlotStatus(UUID.randomUUID(),
                "banana",
                URI.create("fake://foo/v1/slot/banana"),
                STOPPED,
                BANANA_ASSIGNMENT);

        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus));

        console.updateAgentStatus(agentStatus);

        appleSlot1 = console.getSlot(appleSlotStatus1.getId());
        appleSlot2 = console.getSlot(appleSlotStatus2.getId());
        bananaSlot = console.getSlot(bananaSlotStatus.getId());

    }

    @Test
    public void testMultipleStateMachineWithFilter()
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/lifecycle?binary=*:apple:*");

        // default state is stopped
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        // stopped.start => running
        assertOkResponse(resource.setState("start", uriInfo), RUNNING, appleSlot1, appleSlot2);
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        // running.start => running
        assertOkResponse(resource.setState("start", uriInfo), RUNNING, appleSlot1, appleSlot2);
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        // running.stop => stopped
        assertOkResponse(resource.setState("stop", uriInfo), STOPPED, appleSlot1, appleSlot2);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        // stopped.stop => stopped
        assertOkResponse(resource.setState("stop", uriInfo), STOPPED, appleSlot1, appleSlot2);
        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        // stopped.restart => running
        assertOkResponse(resource.setState("restart", uriInfo), RUNNING, appleSlot1, appleSlot2);
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);

        // running.restart => running
        assertOkResponse(resource.setState("restart", uriInfo), RUNNING, appleSlot1, appleSlot2);
        assertEquals(appleSlot1.status().getState(), RUNNING);
        assertEquals(appleSlot2.status().getState(), RUNNING);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test
    public void testSetStateUnknownState()
    {
        Response response = resource.setState("unknown", uriInfo);
        assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testSetStateNullState()
    {
        resource.setState(null, uriInfo);
    }

    private void assertOkResponse(Response response, LifecycleState state, RemoteSlot... slots)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (RemoteSlot slot : slots) {
            builder.add(SlotStatusRepresentation.from(new SlotStatus(slot.status(), state)));
            assertEquals(slot.status().getAssignment(), APPLE_ASSIGNMENT);
        }
        assertEqualsNoOrder((Collection<?>) response.getEntity(), builder.build());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
