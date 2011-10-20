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
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.io.File;

import static com.proofpoint.galaxy.shared.InstallationHelper.APPLE_INSTALLATION;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestAssignmentResource
{
    private AssignmentResource resource;
    private Agent agent;
    private final UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/agent/slot/assignment");
    private static final Assignment APPLE_V2 = new Assignment("food.fruit:apple:2.0", "@apple:2.0");
    private static final InstallationRepresentation UPGRADE = new InstallationRepresentation(AssignmentRepresentation.from(APPLE_V2),
            "fetch://binary.tar.gz",
            ImmutableMap.of("readme.txt", "fetch://readme.txt")
    );

    @BeforeMethod
    public void setup()
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        agent = new Agent(
                new AgentConfig().setSlotsDir(new File(tempDir, "slots").getAbsolutePath()),
                new HttpServerInfo(new HttpServerConfig(), new NodeInfo("test")),
                new NodeInfo("test"),
                new MockDeploymentManagerFactory(),
                new MockLifecycleManager()
        );
        resource = new AssignmentResource(agent);
    }

    @Test
    public void testAssignUnknown()
    {
        Response response = resource.assign(null, "unknown", UPGRADE, uriInfo);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullId()
    {
        resource.assign(null, null, UPGRADE, uriInfo);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullAssignment()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        resource.assign(null, slotStatus.getName(), null, uriInfo);
    }

    @Test
    public void testAssignInvalidVersion()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        try {
            resource.assign("bad-version", slotStatus.getName(), UPGRADE, uriInfo);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), Status.CONFLICT.getStatusCode());
            SlotStatusRepresentation actualStatus = (SlotStatusRepresentation) e.getResponse().getEntity();
            assertEquals(actualStatus, SlotStatusRepresentation.from(slotStatus));
        }
    }

    @Test
    public void testAssignGoodVersion()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        Response response = resource.assign(slotStatus.getVersion(), slotStatus.getName(), UPGRADE, uriInfo);
        assertEquals(response.getStatus(), Status.OK.getStatusCode());
        SlotStatusRepresentation actualStatus = (SlotStatusRepresentation) response.getEntity();
        SlotStatus expectedStatus = new SlotStatus(slotStatus, STOPPED, APPLE_V2);
        assertEquals(actualStatus, SlotStatusRepresentation.from(expectedStatus));
    }

    @Test
    public void testReplaceAssignment()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);

        Response response = resource.assign(null, slotStatus.getName(), UPGRADE, uriInfo);

        SlotStatusRepresentation actualEntity = (SlotStatusRepresentation) response.getEntity();

        SlotStatus expectedStatus = new SlotStatus(slotStatus, STOPPED, APPLE_V2);

        Assert.assertEquals(actualEntity, SlotStatusRepresentation.from(expectedStatus));

        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(actualEntity.toSlotStatus(), expectedStatus);
    }
}
