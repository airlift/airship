package com.proofpoint.galaxy.cli;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.ServiceInventory;
import com.proofpoint.galaxy.coordinator.Strings;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusWithExpectedState;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.json.JsonCodec;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Collections2.transform;
import static com.proofpoint.galaxy.cli.CoordinatorRecord.toCoordinatorRecords;
import static com.proofpoint.galaxy.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static com.proofpoint.galaxy.coordinator.StringFunctions.toStringFunction;
import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.toAgentStatusRepresentations;
import static com.proofpoint.galaxy.cli.AgentRecord.toAgentRecords;
import static com.proofpoint.galaxy.cli.SlotRecord.toSlotRecords;
import static com.proofpoint.galaxy.cli.SlotRecord.toSlotRecordsWithExpectedState;
import static com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation.toCoordinatorStatusRepresentations;
import static java.lang.Math.max;

public class LocalCommander implements Commander
{
    private static final JsonCodec<ServiceDescriptorsRepresentation> SERVICE_DESCRIPTORS_CODEC = JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class);

    private final File localDirectory;
    private final Coordinator coordinator;
    private final ServiceInventory serviceInventory;

    public LocalCommander(File localDirectory, Coordinator coordinator, ServiceInventory serviceInventory)
    {
        this.localDirectory = localDirectory;
        this.coordinator = coordinator;
        this.serviceInventory = serviceInventory;
    }

    @Override
    public List<Record> show(SlotFilter slotFilter)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        Iterable<SlotStatusWithExpectedState> slots = coordinator.getAllSlotStatusWithExpectedState(slotPredicate);

        // update just in case something changed
        updateServiceInventory();

        return toSlotRecordsWithExpectedState(prefixSize, slots);
    }

    @Override
    public List<Record> install(AgentFilter agentFilter, int count, Assignment assignment)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<AgentStatus> agentPredicate = agentFilter.toAgentPredicate(uuids);
        List<SlotStatus> slots = coordinator.install(agentPredicate, count, assignment);

        // update to latest state
        updateServiceInventory();

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

        // update to latest state
        updateServiceInventory();

        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public List<Record> setState(SlotFilter slotFilter, SlotLifecycleState state)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        // before changing state (like starting) update just in case something changed
        updateServiceInventory();

        Iterable<SlotStatus> slots = coordinator.setState(state, slotPredicate);

        // update to latest state
        updateServiceInventory();

        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public List<Record> terminate(SlotFilter slotFilter)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        Iterable<SlotStatus> slots = coordinator.terminate(slotPredicate);

        // update to latest state
        updateServiceInventory();

        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public List<Record> resetExpectedState(SlotFilter slotFilter)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        final int prefixSize = getPrefixSize(uuids);

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        Iterable<SlotStatus> slots = coordinator.resetExpectedState(slotPredicate);

        // update just in case something changed
        updateServiceInventory();

        return toSlotRecords(prefixSize, slots);
    }

    @Override
    public boolean ssh(SlotFilter slotFilter, String command)
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        List<SlotStatusWithExpectedState> slots = coordinator.getAllSlotStatusWithExpectedState(slotPredicate);

        // update just in case something changed
        updateServiceInventory();

        if (slots.isEmpty()) {
            return false;
        }
        Exec.execLocal(SlotStatusRepresentation.from(slots.get(0)), command);
        return true;
    }

    @Override
    public List<Record> showCoordinators(CoordinatorFilter coordinatorFilter)
    {
        Predicate<CoordinatorStatus> coordinatorPredicate = coordinatorFilter.toCoordinatorPredicate();
        List<CoordinatorStatus> coordinatorStatuses = coordinator.getCoordinators(coordinatorPredicate);

        // update just in case something changed
        updateServiceInventory();

        return toCoordinatorRecords(toCoordinatorStatusRepresentations(coordinatorStatuses));
    }

    @Override
    public List<Record> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup)
    {
        throw new UnsupportedOperationException("Coordinators can not be provisioned in local mode");
    }

    @Override
    public boolean sshCoordinator(CoordinatorFilter coordinatorFilter, String command)
    {
        throw new UnsupportedOperationException("Coordinator ssh no supported in local mode");
    }

    @Override
    public List<Record> showAgents(AgentFilter agentFilter)
            throws Exception
    {
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<AgentStatus> agentPredicate = agentFilter.toAgentPredicate(uuids);
        List<AgentStatus> agentStatuses = coordinator.getAgents(agentPredicate);

        // update just in case something changed
        updateServiceInventory();

        return toAgentRecords(toAgentStatusRepresentations(agentStatuses));
    }

    @Override
    public List<Record> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup)
    {
        throw new UnsupportedOperationException("Agents can not be provisioned in local mode");
    }

    @Override
    public Record terminateAgent(String agentId)
    {
        throw new UnsupportedOperationException("Agents can not be terminated in local mode");
    }

    @Override
    public boolean sshAgent(AgentFilter agentFilter, String command)
    {
        throw new UnsupportedOperationException("Agent ssh no supported in local mode");
    }

    private void updateServiceInventory()
    {
        List<ServiceDescriptor> inventory = serviceInventory.getServiceInventory(coordinator.getAllSlotStatus());
        ServiceDescriptorsRepresentation serviceDescriptors = new ServiceDescriptorsRepresentation(coordinator.getEnvironment(), inventory);

        File serviceInventoryFile = new File(localDirectory, "service-inventory.json");
        try {
            Files.write(SERVICE_DESCRIPTORS_CODEC.toJson(serviceDescriptors), serviceInventoryFile, Charsets.UTF_8);
        }
        catch (IOException e) {
            System.out.println("Unable to write " + serviceInventoryFile);
        }
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
