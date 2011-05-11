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
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.log.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Charsets.UTF_8;
import static java.lang.Math.max;
import static java.lang.String.format;

public class Agent
{
    private static final Logger log = Logger.get(Agent.class);
    private final UUID agentId;
    private final ConcurrentMap<String, Slot> slots;
    private final AgentConfig config;
    private final DeploymentManagerFactory deploymentManager;
    private final LifecycleManager lifecycleManager;
    private final File slotsDir;
    private final HttpServerInfo httpServerInfo;

    @Inject
    public Agent(AgentConfig config, HttpServerInfo httpServerInfo, DeploymentManagerFactory deploymentManagerFactory, LifecycleManager lifecycleManager)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(deploymentManagerFactory, "deploymentManagerFactory is null");
        Preconditions.checkNotNull(lifecycleManager, "lifecycleManager is null");

        this.config = config;
        this.httpServerInfo = httpServerInfo;

        this.deploymentManager = deploymentManagerFactory;
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
        // Load agent id or create a new one (and save it)
        //
        File agentIdFile = new File(config.getSlotsDir(), "galaxy-agent-id.txt");
        UUID uuid = null;
        if (agentIdFile.exists()) {
            Preconditions.checkArgument(agentIdFile.canRead(), "can not read " + agentIdFile.getAbsolutePath());
            try {
                String agentIdString = Files.toString(agentIdFile, UTF_8).trim();
                try {
                    uuid = UUID.fromString(agentIdString);
                }
                catch (IllegalArgumentException e) {

                }
                if (uuid == null) {
                    log.warn("Invalid agent id [" + agentIdString + "]: attempting to delete galaxy-agent-id.txt file and recreating a new one");
                    agentIdFile.delete();
                }
            }
            catch (IOException e) {
                Preconditions.checkArgument(agentIdFile.canRead(), "can not read " + agentIdFile.getAbsolutePath());
            }
        }

        if (uuid == null) {
            uuid = UUID.randomUUID();
            try {
                Files.write(uuid.toString(), agentIdFile, UTF_8);
            }
            catch (IOException e) {
                Preconditions.checkArgument(agentIdFile.canRead(), "can not write " + agentIdFile.getAbsolutePath());
            }
        }
        agentId = uuid;

        //
        // Load existing slots
        //
        for (DeploymentManager manager : deploymentManager.loadSlots()) {
            String slotName = manager.getSlotName();
            URI slotUri = httpServerInfo.getHttpUri().resolve("/v1/slot/").resolve(slotName);
            Slot slot = new DeploymentSlot(slotName, config, slotUri, deploymentManager.createDeploymentManager(slotName), lifecycleManager);
            slots.put(slotName, slot);
        }
    }

    public UUID getAgentId()
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
        AgentStatus agentStatus = new AgentStatus(httpServerInfo.getHttpUri(), agentId, builder.build());
        return agentStatus;
    }

    public Slot getSlot(String name)
    {
        Preconditions.checkNotNull(name, "name must not be null");

        Slot slot = slots.get(name);
        return slot;
    }

    public Slot addNewSlot()
    {
        String slotName = getNextSlotName();
        Slot slot = createSlot(slotName);
        return slot;
    }

    private Slot createSlot(String slotName)
    {
        URI slotUri = httpServerInfo.getHttpUri().resolve("/v1/slot/").resolve(slotName);
        Slot slot = new DeploymentSlot(slotName, config, slotUri, deploymentManager.createDeploymentManager(slotName), lifecycleManager);
        slots.put(slotName, slot);
        return slot;
    }

    public boolean deleteSlot(String name)
    {
        Preconditions.checkNotNull(name, "name must not be null");

        Slot slot = slots.remove(name);
        if (slot == null) {
            return false;
        }

        slot.clear();
        return true;
    }

    public Collection<Slot> getAllSlots()
    {
        return ImmutableList.copyOf(slots.values());
    }


    private static final Pattern SLOT_ID_PATTERN = Pattern.compile("slot(\\d+)");

    private String getNextSlotName()
    {
        int nextId = 1;
        for (String deploymentId : slots.keySet()) {
            Matcher matcher = SLOT_ID_PATTERN.matcher(deploymentId);
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
            String deploymentId = "slot" + (nextId + i);
            if (!new File(slotsDir, deploymentId).exists()) {
                return deploymentId;
            }
        }
        throw new IllegalStateException("Could not find an valid slot name");
    }

}

