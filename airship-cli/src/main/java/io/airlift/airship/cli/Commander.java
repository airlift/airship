package com.proofpoint.galaxy.cli;

import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.UpgradeVersions;

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
