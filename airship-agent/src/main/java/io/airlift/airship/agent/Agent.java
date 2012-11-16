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
package io.airlift.airship.agent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import com.google.inject.Inject;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static java.lang.String.format;

public class Agent
{
    private final String agentId;
    private final ConcurrentMap<UUID, Slot> slots;
    private final DeploymentManagerFactory deploymentManagerFactory;
    private final LifecycleManager lifecycleManager;
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
            String slotsDirName,
            URI internalUri,
            URI externalUri,
            String resourcesFilename,
            DeploymentManagerFactory deploymentManagerFactory,
            LifecycleManager lifecycleManager,
            Duration maxLockWait)
    {
        Preconditions.checkNotNull(agentId, "agentId is null");
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkNotNull(slotsDirName, "slotsDirName is null");
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

        slots = new ConcurrentHashMap<UUID, Slot>();

        File slotsDir = new File(slotsDirName);
        if (!slotsDir.isDirectory()) {
            slotsDir.mkdirs();
            Preconditions.checkArgument(slotsDir.isDirectory(), format("Slots directory %s is not a directory", slotsDir));
        }

        //
        // Load existing slots
        //
        for (DeploymentManager deploymentManager : this.deploymentManagerFactory.loadSlots()) {
            UUID slotId = deploymentManager.getSlotId();
            if (deploymentManager.getDeployment() == null) {
                // todo bad slot
            }
            else {
                URI slotInternalUri = uriBuilderFrom(internalUri).appendPath("/v1/agent/slot/").appendPath(slotId.toString()).build();
                URI slotExternalUri = uriBuilderFrom(externalUri).appendPath("/v1/agent/slot/").appendPath(slotId.toString()).build();
                Slot slot = new DeploymentSlot(slotInternalUri, slotExternalUri, deploymentManager, lifecycleManager, maxLockWait);
                slots.put(slotId, slot);
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

    public Slot getSlot(UUID slotId)
    {
        Preconditions.checkNotNull(slotId, "slotId must not be null");

        Slot slot = slots.get(slotId);
        return slot;
    }

    public SlotStatus install(Installation installation)
    {
        // todo name selection is not thread safe
        // create slot
        DeploymentManager deploymentManager = deploymentManagerFactory.createDeploymentManager(installation);
        UUID slotId = deploymentManager.getSlotId();

        URI slotInternalUri = uriBuilderFrom(internalUri).appendPath("/v1/agent/slot/").appendPath(slotId.toString()).build();
        URI slotExternalUri = uriBuilderFrom(externalUri).appendPath("/v1/agent/slot/").appendPath(slotId.toString()).build();
        Slot slot = new DeploymentSlot(slotInternalUri, slotExternalUri, deploymentManager, lifecycleManager, installation, maxLockWait);
        slots.put(slotId, slot);

        // return last slot status
        return slot.getLastSlotStatus();
    }

    public SlotStatus terminateSlot(UUID slotId)
    {
        Preconditions.checkNotNull(slotId, "slotId must not be null");

        Slot slot = slots.get(slotId);
        if (slot == null) {
            return null;
        }

        SlotStatus status = slot.terminate();
        if (status.getState() == TERMINATED) {
            slots.remove(slotId);
        }
        return status;
    }

    public Collection<Slot> getAllSlots()
    {
        return ImmutableList.copyOf(slots.values());
    }
}

