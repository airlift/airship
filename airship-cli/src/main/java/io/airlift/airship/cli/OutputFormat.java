package io.airlift.airship.cli;

import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.SlotStatusRepresentation;

public interface OutputFormat
{
    void displaySlots(Iterable<SlotStatusRepresentation> slots);

    void displayAgents(Iterable<AgentStatusRepresentation> agents);

    void displayCoordinators(Iterable<CoordinatorStatusRepresentation> coordinators);
}
