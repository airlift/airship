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
package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.Assignment;
import com.proofpoint.galaxy.coordinator.BinaryRepository;
import com.proofpoint.galaxy.coordinator.ConfigRepository;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.Map;

@Immutable
public class Installation
{
    private final Assignment assignment;
    private final URI binaryFile;
    private final Map<String, URI> configFiles;

    public Installation(Assignment assignment, URI binaryFile, Map<String, URI> configFiles)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");
        Preconditions.checkNotNull(binaryFile, "binaryFile is null");
        Preconditions.checkNotNull(configFiles, "configFiles is null");

        this.assignment = assignment;
        this.binaryFile = binaryFile;
        this.configFiles = ImmutableMap.copyOf(configFiles);
    }

    public Installation(Assignment assignment, BinaryRepository binaryRepository, ConfigRepository configRepository)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");
        Preconditions.checkNotNull(binaryRepository, "binaryRepository is null");
        Preconditions.checkNotNull(configRepository, "configRepository is null");

        this.assignment = assignment;
        this.binaryFile = binaryRepository.getBinaryUri(assignment.getBinary());
        this.configFiles = configRepository.getConfigMap(assignment.getConfig());
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public URI getBinaryFile()
    {
        return binaryFile;
    }

    public Map<String, URI> getConfigFiles()
    {
        return configFiles;
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

        Installation that = (Installation) o;

        if (!assignment.equals(that.assignment)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return assignment.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Installation");
        sb.append("{assignment=").append(assignment);
        sb.append(", binaryFile=").append(binaryFile);
        sb.append(", configFiles=").append(configFiles);
        sb.append('}');
        return sb.toString();
    }
}
