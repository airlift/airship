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

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.UUID;

import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENT_VERSION_HEADER;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_SLOT_VERSION_HEADER;
import static io.airlift.airship.shared.VersionsUtil.checkSlotVersion;

@Path("/v1/agent/slot/{slotId}/lifecycle")
public class LifecycleResource
{
    private final Agent agent;

    @Inject
    public LifecycleResource(Agent agent)
    {
        Preconditions.checkNotNull(agent, "agent must not be null");

        this.agent = agent;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response setState(@HeaderParam(AIRSHIP_SLOT_VERSION_HEADER) String slotVersion,
            @PathParam("slotId") UUID slotId,
            String newState)
    {
        Preconditions.checkNotNull(slotId, "slotId must not be null");
        Preconditions.checkNotNull(newState, "newState must not be null");

        Slot slot = agent.getSlot(slotId);
        if (slot == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        checkSlotVersion(slot.status(), slotVersion);

        SlotStatus status;
        if ("running".equals(newState)) {
            status = slot.start();
        }
        else if ("restarting".equals(newState)) {
            status = slot.restart();
        }
        else if ("stopped".equals(newState)) {
            status = slot.stop();
        }
        else if ("killing".equals(newState)) {
            status = slot.kill();
        }
        else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok(SlotStatusRepresentation.from(status))
                .header(AIRSHIP_AGENT_VERSION_HEADER, agent.getAgentStatus().getVersion())
                .header(AIRSHIP_SLOT_VERSION_HEADER, status.getVersion())
                .build();
    }
}
