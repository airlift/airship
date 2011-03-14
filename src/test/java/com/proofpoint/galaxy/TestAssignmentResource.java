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

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static com.proofpoint.galaxy.RepositoryTestHelper.newAssignment;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestAssignmentResource
{
    private AssignmentResource resource;
    private AgentManager agentManager;
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");
    private AssignmentRepresentation assignment = new AssignmentRepresentation("fruit:apple:1.0", "fetch://binary.tar.gz", "@prod:apple:1.0", ImmutableMap.of("readme.txt", "fetch://readme.txt"));

    @BeforeMethod
    public void setup()
    {

        agentManager = new AgentManager(new AgentConfig().setSlotsDir(System.getProperty("java.io.tmpdir")), new MockDeploymentManagerFactory(), new MockLifecycleManager());
        resource = new AssignmentResource(agentManager);
    }

    @Test
    public void testAssignUnknown()
    {
        Response response = resource.assign("unknown", assignment, uriInfo);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testAssign()
    {
        SlotManager slotManager = agentManager.addNewSlot();

        Assignment expectedAssignment = newAssignment("fruit:apple:1.0", "@prod:apple:1.0");
        SlotStatus expectedStatus = new SlotStatus(slotManager.getName(), expectedAssignment.getBinary(), expectedAssignment.getConfig(), LifecycleState.STOPPED
        );

        Response response = resource.assign(slotManager.getName(), AssignmentRepresentation.from(expectedAssignment), uriInfo);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus, uriInfo));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(slotManager.status(), expectedStatus);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullId()
    {
        resource.assign(null, assignment, uriInfo);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullAssignment()
    {
        SlotManager slotManager = agentManager.addNewSlot();
        resource.assign(slotManager.getName(), null, uriInfo);
    }

    @Test
    public void testReplaceAssignment()
    {
        SlotManager slotManager = agentManager.addNewSlot();
        slotManager.assign(newAssignment("fruit:apple:1.0", "@prod:apple:1.0"));

        Assignment expectedAssignment = newAssignment("fruit:banana:1.0", "@prod:banana:1.0");
        SlotStatus expectedStatus = new SlotStatus(slotManager.getName(), expectedAssignment.getBinary(), expectedAssignment.getConfig(), LifecycleState.STOPPED
        );

        Response response = resource.assign(slotManager.getName(), AssignmentRepresentation.from(expectedAssignment), uriInfo);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus, uriInfo));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(slotManager.status(), expectedStatus);
    }

    @Test
    public void testClear()
    {
        SlotManager slotManager = agentManager.addNewSlot();
        slotManager.assign(newAssignment("fruit:apple:1.0", "@prod:apple:1.0"));
        SlotStatus expectedStatus = new SlotStatus(slotManager.getName());

        Response response = resource.clear(slotManager.getName(), uriInfo);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus, uriInfo));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(slotManager.status(), expectedStatus);
    }

    @Test
    public void testClearMissing()
    {
        Response response = resource.clear("unknown", uriInfo);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testClearNullId()
    {
        resource.clear(null, uriInfo);
    }
}
