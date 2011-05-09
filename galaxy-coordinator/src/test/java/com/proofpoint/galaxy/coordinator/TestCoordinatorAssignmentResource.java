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
package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.LifecycleState;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.LifecycleState.UNASSIGNED;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_BINARY_REPO;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_CONFIG_REPO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestCoordinatorAssignmentResource
{
    private CoordinatorAssignmentResource resource;

    private RemoteSlot appleSlot1;
    private RemoteSlot appleSlot2;
    private RemoteSlot bananaSlot;

    @BeforeMethod
    public void setup()
    {
        Coordinator coordinator = new Coordinator(new MockRemoteSlotFactory(), new CoordinatorConfig().setStatusExpiration(new Duration(100, TimeUnit.DAYS)));
        resource = new CoordinatorAssignmentResource(coordinator, MOCK_BINARY_REPO, MOCK_CONFIG_REPO);

        SlotStatus appleSlotStatus1 = new SlotStatus(UUID.randomUUID(),
                "apple1",
                URI.create("fake://appleServer1/v1/slot/apple1"));
        SlotStatus appleSlotStatus2 = new SlotStatus(UUID.randomUUID(),
                "apple2",
                URI.create("fake://appleServer2/v1/slot/apple1"));
        SlotStatus bananaSlotStatus = new SlotStatus(UUID.randomUUID(),
                "banana",
                URI.create("fake://bananaServer/v1/slot/banana"));

        AgentStatus agentStatus = new AgentStatus(URI.create("fake://appleServer1/"), UUID.randomUUID(), ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus));

        coordinator.updateAgentStatus(agentStatus);

        appleSlot1 = coordinator.getSlot(appleSlotStatus1.getId());
        appleSlot2 = coordinator.getSlot(appleSlotStatus2.getId());
        bananaSlot = coordinator.getSlot(bananaSlotStatus.getId());
    }

    @Test
    public void testAssign()
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.assign(AssignmentRepresentation.from(APPLE_ASSIGNMENT), uriInfo);

        assertOkResponse(response, LifecycleState.STOPPED, appleSlot1, appleSlot2);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(appleSlot1.status().getState(), STOPPED);
        assertEquals(appleSlot2.status().getState(), STOPPED);
        assertEquals(bananaSlot.status().getState(), UNASSIGNED);
    }

    @Test(expectedExceptions = InvalidSlotFilterException.class)
    public void testAssignNoFilterException()
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");
        resource.assign(AssignmentRepresentation.from(APPLE_ASSIGNMENT), uriInfo);
    }

    @Test
    public void testReplaceAssignment()
    {
        appleSlot1.assign(makeAssignment(BANANA_ASSIGNMENT));
        assertEquals(appleSlot1.status().getAssignment(), BANANA_ASSIGNMENT);
        appleSlot2.assign(makeAssignment(BANANA_ASSIGNMENT));
        assertEquals(appleSlot2.status().getAssignment(), BANANA_ASSIGNMENT);

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.assign(AssignmentRepresentation.from(APPLE_ASSIGNMENT), uriInfo);

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
            if (state != UNASSIGNED) {
                builder.add(SlotStatusRepresentation.from(new SlotStatus(slot.status(), state)));
                assertEquals(slot.status().getAssignment(), APPLE_ASSIGNMENT);
            }
            else {
                builder.add(SlotStatusRepresentation.from(new SlotStatus(slot.status(), state)));
            }
        }
        assertEqualsNoOrder((Collection<?>) response.getEntity(), builder.build());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testClear()
    {

        appleSlot1.assign(makeAssignment(APPLE_ASSIGNMENT));
        assertEquals(appleSlot1.status().getAssignment(), APPLE_ASSIGNMENT);
        appleSlot2.assign(makeAssignment(APPLE_ASSIGNMENT));
        assertEquals(appleSlot2.status().getAssignment(), APPLE_ASSIGNMENT);
        bananaSlot.assign(makeAssignment(BANANA_ASSIGNMENT));
        assertEquals(bananaSlot.status().getAssignment(), BANANA_ASSIGNMENT);

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.clear(uriInfo);

        assertOkResponse(response, LifecycleState.UNASSIGNED, appleSlot1, appleSlot2);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(appleSlot1.status().getState(), UNASSIGNED);
        assertEquals(appleSlot2.status().getState(), UNASSIGNED);
        assertEquals(bananaSlot.status().getState(), STOPPED);
    }

    @Test(expectedExceptions = InvalidSlotFilterException.class)
    public void testClearNoFilterException()
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");
        resource.clear(uriInfo);
    }

    private static Installation makeAssignment(Assignment appleAssignment)
    {
        return new Installation(appleAssignment,
                    URI.create("fake://localhost/binaryFile"),
                    ImmutableMap.of("config", URI.create("fake://localhost/configFile")));
    }
}
