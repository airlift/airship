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
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.UpgradeVersions;
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

import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_BINARY_REPO;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_CONFIG_REPO;
import static java.lang.Math.max;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestCoordinatorAssignmentResource
{
    private CoordinatorAssignmentResource resource;
    private Coordinator coordinator;
    private String agentId;
    private int prefixSize;
    private UUID apple1SlotId;
    private UUID apple2SlotId;
    private UUID bananaSlotId;

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
                new LocalProvisioner(),
                new InMemoryStateManager(),
                new MockServiceInventory());
        resource = new CoordinatorAssignmentResource(coordinator);

        apple1SlotId = UUID.randomUUID();
        SlotStatus appleSlotStatus1 = new SlotStatus(apple1SlotId,
                "apple1",
                URI.create("fake://appleServer1/v1/agent/slot/apple1"),
                "location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/apple1",
                ImmutableMap.<String, Integer>of());
        apple2SlotId = UUID.randomUUID();
        SlotStatus appleSlotStatus2 = new SlotStatus(apple2SlotId,
                "apple2",
                URI.create("fake://appleServer2/v1/agent/slot/apple1"),
                "location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/apple2",
                ImmutableMap.<String, Integer>of());
        bananaSlotId = UUID.randomUUID();
        SlotStatus bananaSlotStatus = new SlotStatus(bananaSlotId,
                "banana",
                URI.create("fake://bananaServer/v1/agent/slot/banana"),
                "location",
                STOPPED,
                BANANA_ASSIGNMENT,
                "/banana",
                ImmutableMap.<String, Integer>of());

        agentId = UUID.randomUUID().toString();
        AgentStatus agentStatus = new AgentStatus(agentId,
                ONLINE,
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus),
                ImmutableMap.of("cpu", 8, "memory", 1024));

        prefixSize = max(CoordinatorSlotResource.MIN_PREFIX_SIZE, Strings.shortestUniquePrefix(asList(
                appleSlotStatus1.getId().toString(), appleSlotStatus2.getId().toString(), bananaSlotStatus.getId().toString())));

        coordinator.setAgentStatus(agentStatus);
    }

    @Test
    public void testUpgradeBoth()
    {
        testUpgrade(new UpgradeVersions("2.0", "2,0"));
    }

    @Test
    public void testUpgradeBinary()
    {
        testUpgrade(new UpgradeVersions("2.0", null));
    }

    @Test
    public void testUpgradeConfig()
    {
        testUpgrade(new UpgradeVersions(null, "2.0"));
    }

    private void testUpgrade(UpgradeVersions upgradeVersions)
    {
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.upgrade(upgradeVersions, uriInfo);

        AgentStatus agentStatus = coordinator.getAgentStatus(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SlotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        assertOkResponse(response, SlotLifecycleState.STOPPED, apple1Status, apple2Status);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), STOPPED);

        assertEquals(apple1Status.getAssignment(), upgradeVersions.upgradeAssignment(APPLE_ASSIGNMENT));
        assertEquals(apple2Status.getAssignment(), upgradeVersions.upgradeAssignment(APPLE_ASSIGNMENT));
        assertEquals(bananaStatus.getAssignment(), BANANA_ASSIGNMENT);
    }

    @Test
    public void testUpgradeAmbiguous()
    {
        UpgradeVersions upgradeVersions = new UpgradeVersions("2.0", "2,0");
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?state=stopped");
        try {
            resource.upgrade(upgradeVersions, uriInfo);
            fail("Expected AmbiguousUpgradeException");
        }
        catch (AmbiguousUpgradeException expected) {
        }
    }

    private void assertOkResponse(Response response, SlotLifecycleState state, SlotStatus... slots)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (SlotStatus slotStatus : slots) {
            builder.add(SlotStatusRepresentation.from(slotStatus.updateState(state), prefixSize));
        }
        assertEqualsNoOrder((Collection<?>) response.getEntity(), builder.build());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
