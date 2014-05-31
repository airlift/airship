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
package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.MockUriInfo;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.UpgradeVersions;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.airlift.airship.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static io.airlift.airship.coordinator.TestingMavenRepository.MOCK_REPO;
import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT_2;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.ExtraAssertions.assertEqualsNoOrder;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotStatus.createSlotStatus;
import static io.airlift.airship.shared.Strings.shortestUniquePrefix;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

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

        MockProvisioner provisioner = new MockProvisioner();
        coordinator = new Coordinator(nodeInfo,
                new HttpServerInfo(new HttpServerConfig(), nodeInfo),
                new CoordinatorConfig().setStatusExpiration(new Duration(1, TimeUnit.DAYS)),
                provisioner.getCoordinatorFactory(),
                provisioner.getAgentFactory(),
                MOCK_REPO,
                provisioner,
                new InMemoryStateManager(),
                new MockServiceInventory());
        resource = new CoordinatorAssignmentResource(coordinator, MOCK_REPO);

        apple1SlotId = UUID.randomUUID();
        SlotStatus appleSlotStatus1 = createSlotStatus(apple1SlotId,
                URI.create("fake://appleServer1/v1/agent/slot/apple1"),
                URI.create("fake://appleServer1/v1/agent/slot/apple1"),
                "instance",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/apple1",
                ImmutableMap.<String, Integer>of());
        apple2SlotId = UUID.randomUUID();
        SlotStatus appleSlotStatus2 = createSlotStatus(apple2SlotId,
                URI.create("fake://appleServer2/v1/agent/slot/apple1"),
                URI.create("fake://appleServer2/v1/agent/slot/apple1"),
                "instance",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/apple2",
                ImmutableMap.<String, Integer>of());
        bananaSlotId = UUID.randomUUID();
        SlotStatus bananaSlotStatus = createSlotStatus(bananaSlotId,
                URI.create("fake://bananaServer/v1/agent/slot/banana"),
                URI.create("fake://bananaServer/v1/agent/slot/banana"),
                "instance",
                "/location",
                STOPPED,
                BANANA_ASSIGNMENT,
                "/banana",
                ImmutableMap.<String, Integer>of());

        agentId = UUID.randomUUID().toString();
        AgentStatus agentStatus = new AgentStatus(agentId,
                ONLINE,
                "instance-id",
                URI.create("fake://appleServer1/"),
                URI.create("fake://appleServer1/"),
                "/unknown/location",
                "instance.type",
                ImmutableList.of(appleSlotStatus1, appleSlotStatus2, bananaSlotStatus),
                ImmutableMap.of("cpu", 8, "memory", 1024));

        prefixSize = shortestUniquePrefix(asList(
                appleSlotStatus1.getId().toString(),
                appleSlotStatus2.getId().toString(),
                bananaSlotStatus.getId().toString()),
                MIN_PREFIX_SIZE);

        provisioner.addAgents(agentStatus);
        coordinator.updateAllAgentsAndWait();
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
        Response response = resource.upgrade(upgradeVersions, uriInfo, null, false);

        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SlotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        assertOkResponse(response, SlotLifecycleState.STOPPED, apple1Status, apple2Status);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), STOPPED);

        assertEquals(apple1Status.getAssignment(), upgradeVersions.upgradeAssignment(MOCK_REPO, APPLE_ASSIGNMENT));
        assertEquals(apple2Status.getAssignment(), upgradeVersions.upgradeAssignment(MOCK_REPO, APPLE_ASSIGNMENT));
        assertEquals(bananaStatus.getAssignment(), BANANA_ASSIGNMENT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* single binary.*food.fruit:apple:1.0, food.fruit:banana:2.0-SNAPSHOT")
    public void testUpgradeDifferentBinaries()
    {
        UpgradeVersions upgradeVersions = new UpgradeVersions("2.0", "2.0");

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?state=stopped");
        resource.upgrade(upgradeVersions, uriInfo, null, false);
    }

    @Test
    public void testUpgradeDifferentBinaryVersion()
    {
        // upgrade apple slot 1 to binary version 2.0, but leave everything else unchanged
        resource.upgrade(new UpgradeVersions("2.0", null), MockUriInfo.from("http://localhost/v1/slot/assignment?uuid=" + apple1SlotId), null, false);
        AgentStatus agentStatus = coordinator.getAgentByAgentId(agentId);
        SlotStatus apple1Status = agentStatus.getSlotStatus(apple1SlotId);
        SlotStatus apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        SlotStatus bananaStatus = agentStatus.getSlotStatus(bananaSlotId);
        assertEquals(apple1Status.getAssignment().getBinary(), APPLE_ASSIGNMENT_2.getBinary());
        assertEquals(apple2Status.getAssignment().getBinary(), APPLE_ASSIGNMENT.getBinary());
        assertEquals(bananaStatus.getAssignment().getBinary(), BANANA_ASSIGNMENT.getBinary());

        // upgrade all apple slots to binary and config version2.0
        UpgradeVersions upgradeVersions = new UpgradeVersions("2.0", "2.0");
        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.upgrade(upgradeVersions, uriInfo, null, false);

        coordinator.getAgentByAgentId(agentId);
        agentStatus = coordinator.getAgentByAgentId(agentId);
        apple1Status = agentStatus.getSlotStatus(apple1SlotId);
        apple2Status = agentStatus.getSlotStatus(apple2SlotId);
        bananaStatus = agentStatus.getSlotStatus(bananaSlotId);

        assertOkResponse(response, SlotLifecycleState.STOPPED, apple1Status, apple2Status);
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(apple1Status.getState(), STOPPED);
        assertEquals(apple2Status.getState(), STOPPED);
        assertEquals(bananaStatus.getState(), STOPPED);

        assertEquals(apple1Status.getAssignment(), upgradeVersions.upgradeAssignment(MOCK_REPO, APPLE_ASSIGNMENT));
        assertEquals(apple2Status.getAssignment(), upgradeVersions.upgradeAssignment(MOCK_REPO, APPLE_ASSIGNMENT));
        assertEquals(bananaStatus.getAssignment(), BANANA_ASSIGNMENT);
    }

    private void assertOkResponse(Response response, SlotLifecycleState state, SlotStatus... slots)
    {
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (SlotStatus slotStatus : slots) {
            builder.add(SlotStatusRepresentation.from(slotStatus.changeState(state), prefixSize, MOCK_REPO));
        }
        assertEqualsNoOrder((Collection<?>) response.getEntity(), builder.build());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
