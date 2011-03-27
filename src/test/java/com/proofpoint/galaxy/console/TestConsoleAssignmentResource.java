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

import static com.proofpoint.galaxy.AssignmentHelper.createMockAssignment;
import static com.proofpoint.galaxy.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.LifecycleState.UNASSIGNED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestConsoleAssignmentResource
{
    private final ConsoleAssignment apple = new ConsoleAssignment("food.fruit:apple:1.0", "@prod:apple:1.0");
    private final ConsoleAssignment banana = new ConsoleAssignment("food.fruit:banana:2.0-SNAPSHOT", "@prod:banana:1.0");
    private ConsoleAssignmentResource resource;

    private RemoteSlot appleSlot1;
    private RemoteSlot appleSlot2;
    private RemoteSlot bananaSlot;

    @BeforeMethod
    public void setup()
    {
        Console console = new Console(new MockRemoteSlotFactory(), new ConsoleConfig().setStatusExpiration(new Duration(100, TimeUnit.DAYS)));
        resource = new ConsoleAssignmentResource(console, new MockBinaryRepository(), new MockConfigRepository());

        SlotStatus appleSlotStatus1 = new SlotStatus(UUID.randomUUID(),
                "apple1",
                URI.create("fake://appleServer1/v1/slot/apple1"),
                UNASSIGNED);
        SlotStatus appleSlotStatus2 = new SlotStatus(UUID.randomUUID(),
                "apple2",
                URI.create("fake://appleServer2/v1/slot/apple1"),
                UNASSIGNED);
        SlotStatus bananaSlotStatus = new SlotStatus(UUID.randomUUID(),
                "banana",
                URI.create("fake://bananaServer/v1/slot/banana"),
                UNASSIGNED);

        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus));

        console.updateAgentStatus(agentStatus);

        appleSlot1 = console.getSlot(appleSlotStatus1.getId());
        appleSlot2 = console.getSlot(appleSlotStatus2.getId());
        bananaSlot = console.getSlot(bananaSlotStatus.getId());
    }

    @Test
    public void testAssign()
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.assign(ConsoleAssignmentRepresentation.from(apple), uriInfo);

        assertOkResponse(response, LifecycleState.STOPPED, appleSlot1, appleSlot2);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), UNASSIGNED);
    }

    @Test
    public void testReplaceAssignment()
    {
        appleSlot1.assign(createMockAssignment(banana.getBinary(), banana.getConfig()));
        assertEquals(appleSlot1.status().getBinary(), banana.getBinary());
        appleSlot2.assign(createMockAssignment(banana.getBinary(), banana.getConfig()));
        assertEquals(appleSlot2.status().getBinary(), banana.getBinary());

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.assign(ConsoleAssignmentRepresentation.from(apple), uriInfo);

        assertOkResponse(response, LifecycleState.STOPPED, appleSlot1, appleSlot2);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), UNASSIGNED);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullAssignment()
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        resource.assign(null, uriInfo);
    }

    private void assertOkResponse(Response response, LifecycleState state, RemoteSlot... slots)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (RemoteSlot slot : slots) {
            SlotStatus status = slot.status();
            if (state != UNASSIGNED) {
                builder.add(SlotStatusRepresentation.from(new SlotStatus(slot.getId(), status.getName(), status.getSelf(), apple.getBinary(), apple.getConfig(), state)));
            }
            else {
                builder.add(SlotStatusRepresentation.from(new SlotStatus(slot.getId(), status.getName(), status.getSelf(), state)));
            }
        }
        assertEqualsNoOrder((Collection<?>) response.getEntity(), builder.build());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testClear()
    {
        appleSlot1.assign(createMockAssignment(apple.getBinary(), apple.getConfig()));
        assertEquals(appleSlot1.status().getBinary(), apple.getBinary());
        appleSlot2.assign(createMockAssignment(apple.getBinary(), apple.getConfig()));
        assertEquals(appleSlot2.status().getBinary(), apple.getBinary());
        bananaSlot.assign(createMockAssignment(banana.getBinary(), banana.getConfig()));
        assertEquals(bananaSlot.status().getBinary(), banana.getBinary());

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.clear(uriInfo);

        assertOkResponse(response, LifecycleState.UNASSIGNED, appleSlot1, appleSlot2);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(appleSlot1.status().getState(), UNASSIGNED);
        assertEquals(appleSlot2.status().getState(), UNASSIGNED);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }
}
