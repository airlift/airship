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
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

@Immutable
public class SlotStatus
{
    public static SlotStatus createSlotStatus(UUID id,
            String name,
            URI self,
            URI externalUri,
            String location,
            SlotLifecycleState state,
            Assignment assignment,
            String installPath,
            Map<String, Integer> resources)
    {
        return new SlotStatus(id, name, self, externalUri, location, state, assignment, installPath, resources, null, null, null);
    }

    public static SlotStatus createSlotStatusWithExpectedState(UUID id,
            String name,
            URI self,
            URI externalUri,
            String location,
            SlotLifecycleState state,
            Assignment assignment,
            String installPath,
            Map<String, Integer> resources,
            SlotLifecycleState expectedState,
            Assignment expectedAssignment,
            String statusMessage)
    {
        return new SlotStatus(id, name, self, externalUri, location, state, assignment, installPath, resources, expectedState, expectedAssignment, statusMessage);
    }

    private final UUID id;
    private final String name;
    private final URI self;
    private final URI externalUri;
    private final String location;
    private final Assignment assignment;
    private final SlotLifecycleState state;
    private final String version;

    private final SlotLifecycleState expectedState;
    private final Assignment expectedAssignment;

    private final String statusMessage;

    private final String installPath;

    private final Map<String, Integer> resources;

    private SlotStatus(UUID id,
            String name,
            URI self,
            URI externalUri,
            String location,
            SlotLifecycleState state,
            Assignment assignment,
            String installPath,
            Map<String, Integer> resources,
            SlotLifecycleState expectedState,
            Assignment expectedAssignment,
            String statusMessage)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkNotNull(state, "state is null");
        if (state != TERMINATED && state != UNKNOWN) {
            Preconditions.checkNotNull(assignment, "assignment is null");
        }
        Preconditions.checkNotNull(resources, "resources is null");

        this.id = id;
        this.name = name;
        this.self = self;
        this.externalUri = externalUri;
        this.location = location;
        this.assignment = assignment;
        this.state = state;
        this.version = VersionsUtil.createSlotVersion(id, state, assignment);
        this.installPath = installPath;
        this.expectedState = expectedState;
        this.expectedAssignment = expectedAssignment;
        this.statusMessage = statusMessage;
        this.resources = ImmutableMap.copyOf(resources);
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

    public URI getExternalUri()
    {
        return externalUri;
    }

    public String getLocation()
    {
        return location;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public SlotLifecycleState getState()
    {
        return state;
    }

    public String getVersion()
    {
        return version;
    }

    public SlotLifecycleState getExpectedState()
    {
        return expectedState;
    }

    public Assignment getExpectedAssignment()
    {
        return expectedAssignment;
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    public String getInstallPath()
    {
        return installPath;
    }

    public Map<String, Integer> getResources()
    {
        return resources;
    }

    public SlotStatus changeState(SlotLifecycleState state)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.name,
                this.self,
                this.externalUri,
                this.location,
                state,
                state == TERMINATED ? null : this.assignment,
                state == TERMINATED ? null : this.installPath,
                state == TERMINATED ? ImmutableMap.<String, Integer>of() : this.resources,
                this.expectedState,
                this.expectedAssignment,
                this.statusMessage);
    }

    public SlotStatus changeAssignment(SlotLifecycleState state, Assignment assignment, Map<String, Integer> resources)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.name,
                this.self,
                this.externalUri,
                this.location,
                state,
                state == TERMINATED ? null : assignment,
                state == TERMINATED ? null : this.installPath,
                state == TERMINATED ? ImmutableMap.<String, Integer>of() : ImmutableMap.copyOf(resources),
                this.expectedState,
                this.expectedAssignment,
                this.statusMessage);
    }

    public SlotStatus changeExpectedState(SlotLifecycleState expectedState, Assignment expectedAssignment)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.name,
                this.self,
                this.externalUri,
                this.location,
                this.state,
                this.assignment,
                this.installPath,
                this.resources,
                expectedState,
                expectedAssignment,
                this.statusMessage);
    }

    public SlotStatus changeStatusMessage(String statusMessage)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.name,
                this.self,
                this.externalUri,
                this.location,
                this.state,
                this.assignment,
                this.installPath,
                this.resources,
                this.expectedState,
                this.expectedAssignment,
                statusMessage);
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
        if (!location.equals(that.location)) {
            return false;
        }
        if (self != null ? !self.equals(that.self) : that.self != null) {
            return false;
        }
        if (externalUri != null ? !externalUri.equals(that.externalUri) : that.externalUri != null) {
            return false;
        }
        if (state != that.state) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }
        if (!resources.equals(that.resources)) {
            return false;
        }
        if (expectedState != that.expectedState) {
            return false;
        }
        if (expectedAssignment != null ? !expectedAssignment.equals(that.expectedAssignment) : that.expectedAssignment != null) {
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
        result = 31 * result + externalUri.hashCode();
        result = 31 * result + location.hashCode();
        result = 31 * result + (assignment != null ? assignment.hashCode() : 0);
        result = 31 * result + state.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (installPath != null ? installPath.hashCode() : 0);
        result = 31 * result + resources.hashCode();
        result = 31 * result + (expectedState != null ? expectedState.hashCode() : 0);
        result = 31 * result + (expectedAssignment != null ? expectedAssignment.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("SlotStatus");
        sb.append("{id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append(", self=").append(self);
        sb.append(", externalUri=").append(externalUri);
        sb.append(", location='").append(location).append('\'');
        sb.append(", assignment=").append(assignment);
        sb.append(", state=").append(state);
        sb.append(", version='").append(version).append('\'');
        sb.append(", expectedState=").append(expectedState);
        sb.append(", expectedAssignment=").append(expectedAssignment);
        sb.append(", statusMessage='").append(statusMessage).append('\'');
        sb.append(", installPath='").append(installPath).append('\'');
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
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
