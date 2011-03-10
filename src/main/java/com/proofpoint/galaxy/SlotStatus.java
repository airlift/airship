/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

@Immutable
public class SlotStatus
{
    private final String name;
    private final Assignment assignment;
    private final LifecycleState state;

    public SlotStatus(String name)
    {
        Preconditions.checkNotNull(name, "name is null");

        this.name = name;
        this.assignment = null;
        this.state = LifecycleState.UNASSIGNED;
    }

    public SlotStatus(String name, Assignment assignment, LifecycleState state)
    {
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(assignment, "assignment is null");
        Preconditions.checkNotNull(state, "state is null");

        this.name = name;
        this.assignment = assignment;
        this.state = state;
    }

    public String getName()
    {
        return name;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public LifecycleState getState()
    {
        return state;
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

        SlotStatus slotStatus = (SlotStatus) o;

        if (assignment != null ? !assignment.equals(slotStatus.assignment) : slotStatus.assignment != null) {
            return false;
        }
        if (!name.equals(slotStatus.name)) {
            return false;
        }
        if (state != slotStatus.state) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = name.hashCode();
        result = 31 * result + (assignment != null ? assignment.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Slot");
        sb.append("{name='").append(name).append('\'');
        sb.append(", assignment=").append(assignment);
        sb.append(", state=").append(state);
        sb.append('}');
        return sb.toString();
    }
}
