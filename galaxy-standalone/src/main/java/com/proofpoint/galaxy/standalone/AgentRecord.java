package com.proofpoint.galaxy.standalone;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;

public class AgentRecord implements Record
{
    public static ImmutableList<Record> toAgentRecords(Iterable<AgentStatusRepresentation> agents)
    {
        return ImmutableList.copyOf(Iterables.transform(agents, new Function<AgentStatusRepresentation, Record>()
        {
            @Override
            public AgentRecord apply(AgentStatusRepresentation agent)
            {
                return new AgentRecord(agent);
            }
        }));
    }

    private final AgentStatusRepresentation agentStatus;

    public AgentRecord(AgentStatusRepresentation agentStatus)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");

        this.agentStatus = agentStatus;
    }

    public Object getObjectValue(Column column)
    {
        switch (column) {
            case shortId:
                return agentStatus.getAgentId();
            case uuid:
                return agentStatus.getAgentId();
            case host:
                return agentStatus.getHost();
            case ip:
                return agentStatus.getIp();
            case status:
                return agentStatus.getState();
            case location:
                return agentStatus.getLocation();
            case instanceType:
                return agentStatus.getInstanceType();
            default:
                return null;
        }
    }

    @Override
    public String getValue(Column column)
    {
        Object value = getObjectValue(column);
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    @Override
    public String getColorizedValue(Column column)
    {
        return getValue(column);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AgentRecord that = (AgentRecord) o;

        if (!agentStatus.getAgentId().equals(that.agentStatus.getAgentId())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return agentStatus.getAgentId().hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("AgentRecord");
        sb.append("{agentStatus=").append(agentStatus);
        sb.append('}');
        return sb.toString();
    }
}
