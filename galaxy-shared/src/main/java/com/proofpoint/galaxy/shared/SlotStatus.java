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
package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

@Immutable
public class SlotStatus
{
    private final UUID id;
    private final String name;
    private final URI self;
    private final Assignment assignment;
    private final SlotLifecycleState state;
    private final String installPath;

    public SlotStatus(UUID id, String name, URI self, SlotLifecycleState state, Assignment assignment, String installPath)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(self, "self is null");
        Preconditions.checkNotNull(state, "state is null");
        if (state != TERMINATED && state != UNKNOWN) {
            Preconditions.checkNotNull(assignment, "assignment is null");
        }

        this.id = id;
        this.name = name;
        this.self = self;
        this.assignment = assignment;
        this.state = state;
        this.installPath = installPath;
    }

    public SlotStatus(SlotStatus status, SlotLifecycleState state)
    {
        Preconditions.checkNotNull(status, "status is null");
        Preconditions.checkNotNull(state, "state is null");

        this.id = status.id;
        this.name = status.name;
        this.self = status.self;
        if (state != TERMINATED) {
            this.assignment = status.assignment;
            this.installPath = status.installPath;
        }
        else {
            this.assignment = null;
            this.installPath = null;
        }
        this.state = state;
    }
    
    public SlotStatus(SlotStatus status, SlotLifecycleState state, Assignment assignment)
    {
        this(status, state, assignment, status.getInstallPath());
    }

    public SlotStatus(SlotStatus status, SlotLifecycleState state, Assignment assignment, String installPath)
    {
        Preconditions.checkNotNull(status, "status is null");
        Preconditions.checkNotNull(state, "state is null");
        Preconditions.checkNotNull(assignment, "assignment is null");

        this.id = status.id;
        this.name = status.name;
        this.self = status.self;
        this.assignment = assignment;
        this.state = state;
        this.installPath = installPath;
    }

    public UUID getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public URI getSelf()
    {
        return self;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public SlotLifecycleState getState()
    {
        return state;
    }

    public String getInstallPath()
    {
        return installPath;
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

        SlotStatus that = (SlotStatus) o;

        if (assignment != null ? !assignment.equals(that.assignment) : that.assignment != null) {
            return false;
        }
        if (!id.equals(that.id)) {
            return false;
        }
        if (installPath != null ? !installPath.equals(that.installPath) : that.installPath != null) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        if (!self.equals(that.self)) {
            return false;
        }
        if (state != that.state) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + self.hashCode();
        result = 31 * result + (assignment != null ? assignment.hashCode() : 0);
        result = 31 * result + state.hashCode();
        result = 31 * result + (installPath != null ? installPath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "SlotStatus{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", self=" + self +
                ", assignment=" + assignment +
                ", state=" + state +
                ", installPath='" + installPath + '\'' +
                '}';
    }

    public static Function<SlotStatus, UUID> uuidGetter()
    {
        return new Function<SlotStatus, UUID>()
        {
            public UUID apply(SlotStatus input)
            {
                return input.getId();
            }
        };
    }

}
