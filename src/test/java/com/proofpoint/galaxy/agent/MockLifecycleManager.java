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

import com.proofpoint.galaxy.LifecycleState;

import java.util.Map;
import java.util.TreeMap;

public class MockLifecycleManager implements LifecycleManager
{
    private final Map<String, LifecycleState> states = new TreeMap<String, LifecycleState>();

    @Override
    public LifecycleState status(Deployment deployment)
    {
        LifecycleState state = states.get(deployment.getDeploymentId());
        if (state == null) {
            return LifecycleState.STOPPED;
        }
        return state;
    }

    @Override
    public LifecycleState start(Deployment deployment)
    {
        states.put(deployment.getDeploymentId(), LifecycleState.RUNNING);
        return LifecycleState.RUNNING;
    }

    @Override
    public LifecycleState restart(Deployment deployment)
    {
        states.put(deployment.getDeploymentId(), LifecycleState.RUNNING);
        return LifecycleState.RUNNING;
    }

    @Override
    public LifecycleState stop(Deployment deployment)
    {
        states.put(deployment.getDeploymentId(), LifecycleState.STOPPED);
        return LifecycleState.STOPPED;
    }
}
