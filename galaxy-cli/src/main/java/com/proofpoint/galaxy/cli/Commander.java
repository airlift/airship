package com.proofpoint.galaxy.cli;

import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.UpgradeVersions;

import java.util.List;

public interface Commander
{
    CommanderResponse<List<Record>> show(SlotFilter slotFilter);

    List<Record> install(AgentFilter agentFilter, int count, Assignment assignment, String expectedVersion);

    List<Record> upgrade(SlotFilter slotFilter, UpgradeVersions upgradeVersions, String expectedVersion);

    List<Record> setState(SlotFilter slotFilter, SlotLifecycleState state, String expectedVersion);

    List<Record> terminate(SlotFilter slotFilter, String expectedVersion);

    List<Record> resetExpectedState(SlotFilter slotFilter, String expectedVersion);

    boolean ssh(SlotFilter slotFilter, String command);

    List<Record> showCoordinators(CoordinatorFilter coordinatorFilter);

    List<Record> provisionCoordinators(String coordinatorConfig,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup);

    boolean sshCoordinator(CoordinatorFilter coordinatorFilter, String command);

    CommanderResponse<List<Record>> showAgents(AgentFilter agentFilter);

    List<Record> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup);

    Record terminateAgent(String agentId);

    boolean sshAgent(AgentFilter agentFilter, String command);
}
