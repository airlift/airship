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
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_BINARY_REPO;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_CONFIG_REPO;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static java.lang.Math.max;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestCoordinatorLifecycleResource
{
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/lifecycle");
    private CoordinatorLifecycleResource resource;

    private Coordinator coordinator;
    private String agentId;
    private int prefixSize;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("testing");
        BinaryUrlResolver urlResolver = new BinaryUrlResolver(MOCK_BINARY_REPO, new HttpServerInfo(new HttpServerConfig(), nodeInfo));

        coordinator = new Coordinator(nodeInfo,
                new CoordinatorConfig().setStatusExpiration(new Duration(1, TimeUnit.DAYS)),
                new MockRemoteAgentFactory(),
                urlResolver,
                MOCK_CONFIG_REPO,
                new LocalConfigRepository(new CoordinatorConfig(), null),
                new LocalProvisioner(),
                new MockServiceInventory());
        resource = new CoordinatorLifecycleResource(coordinator);

        SlotStatus appleSlotStatus1 = new SlotStatus(UUID.randomUUID(),
                "apple1",
                URI.create("fake://foo/v1/agent/slot/apple1"),
                "location", STOPPED,
                APPLE_ASSIGNMENT,
                "/apple1");
        SlotStatus appleSlotStatus2 = new SlotStatus(UUID.randomUUID(),
                "apple2",
                URI.create("fake://foo/v1/agent/slot/apple1"),
                "location", STOPPED,
                APPLE_ASSIGNMENT,
                "/apple2");
        SlotStatus bananaSlotStatus = new SlotStatus(UUID.randomUUID(),
                "banana",
                URI.create("fake://foo/v1/agent/slot/banana"),
                "location", STOPPED,
                BANANA_ASSIGNMENT,
                "/banana");

        agentId = UUID.randomUUID().toString();
        AgentStatus agentStatus = new AgentStatus(agentId,
                ONLINE,
                URI.create("fake://foo/"),
                "unknown/location",
                "instance.type",
                ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus));

        prefixSize = max(CoordinatorSlotResource.MIN_PREFIX_SIZE, Strings.shortestUniquePrefix(asList(
                appleSlotStatus1.getId().toString(), appleSlotStatus2.getId().toString(),
                bananaSlotStatus.getId().toString())));

        coordinator.setAgentStatus(agentStatus);
    }

    @Test
    public void testMultipleStateMachineWithFilter()
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/lifecycle?binary=*:apple:*");

        // default state is stopped
        assertSlotState("apple1", STOPPED);
        assertSlotState("apple1", STOPPED);
        assertSlotState("banana", STOPPED);

        // stopped.start => running
        assertOkResponse(resource.setState("running", uriInfo), RUNNING, "apple1", "apple2");
        assertSlotState("apple1", RUNNING);
        assertSlotState("apple1", RUNNING);
        assertSlotState("banana", STOPPED);

        // running.start => running
        assertOkResponse(resource.setState("running", uriInfo), RUNNING, "apple1", "apple2");
        assertSlotState("apple1", RUNNING);
        assertSlotState("apple1", RUNNING);
        assertSlotState("banana", STOPPED);

        // running.stop => stopped
        assertOkResponse(resource.setState("stopped", uriInfo), STOPPED, "apple1", "apple2");
        assertSlotState("apple1", STOPPED);
        assertSlotState("apple1", STOPPED);
        assertSlotState("banana", STOPPED);

        // stopped.stop => stopped
        assertOkResponse(resource.setState("stopped", uriInfo), STOPPED, "apple1", "apple2");
        assertSlotState("apple1", STOPPED);
        assertSlotState("apple1", STOPPED);
        assertSlotState("banana", STOPPED);

        // stopped.restart => running
        assertOkResponse(resource.setState("restarting", uriInfo), RUNNING, "apple1", "apple2");
        assertSlotState("apple1", RUNNING);
        assertSlotState("apple1", RUNNING);
        assertSlotState("banana", STOPPED);

        // running.restart => running
        assertOkResponse(resource.setState("restarting", uriInfo), RUNNING, "apple1", "apple2");
        assertSlotState("apple1", RUNNING);
        assertSlotState("apple2", RUNNING);
        assertSlotState("banana", STOPPED);
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

    @Test(expectedExceptions = InvalidSlotFilterException.class)
    public void testSetStateNoFilter()
    {
        resource.setState("running", MockUriInfo.from("http://localhost/v1/slot/lifecycle"));
    }

    private void assertOkResponse(Response response, SlotLifecycleState state, String... slotNames)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (String slotName : slotNames) {
            SlotStatus slotStatus = agentStatus.getSlotStatus(slotName);
            builder.add(SlotStatusRepresentation.from(slotStatus.updateState(state), prefixSize));
            assertEquals(slotStatus.getAssignment(), APPLE_ASSIGNMENT);
        }
        assertEqualsNoOrder((Collection<?>) response.getEntity(), builder.build());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    private void assertSlotState(String slotName, SlotLifecycleState state)
    {
        assertEquals(coordinator.getAgentStatus(agentId).getSlotStatus(slotName).getState(), state);

    }
}
