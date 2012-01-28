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
import com.proofpoint.discovery.client.ServiceState;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.ConfigUtils;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public class HttpServiceInventory implements ServiceInventory
{
    private static final Logger log = Logger.get(HttpServiceInventory.class);
    private final Repository repository;
    private final String environment;
    private final JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec;
    private final Set<ConfigSpec> invalidServiceInventory = Collections.newSetFromMap(new ConcurrentHashMap<ConfigSpec, Boolean>());

    @Inject
    public HttpServiceInventory(Repository repository, NodeInfo nodeInfo, JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec)
    {
        this(repository, checkNotNull(nodeInfo, "nodeInfo is null").getEnvironment(), descriptorsJsonCodec);
    }

    public HttpServiceInventory(Repository repository, String environment, JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec)
    {
        Preconditions.checkNotNull(repository, "repository is null");
        Preconditions.checkNotNull(descriptorsJsonCodec, "descriptorsJsonCodec is null");
        Preconditions.checkNotNull(environment, "environment is null");

        this.repository = repository;
        this.descriptorsJsonCodec = descriptorsJsonCodec;
        this.environment = environment;
    }

    @Override
    public ImmutableList<ServiceDescriptor> getServiceInventory(Iterable<SlotStatus> allSlotStatus)
    {
        ImmutableList.Builder<ServiceDescriptor> newDescriptors = ImmutableList.builder();
        for (SlotStatus slotStatus : allSlotStatus) {
            // if the self reference is null, the slot is totally offline so skip for now
            if (slotStatus.getSelf() == null) {
                continue;
            }

            List<ServiceDescriptor> serviceDescriptors = getServiceInventory(slotStatus);
            if (serviceDescriptors == null) {
                continue;
            }
            for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
                newDescriptors.add(new ServiceDescriptor(null,
                        slotStatus.getId().toString(),
                        serviceDescriptor.getType(),
                        serviceDescriptor.getPool(),
                        slotStatus.getLocation(),
                        slotStatus.getState() == SlotLifecycleState.RUNNING ? ServiceState.RUNNING : ServiceState.STOPPED,
                        interpolateProperties(serviceDescriptor.getProperties(), slotStatus)));
            }
        }
        return newDescriptors.build();
    }

    private Map<String, String> interpolateProperties(Map<String, String> properties, SlotStatus slotStatus)
    {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
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
        InputSupplier<? extends InputStream> configFile = ConfigUtils.newConfigEntrySupplier(repository, config, "galaxy-service-inventory.json");
        if (configFile == null) {
            return null;
        }

        try {
            String json = CharStreams.toString(CharStreams.newReaderSupplier(configFile, Charsets.UTF_8));
            List<ServiceDescriptor> descriptors = descriptorsJsonCodec.fromJson(json);
            invalidServiceInventory.remove(config);
            return descriptors;
        }
        catch (FileNotFoundException e) {
        }
        catch (Exception e) {
            if (invalidServiceInventory.add(config)) {
                log.error(e, "Unable to read service inventory for %s" + config);
            }
        }
        return null;
    }
}
