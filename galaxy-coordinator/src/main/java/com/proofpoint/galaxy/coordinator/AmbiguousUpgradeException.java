package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.galaxy.shared.Assignment;

import java.util.Set;

public class AmbiguousUpgradeException extends RuntimeException
{
    private final Set<Assignment> newAssignments;

    public AmbiguousUpgradeException(Set<Assignment> newAssignments)
    {
        super("Expected a single target assignment for upgrade, but got several target assignments: " + Joiner.on(", ").join(newAssignments));
        this.newAssignments = ImmutableSet.copyOf(newAssignments);
    }

    public Set<Assignment> getNewAssignments()
    {
        return newAssignments;
    }
}
