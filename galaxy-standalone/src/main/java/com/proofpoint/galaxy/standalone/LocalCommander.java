package com.proofpoint.galaxy.standalone;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.Strings;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusWithExpectedState;
import com.proofpoint.galaxy.shared.UpgradeVersions;

import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Collections2.transform;
import static com.proofpoint.galaxy.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static com.proofpoint.galaxy.coordinator.StringFunctions.toStringFunction;
import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.toAgentStatusRepresentations;
import static com.proofpoint.galaxy.standalone.AgentRecord.toAgentRecords;
import static com.proofpoint.galaxy.standalone.SlotRecord.toSlotRecords;
import static com.proofpoint.galaxy.standalone.SlotRecord.toSlotRecordsWithExpectedState;
import static java.lang.Math.max;

public class LocalCommander implements Commander
{
    private final Coordinator coordinator;

    public LocalCommander(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    @Override
    public List<Record> show(SlotFilter slotFilter)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        Iterable<SlotStatusWithExpectedState> slots = coordinator.getAllSlotStatusWithExpectedState(slotPredicate);
        return toSlotRecordsWithExpectedState(prefixSize, slots);
    }

    @Override
    public List<Record> install(AgentFilter agentFilter, int count, Assignment assignment)
    {
        Predicate<AgentStatus> agentPredicate = agentFilter.toAgentPredicate();
        List<SlotStatus> slots = coordinator.install(agentPredicate, count, assignment);

        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        int prefixSize = getPrefixSize(uuids);

        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public List<Record> upgrade(SlotFilter slotFilter, UpgradeVersions upgradeVersions)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(true, uuids);
        List<SlotStatus> slots = coordinator.upgrade(slotPredicate, upgradeVersions);

        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public List<Record> setState(SlotFilter slotFilter, SlotLifecycleState state)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        Iterable<SlotStatus> slots = coordinator.setState(state, slotPredicate);
        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public List<Record> terminate(SlotFilter slotFilter)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        Iterable<SlotStatus> slots = coordinator.terminate(slotPredicate);
        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public List<Record> resetExpectedState(SlotFilter slotFilter)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        Iterable<SlotStatus> slots = coordinator.resetExpectedState(slotPredicate);
        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public boolean ssh(SlotFilter slotFilter, String command)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        List<SlotStatusWithExpectedState> slots = coordinator.getAllSlotStatusWithExpectedState(slotPredicate);
        if (slots.isEmpty()) {
            return false;
        }
        Ssh.execSsh(SlotStatusRepresentation.from(slots.get(0)), command);
        return true;
    }

    @Override
    public List<Record> showAgents(AgentFilter agentFilter)
            throws Exception
    {
        Predicate<AgentStatus> agentPredicate = agentFilter.toAgentPredicate();
        List<AgentStatus> agentStatuses = coordinator.getAgents(agentPredicate);
        return toAgentRecords(toAgentStatusRepresentations(agentStatuses));
    }

    @Override
    public List<Record> addAgents(int count, String instanceType, String availabilityZone)
            throws Exception
    {
        List<AgentStatus> agentStatuses = coordinator.addAgents(count, instanceType, availabilityZone);
        return toAgentRecords(toAgentStatusRepresentations(agentStatuses));
    }

    @Override
    public Record terminateAgent(String agentId)
    {
        AgentStatus agentStatus = coordinator.terminateAgent(agentId);
        return new AgentRecord(AgentStatusRepresentation.from(agentStatus));
    }

    public static int getPrefixSize(List<UUID> uuids)
    {
        final int prefixSize;
        if (!uuids.isEmpty()) {
            prefixSize = max(MIN_PREFIX_SIZE, Strings.shortestUniquePrefix(transform(uuids, toStringFunction())));
        }
        else {
            prefixSize = MIN_PREFIX_SIZE;
        }
        return prefixSize;
    }
}
