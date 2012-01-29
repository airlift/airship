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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.Map;

@Immutable
public class Installation
{
    private final String shortName;
    private final Assignment assignment;
    private final URI binaryFile;
    private final URI configFile;
    private final Map<String, Integer> resources;

    public Installation(String shortName, Assignment assignment, URI binaryFile, URI configFile, Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(shortName, "shortName is null");
        Preconditions.checkNotNull(assignment, "assignment is null");
        Preconditions.checkNotNull(binaryFile, "binaryFile is null");
        Preconditions.checkNotNull(configFile, "configFile is null");
        Preconditions.checkNotNull(resources, "resources is null");

        this.shortName = shortName;
        this.assignment = assignment;
        this.binaryFile = binaryFile;
        this.configFile = configFile;
        this.resources = ImmutableMap.copyOf(resources);
    }

    public String getShortName()
    {
        return shortName;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public URI getBinaryFile()
    {
        return binaryFile;
    }

    public URI getConfigFile()
    {
        return configFile;
    }

    public Map<String, Integer> getResources()
    {
        return resources;
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
        final StringBuilder sb = new StringBuilder();
        sb.append("Installation");
        sb.append("{shortName=").append(shortName);
        sb.append(", assignment=").append(assignment);
        sb.append(", binaryFile=").append(binaryFile);
        sb.append(", configFile=").append(configFile);
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }
}
