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

import com.google.common.collect.ImmutableMultiset;
import com.proofpoint.galaxy.shared.ExtraAssertions;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.net.URI;
import java.util.Collection;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

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
                .setCoordinatorBaseURI(URI.create("fake://server"))
                .setSlotsDir(new File(tempDir, "slots").getAbsolutePath());
        HttpServerInfo httpServerInfo = new HttpServerInfo(new HttpServerConfig(), new NodeInfo("test"));
        agent = new Agent(agentConfig,
                httpServerInfo,
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager());
        AnnouncementService announcementService = new AnnouncementService(agentConfig, agent, httpServerInfo);
        resource = new SlotResource(agent, announcementService);
    }

    @Test
    public void testGetSlotStatus()
    {
        Slot slot = agent.addNewSlot();

        URI requestUri = URI.create("http://localhost/v1/agent/slot/" + slot.getName());
        Response response = resource.getSlotStatus(slot.getName(), MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Assert.assertEquals(response.getEntity(), SlotStatusRepresentation.from(slot.status()));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetSlotStatusUnknown()
    {
        Response response = resource.getSlotStatus("unknown", MockUriInfo.from("http://localhost/v1/agent/slot/unknown"));
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
    }

    @Test
    public void testGetAllSlotStatus()
    {
        Slot slot1 = agent.addNewSlot();
        Slot slot2 = agent.addNewSlot();

        Response response = resource.getAllSlotsStatus(uriInfo);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        ExtraAssertions.assertEqualsNoOrder((Collection<?>) response.getEntity(), ImmutableMultiset.of(
                SlotStatusRepresentation.from(slot1.status()),
                SlotStatusRepresentation.from(slot2.status())
        ));
    }

    @Test
    public void testAddSlot()
    {
        Response response = resource.addSlot(uriInfo);

        // find the new slot
        Slot slot = agent.getAllSlots().iterator().next();

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getMetadata().getFirst(HttpHeaders.LOCATION), URI.create("http://localhost/v1/agent/slot/" + slot.getName()));

        assertNull(response.getEntity());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testRemoveSlot()
    {
        Slot slot = agent.addNewSlot();

        Response response = resource.removeSlot(slot.getName());
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertNull(response.getEntity());

        assertNull(agent.getSlot(slot.getName()));
    }

    @Test
    public void testRemoveSlotMissing()
    {
        Response response = resource.removeSlot("unknown");
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testRemoveSlotNullId()
    {
        resource.removeSlot(null);
    }
}
