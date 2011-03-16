package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapMaker;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class Console
{
    private final ConcurrentMap<UUID, AgentStatus> agents;

    public Console()
    {
        agents = new MapMaker().expiration(30, TimeUnit.SECONDS).makeMap();
    }

    public List<AgentStatus> getAllAgentStatus()
    {
        return ImmutableList.copyOf(agents.values());
    }

    public AgentStatus getAgentStatus(UUID agentId)
    {
        return agents.get(agentId);
    }

    public void updateAgentStatus(AgentStatus status)
    {
        agents.put(status.getAgentId(), status);
    }

    public boolean removeAgentStatus(UUID agentId)
    {
        return agents.remove(agentId) != null;
    }
}
