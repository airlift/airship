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
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.VersionConflictException;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;

import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_AGENT_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.InstallationHelper.APPLE_INSTALLATION;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOT_VERSION_HEADER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestAssignmentResource
{
    private AssignmentResource resource;
    private Agent agent;
    private static final Assignment APPLE_V2 = new Assignment("food.fruit:apple:2.0", "@apple:2.0");
    private static final InstallationRepresentation UPGRADE = new InstallationRepresentation("apple",
            AssignmentRepresentation.from(APPLE_V2),
            "fetch://binary.tar.gz",
            "fetch://config.config",
            ImmutableMap.of("memory", 512)
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
        Response response = resource.assign(null, null, "unknown", UPGRADE);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullId()
    {
        resource.assign(null, null, null, UPGRADE);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAssignNullAssignment()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        resource.assign(null, null, slotStatus.getName(), null);
    }

    @Test
    public void testAssignInvalidVersion()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        try {
            resource.assign("bad-version", "bad-version", slotStatus.getName(), UPGRADE);
            fail("Expected VersionConflictException");
        }
        catch (VersionConflictException e) {
        }
        try {
            resource.assign("bad-version", null, slotStatus.getName(), UPGRADE);
            fail("Expected VersionConflictException");
        }
        catch (VersionConflictException e) {
            assertEquals(e.getName(), GALAXY_AGENT_VERSION_HEADER);
            assertEquals(e.getVersion(), agent.getAgentStatus().getVersion());
        }
        try {
            resource.assign(null, "bad-version", slotStatus.getName(), UPGRADE);
            fail("Expected VersionConflictException");
        }
        catch (VersionConflictException e) {
            assertEquals(e.getName(), GALAXY_SLOT_VERSION_HEADER);
            assertEquals(e.getVersion(), slotStatus.getVersion());
        }
    }

    @Test
    public void testAssign()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        assertUpgrade(slotStatus, agent.getAgentStatus().getVersion(), slotStatus.getVersion());
    }

    @Test
    public void testAssignNoAgentVersion()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        assertUpgrade(slotStatus, null, slotStatus.getVersion());
    }

    @Test
    public void testAssignNoSlotVersion()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        assertUpgrade(slotStatus, agent.getAgentStatus().getVersion(), null);
    }

    @Test
    public void testAssignNoVersions()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        assertUpgrade(slotStatus, null, null);
    }

    private void assertUpgrade(SlotStatus slotStatus, String agentVersion, String slotVersion)
    {
        Response response = resource.assign(agentVersion, slotVersion, slotStatus.getName(), UPGRADE);
        assertEquals(response.getStatus(), Status.OK.getStatusCode());

        SlotStatusRepresentation actualStatus = (SlotStatusRepresentation) response.getEntity();
        SlotStatus expectedStatus = new SlotStatus(slotStatus, STOPPED, APPLE_V2);
        assertEquals(actualStatus, SlotStatusRepresentation.from(expectedStatus));
        assertEquals(actualStatus.toSlotStatus(), expectedStatus);
        assertEquals(response.getMetadata().get(GALAXY_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
        assertEquals(response.getMetadata().get(GALAXY_SLOT_VERSION_HEADER).get(0), expectedStatus.getVersion());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
