package com.proofpoint.galaxy.shared;

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
    public static final String GALAXY_SLOTS_VERSION_HEADER = "x-galaxy-slots-version";
    public static final String GALAXY_SLOT_VERSION_HEADER = "x-galaxy-slot-version";
    public static final String GALAXY_AGENT_VERSION_HEADER = "x-galaxy-agent-version";

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
            throw new VersionConflictException(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion());
        }
    }

    public static void checkSlotsVersion(String expectedSlotsVersion, Iterable<SlotStatus> allSlotStatus)
    {
        String actualSlotsVersion = createSlotsVersion(allSlotStatus);
        if (expectedSlotsVersion != null && !expectedSlotsVersion.equals(actualSlotsVersion)) {
            throw new VersionConflictException(GALAXY_SLOTS_VERSION_HEADER, actualSlotsVersion);
        }
    }

    public static void checkAgentVersion(AgentStatus agentStatus, String expectedAgentStatus)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");

        if (expectedAgentStatus == null) {
            return;
        }

        if (!expectedAgentStatus.equals(agentStatus.getVersion())) {
            throw new VersionConflictException(GALAXY_AGENT_VERSION_HEADER, agentStatus.getVersion());
        }
    }

    public static String createSlotVersion(UUID id, SlotLifecycleState state, Assignment assignment)
    {
        String data = Joiner.on("||").useForNull("--NULL--").join(id, state, assignment);
        return DigestUtils.md5Hex(data);
    }

    public static String createSlotsVersion(Iterable<SlotStatus> slots)
    {
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
}
