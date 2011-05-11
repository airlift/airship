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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.io.File;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.agent.InstallationHelper.APPLE_INSTALLATION;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.agent.InstallationHelper.BANANA_INSTALLATION;
import static com.proofpoint.galaxy.shared.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestAssignmentResource
{
    private AssignmentResource resource;
    private Agent agent;
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");
    private InstallationRepresentation installation = new InstallationRepresentation(AssignmentRepresentation.from(APPLE_ASSIGNMENT),
            "fetch://binary.tar.gz",
            ImmutableMap.of("readme.txt", "fetch://readme.txt")
    );

    @BeforeMethod
    public void setup()
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        agent = new Agent(
                new AgentConfig()
                        .setSlotsDir(new File(tempDir, "slots").getAbsolutePath())
                        .setDataDir(new File(tempDir, "data").getAbsolutePath()),
                new HttpServerInfo(new HttpServerConfig(), new NodeInfo("test")),
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager());
        resource = new AssignmentResource(agent);
    }

    @Test
    public void testAssignUnknown()
    {
        Response response = resource.assign("unknown", installation, uriInfo);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testAssign()
    {
        Slot slot = agent.addNewSlot();

        SlotStatus expectedStatus = new SlotStatus(slot.status(), STOPPED, APPLE_ASSIGNMENT);

        Response response = resource.assign(slot.getName(), InstallationRepresentation.from(APPLE_INSTALLATION), uriInfo);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Assert.assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(slot.status(), expectedStatus);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullId()
    {
        resource.assign(null, installation, uriInfo);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullAssignment()
    {
        Slot slot = agent.addNewSlot();
        resource.assign(slot.getName(), null, uriInfo);
    }

    @Test
    public void testReplaceAssignment()
    {
        Slot slot = agent.addNewSlot();
        slot.assign(APPLE_INSTALLATION);

        SlotStatus expectedStatus = new SlotStatus(slot.status(), STOPPED, BANANA_ASSIGNMENT);

        Response response = resource.assign(slot.getName(), InstallationRepresentation.from(BANANA_INSTALLATION), uriInfo);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(slot.status(), expectedStatus);
    }

    @Test
    public void testClear()
    {
        Slot slot = agent.addNewSlot();
        slot.assign(APPLE_INSTALLATION);
        SlotStatus expectedStatus = new SlotStatus(slot.getId(), slot.getName(), slot.getSelf());

        Response response = resource.clear(slot.getName(), uriInfo);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), SlotStatusRepresentation.from(expectedStatus));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(slot.status(), expectedStatus);
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
