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

import com.google.common.collect.ImmutableMap;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.AssignmentRepresentation;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.VersionConflictException;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.util.UUID;

import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENT_VERSION_HEADER;
import static io.airlift.airship.shared.InstallationHelper.APPLE_INSTALLATION;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_SLOT_VERSION_HEADER;
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
        Response response = resource.assign(null, null, UUID.randomUUID(), UPGRADE);
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
        resource.assign(null, null, slotStatus.getId(), null);
    }

    @Test
    public void testAssignInvalidVersion()
    {
        SlotStatus slotStatus = agent.install(APPLE_INSTALLATION);
        try {
            resource.assign("bad-version", "bad-version", slotStatus.getId(), UPGRADE);
            fail("Expected VersionConflictException");
        }
        catch (VersionConflictException e) {
        }
        try {
            resource.assign("bad-version", null, slotStatus.getId(), UPGRADE);
            fail("Expected VersionConflictException");
        }
        catch (VersionConflictException e) {
            assertEquals(e.getName(), AIRSHIP_AGENT_VERSION_HEADER);
            assertEquals(e.getVersion(), agent.getAgentStatus().getVersion());
        }
        try {
            resource.assign(null, "bad-version", slotStatus.getId(), UPGRADE);
            fail("Expected VersionConflictException");
        }
        catch (VersionConflictException e) {
            assertEquals(e.getName(), AIRSHIP_SLOT_VERSION_HEADER);
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
        Response response = resource.assign(agentVersion, slotVersion, slotStatus.getId(), UPGRADE);
        assertEquals(response.getStatus(), Status.OK.getStatusCode());

        SlotStatusRepresentation actualStatus = (SlotStatusRepresentation) response.getEntity();
        SlotStatus expectedStatus = slotStatus.changeAssignment(STOPPED, APPLE_V2, slotStatus.getResources());
        assertEquals(actualStatus, SlotStatusRepresentation.from(expectedStatus));
        assertEquals(actualStatus.toSlotStatus(null), expectedStatus);
        assertEquals(response.getMetadata().get(AIRSHIP_AGENT_VERSION_HEADER).get(0), agent.getAgentStatus().getVersion());
        assertEquals(response.getMetadata().get(AIRSHIP_SLOT_VERSION_HEADER).get(0), expectedStatus.getVersion());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
