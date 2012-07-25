package io.airlift.airship.cli;

import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.UpgradeVersions;

import java.util.List;

public interface Commander
{
    CommanderResponse<List<SlotStatusRepresentation>> show(SlotFilter slotFilter);

    List<SlotStatusRepresentation> install(AgentFilter agentFilter, int count, Assignment assignment, String expectedVersion);

    List<SlotStatusRepresentation> upgrade(SlotFilter slotFilter, UpgradeVersions upgradeVersions, String expectedVersion);

    List<SlotStatusRepresentation> setState(SlotFilter slotFilter, SlotLifecycleState state, String expectedVersion);

    List<SlotStatusRepresentation> terminate(SlotFilter slotFilter, String expectedVersion);

    List<SlotStatusRepresentation> resetExpectedState(SlotFilter slotFilter, String expectedVersion);

    boolean ssh(SlotFilter slotFilter, String command);

    List<CoordinatorStatusRepresentation> showCoordinators(CoordinatorFilter coordinatorFilter);

    List<CoordinatorStatusRepresentation> provisionCoordinators(String coordinatorConfig,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup);

    boolean sshCoordinator(CoordinatorFilter coordinatorFilter, String command);

    CommanderResponse<List<AgentStatusRepresentation>> showAgents(AgentFilter agentFilter);

    List<AgentStatusRepresentation> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup);

    AgentStatusRepresentation terminateAgent(String agentId);

    boolean sshAgent(AgentFilter agentFilter, String command);
}
