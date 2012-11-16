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

import com.google.common.collect.ImmutableMultiset;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.MockUriInfo;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.VersionConflictException;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENT_VERSION_HEADER;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.ExtraAssertions.assertEqualsNoOrder;
import static io.airlift.airship.shared.InstallationHelper.APPLE_INSTALLATION;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_SLOT_VERSION_HEADER;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestSlotResource
{
    private SlotResource resource;
    private Agent agent;
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/agent/slot");

    @BeforeMethod
    public void setup()
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        AgentConfig agentConfig = new AgentConfig()
                .setSlotsDir(new File(tempDir, "slots").getAbsolutePath());
        HttpServerInfo httpServerInfo = new HttpServerInfo(new HttpServerConfig(), new NodeInfo("test"));
        agent = new Agent(agentConfig,
                httpServerInfo,
                new NodeInfo("test"),
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager()
        );
        resource = new SlotResource(agent);
    }

    @Test
    public void testGetSlotStatus()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);

        URI requestUri = URI.create("http://localhost/v1/agent/slot/" + slotStatus.getId().toString());
        Response response = resource.getSlotStatus(slotStatus.getId(), MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(slotStatus));
        assertEquals(response.getMetadata().get(AIRSHIP_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
        assertEquals(response.getMetadata().get(AIRSHIP_SLOT_VERSION_HEADER).get(0), slotStatus.getVersion());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetSlotStatusUnknown()
    {
        Response response = resource.getSlotStatus(UUID.randomUUID(), MockUriInfo.from("http://localhost/v1/agent/slot/unknown"));
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testGetSlotStatusNull()
    {
        resource.getSlotStatus(null, MockUriInfo.from(URI.create("http://localhost/v1/agent/slot/null")));
    }

    @Test
    public void testGetAllSlotStatusEmpty()
    {
        Response response = resource.getAllSlotsStatus(uriInfo);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        assertEquals((Collection<?>) response.getEntity(), newArrayList());
        assertEquals(response.getMetadata().get(AIRSHIP_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
    }

    @Test
    public void testGetAllSlotStatus()
    {
        SlotStatus slotStatus1 = agent.install(APPLE_INSTALLATION);
        SlotStatus slotStatus2 = agent.install(APPLE_INSTALLATION);

        Response response = resource.getAllSlotsStatus(uriInfo);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        assertEqualsNoOrder((Collection<?>) response.getEntity(), ImmutableMultiset.of(
                SlotStatusRepresentation.from(slotStatus1),
                SlotStatusRepresentation.from(slotStatus2)
        ));
        assertEquals(response.getMetadata().get(AIRSHIP_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
    }

    @Test
    public void testInstallSlot()
    {
        assertInstallSlot(agent.getAgentStatus().getVersion());
    }

    @Test
    public void testInstallSlotNoVersion()
    {
        assertInstallSlot(null);
    }

    public void assertInstallSlot(String agentVersion)
    {
        Response response = resource.installSlot(agentVersion, InstallationRepresentation.from(APPLE_INSTALLATION), uriInfo);

        // find the new slot
        Slot slot = agent.getAllSlots().iterator().next();

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getMetadata().getFirst(HttpHeaders.LOCATION), URI.create("http://localhost/v1/agent/slot/" + slot.getId().toString()));

        SlotStatus status = slot.status();
        SlotStatus expectedStatus = status.changeAssignment(STOPPED, APPLE_ASSIGNMENT, status.getResources());
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus));
        assertEquals(response.getMetadata().get(AIRSHIP_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
        assertEquals(response.getMetadata().get(AIRSHIP_SLOT_VERSION_HEADER).get(0), expectedStatus.getVersion());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testInstallInvalidVersion()
    {
        try {
            resource.installSlot("invalid-version", InstallationRepresentation.from(APPLE_INSTALLATION), uriInfo);
            fail("Expected WebApplicationException");
        }
        catch (VersionConflictException e) {
            assertEquals(e.getName(), AIRSHIP_AGENT_VERSION_HEADER);
            assertEquals(e.getVersion(), agent.getAgentStatus().getVersion());
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testInstallNullDeployment()
    {
        resource.installSlot(null, null, uriInfo);
    }

    @Test
    public void testTerminateSlot()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);

        Response response = resource.terminateSlot(null, null, slotStatus.getId());
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        SlotStatus expectedStatus = slotStatus.changeState(TERMINATED);
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus));
        assertEquals(response.getMetadata().get(AIRSHIP_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
        assertEquals(response.getMetadata().get(AIRSHIP_SLOT_VERSION_HEADER).get(0), expectedStatus.getVersion());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertNull(agent.getSlot(slotStatus.getId()));
    }

    @Test
    public void testTerminateUnknownSlot()
    {
        Response response = resource.terminateSlot(null, null, UUID.randomUUID());
        assertEquals(response.getStatus(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testRemoveSlotNullId()
    {
        resource.terminateSlot(null, null, null);
    }
}
