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
import com.proofpoint.galaxy.AgentStatus;
import com.proofpoint.galaxy.AgentStatusRepresentation;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestAnnounceResource
{
    private AnnounceResource resource;
    private Coordinator coordinator;
    private AgentStatus agentStatus;

    @BeforeMethod
    public void setup()
    {
        coordinator = new Coordinator(new MockRemoteSlotFactory(), new CoordinatorConfig().setStatusExpiration(new Duration(100, TimeUnit.DAYS)));
        resource = new AnnounceResource(coordinator);
        agentStatus = new AgentStatus(UUID.randomUUID(), ImmutableList.of(new SlotStatus(UUID.randomUUID(), "foo", URI.create("fake://foo"))));
    }

    @Test
    public void testUpdateAgentStatus()
    {
        coordinator.updateAgentStatus(agentStatus);
        AgentStatus newFooAgent = new AgentStatus(UUID.randomUUID(), ImmutableList.of(new SlotStatus(UUID.randomUUID(), "foo", URI.create("fake://foo")), new SlotStatus(UUID.randomUUID(), "moo", URI.create("fake://moo"))));

        Response response = resource.updateAgentStatus(newFooAgent.getAgentId(), AgentStatusRepresentation.from(newFooAgent, URI.create("http://localhost/v1/agent")));

        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertNull(response.getEntity());
        assertEquals(coordinator.getAgentStatus(agentStatus.getAgentId()), agentStatus);
    }

    @Test
    public void testRemoveAgentStatus()
    {
        coordinator.updateAgentStatus(agentStatus);

        Response response = resource.removeAgentStatus(agentStatus.getAgentId());
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertNull(response.getEntity());

        assertNull(coordinator.getAgentStatus(agentStatus.getAgentId()));
    }

    @Test
    public void testRemoveAgentStatusMissing()
    {
        Response response = resource.removeAgentStatus(UUID.randomUUID());
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testRemoveAgentStatusNullId()
    {
        resource.removeAgentStatus(null);
    }
}
