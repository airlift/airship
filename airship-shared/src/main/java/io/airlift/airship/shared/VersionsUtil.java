package io.airlift.airship.shared;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class VersionsUtil
{
    public static final String AIRSHIP_SLOTS_VERSION_HEADER = "x-airship-slots-version";
    public static final String AIRSHIP_SLOT_VERSION_HEADER = "x-airship-slot-version";

    public static final String AIRSHIP_AGENTS_VERSION_HEADER = "x-airship-agents-version";
    public static final String AIRSHIP_AGENT_VERSION_HEADER = "x-airship-agent-version";

    public static final String AIRSHIP_COORDINATOR_VERSION_HEADER = "x-airship-coordinator-version";

    public static final String AIRSHIP_FORCE_HEADER = "x-airship-force";

    private VersionsUtil()
    {
    }

    public static void checkSlotVersion(SlotStatus slotStatus, String expectedSlotVersion)
    {
        Preconditions.checkNotNull(slotStatus, "slotStatus is null");

        if (expectedSlotVersion == null) {
            return;
        }

        if (!expectedSlotVersion.equals(slotStatus.getVersion())) {
            throw new VersionConflictException(AIRSHIP_SLOT_VERSION_HEADER, slotStatus.getVersion());
        }
    }

    public static void checkSlotsVersion(String expectedSlotsVersion, Iterable<SlotStatus> slotStatuses)
    {
        Preconditions.checkNotNull(slotStatuses, "slotStatuses is null");

        if (expectedSlotsVersion == null) {
            return;
        }

        String actualSlotsVersion = createSlotsVersion(slotStatuses);
        if (!expectedSlotsVersion.equals(actualSlotsVersion)) {
            throw new VersionConflictException(AIRSHIP_SLOTS_VERSION_HEADER, actualSlotsVersion);
        }
    }

    public static void checkAgentVersion(AgentStatus agentStatus, String expectedAgentStatus)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");

        if (expectedAgentStatus == null) {
            return;
        }

        if (!expectedAgentStatus.equals(agentStatus.getVersion())) {
            throw new VersionConflictException(AIRSHIP_AGENT_VERSION_HEADER, agentStatus.getVersion());
        }
    }

    public static void checkAgentsVersion(String expectedAgentsVersion, Iterable<AgentStatus> agentStatuses)
    {
        Preconditions.checkNotNull(agentStatuses, "agentStatuses is null");

        if (expectedAgentsVersion == null) {
            return;
        }

        String actualAgentsVersion = createAgentsVersion(agentStatuses);
        if (!expectedAgentsVersion.equals(actualAgentsVersion)) {
            throw new VersionConflictException(AIRSHIP_AGENTS_VERSION_HEADER, actualAgentsVersion);
        }
    }

    public static String createSlotVersion(UUID id, SlotLifecycleState state, Assignment assignment)
    {
        String data = Joiner.on("||").useForNull("--NULL--").join(id, state, assignment);
        return DigestUtils.md5Hex(data);
    }

    public static String createSlotsVersion(Iterable<SlotStatus> slots)
    {
        Preconditions.checkNotNull(slots, "slots is null");

        // canonicalize slot order
        Map<UUID, String> slotVersions = new TreeMap<UUID, String>();
        for (SlotStatus slot : slots) {
            slotVersions.put(slot.getId(), slot.getVersion());
        }
        return DigestUtils.md5Hex(slotVersions.values().toString());
    }

    public static String createAgentVersion(String agentId, AgentLifecycleState state, Iterable<SlotStatus> slots, Map<String, Integer> resources)
    {
        List<Object> parts = new ArrayList<Object>();
        parts.add(agentId);
        parts.add(state);

        // canonicalize slot order
        Map<UUID, String> slotVersions = new TreeMap<UUID, String>();
        for (SlotStatus slot : slots) {
            slotVersions.put(slot.getId(), slot.getVersion());
        }
        parts.addAll(slotVersions.values());

        // canonicalize resources
        parts.add(Joiner.on("--").withKeyValueSeparator("=").join(ImmutableSortedMap.copyOf(resources)));

        String data = Joiner.on("||").useForNull("--NULL--").join(parts);
        return DigestUtils.md5Hex(data);
    }
    
    public static String createAgentsVersion(Iterable<AgentStatus> agents)
    {
        Preconditions.checkNotNull(agents, "agents is null");

        // canonicalize agent order
        Map<String, String> agentVersions = new TreeMap<String, String>();
        for (AgentStatus agent : agents) {
            agentVersions.put(agent.getAgentId() + agent.getInstanceId(), agent.getVersion());
        }
        return DigestUtils.md5Hex(agentVersions.values().toString());
    }

    public static String createVersion(String coordinatorId, CoordinatorLifecycleState state)
    {
        List<Object> parts = new ArrayList<Object>();
        parts.add(coordinatorId);
        parts.add(state);

        String data = Joiner.on("||").useForNull("--NULL--").join(parts);
        return DigestUtils.md5Hex(data);
    }
}
