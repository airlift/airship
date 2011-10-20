package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class VersionsUtil
{
    private VersionsUtil()
    {
    }

    public static void checkSlotVersion(String slotVersion, Agent agent, String slotName)
    {
        Preconditions.checkNotNull(agent, "agent is null");
        Preconditions.checkNotNull(slotName, "slotName must not be null");

        if (slotVersion == null) {
            return;
        }

        Slot slot = agent.getSlot(slotName);
        if (slot == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity("[" + slotName + "]").build());
        }
        SlotStatus slotStatus = slot.getLastSlotStatus();
        if (!slotVersion.equals(slotStatus.getVersion())) {
            throw new WebApplicationException(Response.status(Status.CONFLICT).entity(SlotStatusRepresentation.from(slotStatus)).build());
        }
    }
}
