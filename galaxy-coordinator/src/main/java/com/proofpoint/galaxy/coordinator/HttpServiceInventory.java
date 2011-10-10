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
package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class HttpServiceInventory implements ServiceInventory
{
    private final ConfigRepository configRepository;
    private final String environment;
    private final JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec;

    @Inject
    public HttpServiceInventory(ConfigRepository configRepository, JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec, NodeInfo nodeInfo)
    {
        Preconditions.checkNotNull(configRepository, "repository is null");
        Preconditions.checkNotNull(descriptorsJsonCodec, "descriptorsJsonCodec is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");

        this.configRepository = configRepository;
        this.descriptorsJsonCodec = descriptorsJsonCodec;
        environment = nodeInfo.getEnvironment();
    }

    @Override
    public ImmutableList<ServiceDescriptor> getServiceInventory(List<SlotStatus> allSlotStatus)
    {
        ImmutableList.Builder<ServiceDescriptor> newDescriptors = ImmutableList.builder();
        for (SlotStatus slotStatus : allSlotStatus) {
            List<ServiceDescriptor> serviceDescriptors = getServiceInventory(slotStatus);
            if (serviceDescriptors == null) {
                continue;
            }
            ConfigSpec config = slotStatus.getAssignment().getConfig();
            for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
                newDescriptors.add(new ServiceDescriptor(null,
                        slotStatus.getId().toString(),
                        serviceDescriptor.getType(),
                        config.getPool(),
                        slotStatus.getLocation(),
                        interpolateProperties(serviceDescriptor.getProperties(), slotStatus)));
            }
        }
        return newDescriptors.build();
    }

    private Map<String, String> interpolateProperties(Map<String, String> properties, SlotStatus slotStatus)
    {
        ImmutableMap.Builder<String,String> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            value = value.replaceAll(Pattern.quote("${galaxy.host}"), slotStatus.getSelf().getHost());
            builder.put(key, value);
        }
        return builder.build();
    }

    private List<ServiceDescriptor> getServiceInventory(SlotStatus slotStatus)
    {
        Assignment assignment = slotStatus.getAssignment();
        if (assignment == null) {
            return null;
        }
        ConfigSpec config = assignment.getConfig();
        try {
            InputSupplier<? extends InputStream> configFile = configRepository.getConfigFile(environment, config, "galaxy-service-inventory.json");
            String json = CharStreams.toString(CharStreams.newReaderSupplier(configFile, Charsets.UTF_8));
            return descriptorsJsonCodec.fromJson(json);
        }
        catch (Exception e) {
            return null;
        }
    }
}
