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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

@Immutable
public class Assignment
{
    private final BinarySpec binary;
    private final URI binaryFile;
    private final ConfigSpec config;
    private final Map<String, URI> configFiles;

    public Assignment(String binary, String binaryFile, String config, Map<String, String> configFiles)
    {
        Preconditions.checkNotNull(binary, "binary is null");
        Preconditions.checkNotNull(binaryFile, "binaryFile is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(configFiles, "configFiles is null");

        this.binary = BinarySpec.valueOf(binary);
        this.binaryFile = URI.create(binaryFile);

        this.config = ConfigSpec.valueOf(config);
        Builder<String,URI> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : configFiles.entrySet()) {
            builder.put(entry.getKey(), URI.create(entry.getValue()));
        }
        this.configFiles = builder.build();
    }

    public Assignment(BinarySpec binary, URI binaryFile, ConfigSpec config, Map<String, URI> configFiles)
    {
        Preconditions.checkNotNull(binary, "binary is null");
        Preconditions.checkNotNull(binaryFile, "binaryFile is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(configFiles, "configFiles is null");

        this.binary = binary;
        this.binaryFile = binaryFile;
        this.config = config;
        this.configFiles = ImmutableMap.copyOf(configFiles);
    }

    public Assignment(String binary, BinaryRepository binaryRepository, String config, ConfigRepository configRepository) {
        Preconditions.checkNotNull(binary, "binary is null");
        Preconditions.checkNotNull(binaryRepository, "binaryRepository is null");
        Preconditions.checkNotNull(config, "configSpec is null");
        Preconditions.checkNotNull(configRepository, "configRepository is null");

        this.binary = BinarySpec.valueOf(binary);
        this.binaryFile = binaryRepository.getBinaryUri(this.binary);
        this.config = ConfigSpec.valueOf(config);
        this.configFiles = configRepository.getConfigMap(this.config);
    }

    public Assignment(BinarySpec binary, BinaryRepository binaryRepository, ConfigSpec config, ConfigRepository configRepository) {
        Preconditions.checkNotNull(binary, "binary is null");
        Preconditions.checkNotNull(binaryRepository, "binaryRepository is null");
        Preconditions.checkNotNull(config, "configSpec is null");
        Preconditions.checkNotNull(configRepository, "configRepository is null");

        this.binary = binary;
        this.binaryFile = binaryRepository.getBinaryUri(binary);
        this.config = config;
        this.configFiles = configRepository.getConfigMap(config);
    }

    public BinarySpec getBinary()
    {
        return binary;
    }

    public URI getBinaryFile()
    {
        return binaryFile;
    }

    public ConfigSpec getConfig()
    {
        return config;
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

        Assignment that = (Assignment) o;

        if (!binary.equals(that.binary)) {
            return false;
        }
        if (!config.equals(that.config)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = binary.hashCode();
        result = 31 * result + config.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Assignment");
        sb.append("{binary=").append(binary);
        sb.append(", config=").append(config);
        sb.append(", binaryFile=").append(binaryFile);
        sb.append(", configFiles=").append(configFiles);
        sb.append('}');
        return sb.toString();
    }
}
