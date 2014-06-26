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

import com.google.common.collect.Sets;
import io.airlift.airship.shared.SlotLifecycleState;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.google.common.collect.Maps.newHashMap;

public class MockLifecycleManager
        implements LifecycleManager
{
    private final Map<UUID, SlotLifecycleState> states = newHashMap();
    private final Set<UUID> nodeConfigUpdated = Sets.newHashSet();

    @Override
    public SlotLifecycleState status(Deployment deployment)
    {
        SlotLifecycleState state = states.get(deployment.getNodeId());
        if (state == null) {
            return SlotLifecycleState.STOPPED;
        }
        return state;
    }

    @Override
    public SlotLifecycleState start(Deployment deployment)
    {
        states.put(deployment.getNodeId(), SlotLifecycleState.RUNNING);
        return SlotLifecycleState.RUNNING;
    }

    @Override
    public SlotLifecycleState restart(Deployment deployment)
    {
        states.put(deployment.getNodeId(), SlotLifecycleState.RUNNING);
        return SlotLifecycleState.RUNNING;
    }

    @Override
    public SlotLifecycleState stop(Deployment deployment)
    {
        states.put(deployment.getNodeId(), SlotLifecycleState.STOPPED);
        return SlotLifecycleState.STOPPED;
    }

    @Override
    public SlotLifecycleState kill(Deployment deployment)
    {
        return stop(deployment);
    }

    @Override
    public void updateNodeConfig(Deployment deployment)
    {
        nodeConfigUpdated.add(deployment.getNodeId());
    }

    public Set<UUID> getNodeConfigUpdated()
    {
        return nodeConfigUpdated;
    }
}
