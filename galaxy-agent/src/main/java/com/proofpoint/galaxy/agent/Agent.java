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
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;

import java.io.File;
import java.net.URI;
import java.util.Collection;
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
    private final AgentConfig config;
    private final DeploymentManagerFactory deploymentManagerFactory;
    private final LifecycleManager lifecycleManager;
    private final File slotsDir;
    private final HttpServerInfo httpServerInfo;
    private final String location;
    private final String instanceType;

    @Inject
    public Agent(AgentConfig config,
            HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            DeploymentManagerFactory deploymentManagerFactory,
            LifecycleManager lifecycleManager)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(httpServerInfo, "httpServerInfo is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(deploymentManagerFactory, "deploymentManagerFactory is null");
        Preconditions.checkNotNull(lifecycleManager, "lifecycleManager is null");

        this.agentId = nodeInfo.getInstanceId();
        this.config = config;
        this.httpServerInfo = httpServerInfo;
        location = nodeInfo.getLocation();
        instanceType = config.getInstanceType();

        this.deploymentManagerFactory = deploymentManagerFactory;
        this.lifecycleManager = lifecycleManager;

        slots = new ConcurrentHashMap<String, Slot>();
        
        slotsDir = new File(this.config.getSlotsDir());
        if (!slotsDir.isDirectory()) {
            slotsDir.mkdirs();
            Preconditions.checkArgument(slotsDir.isDirectory(), format("Slots directory %s is not a directory", slotsDir));
        }

        // Assure data dir exists or can be created
        File dataDir = new File(this.config.getSlotsDir());
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
            } else {
                URI slotUri = httpServerInfo.getHttpUri().resolve("/v1/agent/slot/").resolve(slotName);
                Slot slot = new DeploymentSlot(slotUri, deploymentManager, lifecycleManager, config.getMaxLockWait());
                slots.put(slotName, slot);
            }
        }
    }

    public String getAgentId()
    {
        return agentId;
    }

    public AgentStatus getAgentStatus()
    {
        Builder<SlotStatus> builder = ImmutableList.builder();
        for (Slot slot : slots.values()) {
            SlotStatus slotStatus = slot.status();
            builder.add(slotStatus);
        }
        AgentStatus agentStatus = new AgentStatus(agentId, ONLINE, httpServerInfo.getHttpUri(), location, instanceType, builder.build());
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
        // create slot
        String slotName = getNextSlotName(installation.getAssignment().getConfig().getComponent());
        URI slotUri = httpServerInfo.getHttpUri().resolve("/v1/agent/slot/").resolve(slotName);
        Slot slot = new DeploymentSlot(slotUri, deploymentManagerFactory.createDeploymentManager(slotName), lifecycleManager, installation, config.getMaxLockWait());
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

