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

import com.google.common.collect.Sets;
import com.proofpoint.galaxy.shared.SlotLifecycleState;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MockLifecycleManager implements LifecycleManager
{
    private final Map<String, SlotLifecycleState> states = new TreeMap<String, SlotLifecycleState>();
    private final Set<String> nodeConfigUpdated = Sets.newHashSet();

    @Override
    public SlotLifecycleState status(Deployment deployment)
    {
        SlotLifecycleState state = states.get(deployment.getDeploymentId());
        if (state == null) {
            return SlotLifecycleState.STOPPED;
        }
        return state;
    }

    @Override
    public SlotLifecycleState start(Deployment deployment)
    {
        states.put(deployment.getDeploymentId(), SlotLifecycleState.RUNNING);
        return SlotLifecycleState.RUNNING;
    }

    @Override
    public SlotLifecycleState restart(Deployment deployment)
    {
        states.put(deployment.getDeploymentId(), SlotLifecycleState.RUNNING);
        return SlotLifecycleState.RUNNING;
    }

    @Override
    public SlotLifecycleState stop(Deployment deployment)
    {
        states.put(deployment.getDeploymentId(), SlotLifecycleState.STOPPED);
        return SlotLifecycleState.STOPPED;
    }

    @Override
    public void updateNodeConfig(Deployment deployment)
    {
        nodeConfigUpdated.add(deployment.getDeploymentId());
    }

    public Set<String> getNodeConfigUpdated()
    {
        return nodeConfigUpdated;
    }
}
