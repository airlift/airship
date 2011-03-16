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
package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestAnnounceResource
{
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/agent");
    private AnnounceResource resource;
    private Console store;
    private AgentStatus fooAgent;
    private AgentStatus barAgent;

    @BeforeMethod
    public void setup()
    {
        store = new Console();
        resource = new AnnounceResource(store);
        fooAgent = new AgentStatus(UUID.randomUUID(), ImmutableList.of(new SlotStatus(UUID.randomUUID(), "foo")));
        barAgent = new AgentStatus(UUID.randomUUID(), ImmutableList.of(new SlotStatus(UUID.randomUUID(), "bar")));
    }

    @Test
    public void testGetAgentStatus()
    {
        store.updateAgentStatus(fooAgent);

        URI requestUri = URI.create("http://localhost/v1/agent/" + fooAgent.getAgentId());
        Response response = resource.getAgentStatus(fooAgent.getAgentId(), MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), AgentStatusRepresentation.from(fooAgent, uriInfo.getBaseUri()));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAgentStatusUnknown()
    {
        Response response = resource.getAgentStatus(UUID.randomUUID(), MockUriInfo.from("http://localhost/v1/agent/unknown"));
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testGetAgentStatusNull()
    {
        resource.getAgentStatus(null, MockUriInfo.from(URI.create("http://localhost/v1/agent/null")));
    }

    @Test
    public void testGetAllAgentStatusEmpty()
    {
        Response response = resource.getAllAgentStatus(uriInfo);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        assertEquals((Collection<?>) response.getEntity(), newArrayList());
    }

    @Test
    public void testGetAllAgentStatus()
    {
        store.updateAgentStatus(fooAgent);
        store.updateAgentStatus(barAgent);

        Response response = resource.getAllAgentStatus(uriInfo);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        ExtraAssertions.assertEqualsNoOrder((Collection<?>) response.getEntity(), ImmutableMultiset.of(
                AgentStatusRepresentation.from(fooAgent, uriInfo.getBaseUri()),
                AgentStatusRepresentation.from(barAgent, uriInfo.getBaseUri())
        ));
    }

    @Test
    public void testUpdateAgentStatus()
    {
        store.updateAgentStatus(fooAgent);
        AgentStatus newFooAgent = new AgentStatus(UUID.randomUUID(), ImmutableList.of(new SlotStatus(UUID.randomUUID(), "foo"), new SlotStatus(UUID.randomUUID(), "moo")));

        Response response = resource.updateAgentStatus(newFooAgent.getAgentId(), AgentStatusRepresentation.from(newFooAgent, uriInfo.getBaseUri()), uriInfo);

        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertNull(response.getEntity());
        assertEquals(store.getAgentStatus(fooAgent.getAgentId()), fooAgent);
    }

    @Test
    public void testRemoveAgentStatus()
    {
        store.updateAgentStatus(fooAgent);

        Response response = resource.removeAgentStatus(fooAgent.getAgentId());
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertNull(response.getEntity());

        assertNull(store.getAgentStatus(fooAgent.getAgentId()));
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
