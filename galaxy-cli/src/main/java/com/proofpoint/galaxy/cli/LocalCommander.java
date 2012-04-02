package com.proofpoint.galaxy.cli;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.io.Files;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.ServiceInventory;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.json.JsonCodec;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static com.proofpoint.galaxy.cli.CommanderResponse.createCommanderResponse;
import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.fromAgentStatus;
import static com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation.fromCoordinatorStatus;
import static com.proofpoint.galaxy.shared.SlotStatus.uuidGetter;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.fromSlotStatus;
import static com.proofpoint.galaxy.shared.VersionsUtil.checkAgentsVersion;
import static com.proofpoint.galaxy.shared.VersionsUtil.createAgentsVersion;
import static com.proofpoint.galaxy.shared.VersionsUtil.createSlotsVersion;

public class LocalCommander implements Commander
{
    private static final JsonCodec<ServiceDescriptorsRepresentation> SERVICE_DESCRIPTORS_CODEC = JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class);

    private final String environment;
    private final File localDirectory;
    private final Coordinator coordinator;
    private final Repository repository;
    private final ServiceInventory serviceInventory;

    public LocalCommander(String environment, File localDirectory, Coordinator coordinator, Repository repository, ServiceInventory serviceInventory)
    {
        this.environment = environment;
        this.localDirectory = localDirectory;
        this.coordinator = coordinator;
        this.repository = repository;
        this.serviceInventory = serviceInventory;
    }

    @Override
    public CommanderResponse<List<SlotStatusRepresentation>> show(SlotFilter slotFilter)
    {
        List<SlotStatus> allSlotStatus = coordinator.getAllSlotStatus();
        List<UUID> uuids = transform(allSlotStatus, SlotStatus.uuidGetter());

        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(false, uuids);

        List<SlotStatus> slots = coordinator.getAllSlotsStatus(slotPredicate);

        // update just in case something changed
        updateServiceInventory();

        return createCommanderResponse(createSlotsVersion(slots), transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository)));
    }

    @Override
    public List<SlotStatusRepresentation> install(AgentFilter agentFilter, int count, Assignment assignment, String expectedAgentsVersion)
    {
        // select the target agents
        Predicate<AgentStatus> agentsPredicate = agentFilter.toAgentPredicate(transform(coordinator.getAllSlotStatus(), uuidGetter()), false, repository);
        List<AgentStatus> agents = coordinator.getAgents(agentsPredicate);

        // verify the expected status of agents
        checkAgentsVersion(expectedAgentsVersion, agents);

        // install the software
        List<SlotStatus> slots = coordinator.install(agentsPredicate, count, assignment);

        // update to latest state
        updateServiceInventory();

        // calculate unique prefix size with the new slots included
        return transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository));
    }

    @Override
    public List<SlotStatusRepresentation> upgrade(SlotFilter slotFilter, UpgradeVersions upgradeVersions, String expectedSlotsVersion)
    {
        // build predicate
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(true, uuids);

        // upgrade slots
        List<SlotStatus> slots = coordinator.upgrade(slotPredicate, upgradeVersions, expectedSlotsVersion);

        // update to latest state
        updateServiceInventory();

        // build results
        return transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository));
    }

    @Override
    public List<SlotStatusRepresentation> setState(SlotFilter slotFilter, SlotLifecycleState state, String expectedSlotsVersion)
    {
        // build predicate
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(true, uuids);

        // before changing state (like starting) update just in case something changed
        updateServiceInventory();

        // set slots state
        List<SlotStatus> slots = coordinator.setState(state, slotPredicate, expectedSlotsVersion);

        // update to latest state
        updateServiceInventory();

        // build results
        return transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository));
    }

    @Override
    public List<SlotStatusRepresentation> terminate(SlotFilter slotFilter, String expectedSlotsVersion)
    {
        // build predicate
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(true, uuids);

        // terminate slots
        List<SlotStatus> slots = coordinator.terminate(slotPredicate, expectedSlotsVersion);

        // update to latest state
        updateServiceInventory();

        // build results
        return transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository));
    }

    @Override
    public List<SlotStatusRepresentation> resetExpectedState(SlotFilter slotFilter, String expectedSlotsVersion)
    {
        // build predicate
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(true, uuids);

        // rest slots expected state
        List<SlotStatus> slots = coordinator.resetExpectedState(slotPredicate, expectedSlotsVersion);

        // update just in case something changed
        updateServiceInventory();

        // build results
        return transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository));
    }

    @Override
    public boolean ssh(SlotFilter slotFilter, String command)
    {
        // build predicate
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<SlotStatus> slotPredicate = slotFilter.toSlotPredicate(true, uuids);

        // find the matching slots
        List<SlotStatus> slots = newArrayList(coordinator.getAllSlotsStatus(slotPredicate));

        // update just in case something changed
        updateServiceInventory();

        if (slots.isEmpty()) {
            return false;
        }

        // execute the command against one of the slots
        Collections.shuffle(slots);
        Exec.execLocal(SlotStatusRepresentation.from(slots.get(0)), command);
        return true;
    }

    @Override
    public List<CoordinatorStatusRepresentation> showCoordinators(CoordinatorFilter coordinatorFilter)
    {
        Predicate<CoordinatorStatus> coordinatorPredicate = coordinatorFilter.toCoordinatorPredicate();
        List<CoordinatorStatus> coordinatorStatuses = coordinator.getCoordinators(coordinatorPredicate);

        // update just in case something changed
        updateServiceInventory();

        return transform(coordinatorStatuses, fromCoordinatorStatus(coordinator.getCoordinators()));
    }

    @Override
    public List<CoordinatorStatusRepresentation> provisionCoordinators(String coordinatorConfigSpec,
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
    public CommanderResponse<List<AgentStatusRepresentation>> showAgents(AgentFilter agentFilter)
    {
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<AgentStatus> agentPredicate = agentFilter.toAgentPredicate(uuids, true, repository);
        List<AgentStatus> agentStatuses = coordinator.getAgents(agentPredicate);

        // update just in case something changed
        updateServiceInventory();
        return createCommanderResponse(createAgentsVersion(agentStatuses), transform(agentStatuses, fromAgentStatus(coordinator.getAgents(), repository)));
    }

    @Override
    public List<AgentStatusRepresentation> provisionAgents(String agentConfig,
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
    public AgentStatusRepresentation terminateAgent(String agentId)
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
        ServiceDescriptorsRepresentation serviceDescriptors = new ServiceDescriptorsRepresentation(environment, inventory);

        File serviceInventoryFile = new File(localDirectory, "service-inventory.json");
        try {
            Files.write(SERVICE_DESCRIPTORS_CODEC.toJson(serviceDescriptors), serviceInventoryFile, Charsets.UTF_8);
        }
        catch (IOException e) {
            System.out.println("Unable to write " + serviceInventoryFile);
        }
    }
}
