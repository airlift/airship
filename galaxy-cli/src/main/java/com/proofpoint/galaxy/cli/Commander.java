package com.proofpoint.galaxy.cli;

import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.UpgradeVersions;

import java.util.List;

public interface Commander
{
    List<Record> show(SlotFilter slotFilter);

    List<Record> install(AgentFilter agentFilter, int count, Assignment assignment);

    List<Record> upgrade(SlotFilter slotFilter, UpgradeVersions upgradeVersions);

    List<Record> setState(SlotFilter slotFilter, SlotLifecycleState state);

    List<Record> terminate(SlotFilter slotFilter);

    List<Record> resetExpectedState(SlotFilter slotFilter);

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

    List<Record> showAgents(AgentFilter agentFilter)
            throws Exception;

    List<Record> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup);

    Record terminateAgent(String agentId);
}
