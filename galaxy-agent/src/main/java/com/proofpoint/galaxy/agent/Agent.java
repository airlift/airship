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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static java.lang.Math.max;
import static java.lang.String.format;

public class Agent
{
    private final String agentId;
    private final ConcurrentMap<String, Slot> slots;
    private final DeploymentManagerFactory deploymentManagerFactory;
    private final LifecycleManager lifecycleManager;
    private final File slotsDir;
    private final String location;
    private final Map<String, Integer> resources;
    private final Duration maxLockWait;
    private final URI internalUri;
    private final URI externalUri;

    @Inject
    public Agent(AgentConfig config,
            HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            DeploymentManagerFactory deploymentManagerFactory,
            LifecycleManager lifecycleManager)
    {
        this(nodeInfo.getNodeId(),
                nodeInfo.getLocation(),
                config.getSlotsDir(),
                httpServerInfo.getHttpUri(),
                httpServerInfo.getHttpExternalUri(),
                config.getResourcesFile(),
                deploymentManagerFactory,
                lifecycleManager,
                config.getMaxLockWait()
        );
    }

    public Agent(
            String agentId,
            String location,
            String slotsDir,
            URI internalUri,
            URI externalUri,
            String resourcesFilename,
            DeploymentManagerFactory deploymentManagerFactory,
            LifecycleManager lifecycleManager,
            Duration maxLockWait)
    {
        Preconditions.checkNotNull(agentId, "agentId is null");
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkNotNull(slotsDir, "slotsDir is null");
        Preconditions.checkNotNull(internalUri, "internalUri is null");
        Preconditions.checkNotNull(externalUri, "externalUri is null");
        Preconditions.checkNotNull(deploymentManagerFactory, "deploymentManagerFactory is null");
        Preconditions.checkNotNull(lifecycleManager, "lifecycleManager is null");
        Preconditions.checkNotNull(maxLockWait, "maxLockWait is null");

        this.agentId = agentId;
        this.internalUri = internalUri;
        this.externalUri = externalUri;
        this.maxLockWait = maxLockWait;
        this.location = location;

        this.deploymentManagerFactory = deploymentManagerFactory;
        this.lifecycleManager = lifecycleManager;

        slots = new ConcurrentHashMap<String, Slot>();

        this.slotsDir = new File(slotsDir);
        if (!this.slotsDir.isDirectory()) {
            this.slotsDir.mkdirs();
            Preconditions.checkArgument(this.slotsDir.isDirectory(), format("Slots directory %s is not a directory", this.slotsDir));
        }

        // Assure data dir exists or can be created
        File dataDir = new File(slotsDir);
        if (!dataDir.isDirectory()) {
            dataDir.mkdirs();
            Preconditions.checkArgument(dataDir.isDirectory(), format("Data directory %s is not a directory", dataDir));
        }

        //
        // Load existing slots
        //
        for (DeploymentManager deploymentManager : this.deploymentManagerFactory.loadSlots()) {
            String slotName = deploymentManager.getSlotName();
            if (deploymentManager.getDeployment() == null) {
                // todo bad slot
            }
            else {
                URI slotInternalUri = internalUri.resolve("/v1/agent/slot/").resolve(slotName);
                URI slotExternalUri = externalUri.resolve("/v1/agent/slot/").resolve(slotName);
                Slot slot = new DeploymentSlot(slotInternalUri, slotExternalUri, deploymentManager, lifecycleManager, maxLockWait);
                slots.put(slotName, slot);
            }
        }

        //
        // Load resources file
        //
        Map<String, Integer> resources = ImmutableMap.of();
        if (resourcesFilename != null) {
            File resourcesFile = new File(resourcesFilename);
            if (resourcesFile.canRead()) {
                ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
                Properties properties = new Properties();
                FileInputStream in = null;
                try {
                    in = new FileInputStream(resourcesFile);
                    properties.load(in);
                    for (Entry<Object, Object> entry : properties.entrySet()) {
                        builder.put((String) entry.getKey(), Integer.valueOf((String) entry.getValue()));
                    }
                }
                catch (IOException ignored) {
                }
                finally {
                    Closeables.closeQuietly(in);
                }
                resources = builder.build();
            }
        }
        this.resources = resources;
    }

    public Map<String, Integer> getResources()
    {
        return resources;
    }

    public String getAgentId()
    {
        return agentId;
    }

    public String getLocation()
    {
        return location;
    }

    public AgentStatus getAgentStatus()
    {
        Builder<SlotStatus> builder = ImmutableList.builder();
        for (Slot slot : slots.values()) {
            SlotStatus slotStatus = slot.status();
            builder.add(slotStatus);
        }
        AgentStatus agentStatus = new AgentStatus(agentId, ONLINE, null, internalUri, externalUri, location, null, builder.build(), resources);
        return agentStatus;
    }

    public Slot getSlot(String name)
    {
        Preconditions.checkNotNull(name, "name must not be null");

        Slot slot = slots.get(name);
        return slot;
    }

    public SlotStatus install(Installation installation)
    {
        // todo name selection is not thread safe
        // create slot
        String slotName = getNextSlotName(installation.getShortName());
        URI slotInternalUri = internalUri.resolve("/v1/agent/slot/").resolve(slotName);
        URI slotExternalUri = externalUri.resolve("/v1/agent/slot/").resolve(slotName);
        Slot slot = new DeploymentSlot(slotInternalUri, slotExternalUri, deploymentManagerFactory.createDeploymentManager(slotName), lifecycleManager, installation, maxLockWait);
        slots.put(slotName, slot);

        // return last slot status
        return slot.getLastSlotStatus();
    }

    public SlotStatus terminateSlot(String name)
    {
        Preconditions.checkNotNull(name, "name must not be null");

        Slot slot = slots.get(name);
        if (slot == null) {
            return null;
        }

        SlotStatus status = slot.terminate();
        if (status.getState() == TERMINATED) {
            slots.remove(name);
        }
        return status;
    }

    public Collection<Slot> getAllSlots()
    {
        return ImmutableList.copyOf(slots.values());
    }

    private String getNextSlotName(String baseName)
    {
        baseName = baseName.replace("[^a-zA-Z0-9_.-]", "_");
        if (!slots.containsKey(baseName)) {
            return baseName;
        }

        Pattern pattern = Pattern.compile(baseName + "(\\d+)");

        int nextId = 1;
        for (String deploymentId : slots.keySet()) {
            Matcher matcher = pattern.matcher(deploymentId);
            if (matcher.matches()) {
                try {
                    int id = Integer.parseInt(matcher.group(1));
                    nextId = max(nextId, id + 1);
                }
                catch (NumberFormatException ignored) {
                }
            }
        }

        for (int i = 0; i < 10000; i++) {
            String deploymentId = baseName + (nextId + i);
            if (!new File(slotsDir, deploymentId).exists()) {
                return deploymentId;
            }
        }
        throw new IllegalStateException("Could not find an valid slot name");
    }
}

