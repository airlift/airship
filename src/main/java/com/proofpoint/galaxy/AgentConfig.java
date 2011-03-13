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
package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;
import com.proofpoint.configuration.Config;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

public class AgentConfig
{
    private String slotsDir;
    private Duration launcherTimeout = new Duration(1, TimeUnit.MINUTES);
    private Duration tarTimeout = new Duration(1, TimeUnit.MINUTES);
    private Duration maxLockWait = new Duration(1, TimeUnit.SECONDS);

    @NotNull
    public String getSlotsDir()
    {
        return slotsDir;
    }

    @Config("agent.slots-dir")
    public AgentConfig setSlotsDir(String slotsDir)
    {
        this.slotsDir = slotsDir;
        return this;
    }

    @NotNull
    public Duration getLauncherTimeout()
    {
        return launcherTimeout;
    }

    @Config("agent.launcher-timeout")
    public AgentConfig setLauncherTimeout(Duration launcherTimeout)
    {
        this.launcherTimeout = launcherTimeout;
        return this;
    }

    @NotNull
    public Duration getTarTimeout()
    {
        return tarTimeout;
    }

    @Config("agent.tar-timeout")
    public AgentConfig setTarTimeout(Duration tarTimeout)
    {
        this.tarTimeout = tarTimeout;
        return this;
    }

    @NotNull
    public Duration getMaxLockWait()
    {
        return maxLockWait;
    }

    @Config("agent.max-lock-wait")
    public AgentConfig setMaxLockWait(Duration lockWait)
    {
        // TODO: remove once configuration supports bean validation
        Preconditions.checkNotNull(lockWait, "ttl must not be null");
        Preconditions.checkArgument(lockWait.toMillis() > 0, "ttl must be > 0");

        this.maxLockWait = lockWait;
        return this;
    }
}
