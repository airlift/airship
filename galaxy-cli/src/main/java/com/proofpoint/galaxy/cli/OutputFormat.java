package com.proofpoint.galaxy.cli;

import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

public interface OutputFormat
{
    void displaySlots(Iterable<SlotStatusRepresentation> slots);

    void displayAgents(Iterable<AgentStatusRepresentation> agents);

    void displayCoordinators(Iterable<CoordinatorStatusRepresentation> coordinators);
}
