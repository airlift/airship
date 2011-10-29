package com.proofpoint.galaxy.standalone;

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

    void ssh(SlotFilter slotFilter, List<String> args);

    List<Record> showAgents(AgentFilter agentFilter)
            throws Exception;

    List<Record> addAgents(int count, String instanceType, String availabilityZone)
                    throws Exception;

    Record terminateAgent(String agentId);
}
