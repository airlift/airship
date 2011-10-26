package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.GALAXY_AGENT_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.GALAXY_SLOT_VERSION_HEADER;

public class VersionsUtil
{
    private VersionsUtil()
    {
    }

    public static void checkAgentVersion(Agent agent, String agentVersion)
    {
        Preconditions.checkNotNull(agent, "agent is null");

        if (agentVersion == null) {
            return;
        }

        AgentStatus agentStatus = agent.getAgentStatus();
        if (!agentVersion.equals(agentStatus.getVersion())) {
            throw new WebApplicationException(Response.status(Status.CONFLICT)
                    .entity(AgentStatusRepresentation.from(agentStatus))
                    .header(GALAXY_AGENT_VERSION_HEADER, agentStatus.getVersion())
                    .build());
        }
    }

    public static void checkSlotVersion(String slotName, String slotVersion, Agent agent, String agentVersion)
    {
        Preconditions.checkNotNull(agent, "agent is null");
        Preconditions.checkNotNull(slotName, "slotName must not be null");

        Slot slot = agent.getSlot(slotName);
        if (slot == null) {
            throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("[" + slotName + "]").build());
        }

        AgentStatus agentStatus = agent.getAgentStatus();
        SlotStatus slotStatus = slot.getLastSlotStatus();

        if (agentVersion != null && !agentVersion.equals(agentStatus.getVersion()) ||
                slotVersion != null && !slotVersion.equals(slotStatus.getVersion())) {

            throw new WebApplicationException(Response.status(Status.CONFLICT)
                    .entity(SlotStatusRepresentation.from(slotStatus))
                    .header(GALAXY_AGENT_VERSION_HEADER, agentStatus.getVersion())
                    .header(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .build());
        }
    }
}
