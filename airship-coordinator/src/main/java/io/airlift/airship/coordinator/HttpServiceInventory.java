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
package io.airlift.airship.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.inject.Inject;

import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.ConfigUtils;
import io.airlift.airship.shared.DigestUtils;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceState;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class HttpServiceInventory
        implements ServiceInventory
{
    private static final Logger log = Logger.get(HttpServiceInventory.class);
    private final Repository repository;
    private final JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec;
    private final Set<String> invalidServiceInventory = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final File cacheDir;

    @Inject
    public HttpServiceInventory(Repository repository, JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec, CoordinatorConfig config)
    {
        this(repository, descriptorsJsonCodec, new File(config.getServiceInventoryCacheDir()));
    }

    public HttpServiceInventory(Repository repository, JsonCodec<List<ServiceDescriptor>> descriptorsJsonCodec, File cacheDir)
    {
        Preconditions.checkNotNull(repository, "repository is null");
        Preconditions.checkNotNull(descriptorsJsonCodec, "descriptorsJsonCodec is null");

        this.repository = repository;
        this.descriptorsJsonCodec = descriptorsJsonCodec;
        this.cacheDir = cacheDir;
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
            value = value.replaceAll(Pattern.quote("${airship.host}"), slotStatus.getSelf().getHost());
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

        String config = assignment.getConfig();

        File cacheFile = getCacheFile(config);
        if (cacheFile.canRead()) {
            try {
                String json = Files.asCharSource(cacheFile, Charsets.UTF_8).read();
                List<ServiceDescriptor> descriptors = descriptorsJsonCodec.fromJson(json);
                invalidServiceInventory.remove(config);
                return descriptors;
            }
            catch (Exception ignored) {
                // delete the bad cache file
                cacheFile.delete();
            }
        }

        ByteSource configFile = ConfigUtils.newConfigEntrySupplier(repository, config, "airship-service-inventory.json");
        if (configFile == null) {
            return null;
        }

        try {
            String json;
            try {
                json = configFile.asCharSource(Charsets.UTF_8).read();
            }
            catch (FileNotFoundException e) {
                // no service inventory in the config, so replace with json null so caching works
                json = "null";
            }
            invalidServiceInventory.remove(config);

            // cache json
            cacheFile.getParentFile().mkdirs();
            Files.write(json, cacheFile, Charsets.UTF_8);

            List<ServiceDescriptor> descriptors = descriptorsJsonCodec.fromJson(json);
            return descriptors;
        }
        catch (Exception e) {
            if (invalidServiceInventory.add(config)) {
                log.error(e, "Unable to read service inventory for %s" + config);
            }
        }
        return null;
    }

    private File getCacheFile(String config)
    {
        String cacheName = config;
        if (cacheName.startsWith("@")) {
            cacheName = cacheName.substring(1);
        }
        cacheName = cacheName.replaceAll("[^a-zA-Z0-9_.-]", "_");

        cacheName = cacheName + "_" + DigestUtils.md5Hex(cacheName);
        return new File(cacheDir, cacheName).getAbsoluteFile();
    }
}
