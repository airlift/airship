package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.UUID;

public class ExpectedSlotStatus
{
    private final UUID id;
    private final SlotLifecycleState status;
    private final Assignment assignment;

    public ExpectedSlotStatus(UUID id, SlotLifecycleState status, Assignment assignment)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(status, "status is null");
        this.id = id;
        this.assignment = assignment;
        this.status = status;
    }

    @JsonCreator
    public ExpectedSlotStatus(
            @JsonProperty("id") UUID id,
            @JsonProperty("status") SlotLifecycleState status,
            @JsonProperty("binary") BinarySpec binarySpec,
            @JsonProperty("config") ConfigSpec configSpec)
    {
        this.id = id;
        this.status = status;
        this.assignment = new Assignment(binarySpec, configSpec);
    }

    @JsonProperty
    public UUID getId()
    {
        return id;
    }

    @JsonProperty
    public SlotLifecycleState getStatus()
    {
        return status;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    @JsonProperty
    public String getBinary()
    {
        if (assignment == null) {
            return null;
        }
        return assignment.getBinary().toGAV();
    }

    @JsonProperty
    public String getConfig()
    {
        if (assignment == null) {
            return null;
        }
        return assignment.getConfig().toGAV();
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

        ExpectedSlotStatus that = (ExpectedSlotStatus) o;

        if (!id.equals(that.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("ExpectedSlotStatus");
        sb.append("{id=").append(id);
        sb.append(", status=").append(status);
        sb.append(", assignment=").append(assignment);
        sb.append('}');
        return sb.toString();
    }

    public static Function<ExpectedSlotStatus, UUID> uuidGetter()
    {
        return new Function<ExpectedSlotStatus, UUID>()
        {
            public UUID apply(ExpectedSlotStatus input)
            {
                return input.getId();
            }
        };
    }
}
