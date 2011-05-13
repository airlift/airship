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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.proofpoint.configuration.Config;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CoordinatorConfig
{
    private List<String> binaryRepoBases = ImmutableList.of();
    private List<String> configRepoBases = ImmutableList.of();
    private String localConfigRepo;
    private Duration statusExpiration = new Duration(30, TimeUnit.SECONDS);

    @NotNull
    public List<String> getBinaryRepoBases()
    {
        return binaryRepoBases;
    }

    @Config("coordinator.binary-repo")
    public CoordinatorConfig setBinaryRepoBases(String binaryRepoBases)
    {
        this.binaryRepoBases = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(binaryRepoBases));
        return this;
    }

    @NotNull
    public List<String> getConfigRepoBases()
    {
        return configRepoBases;
    }

    @Config("coordinator.config-repo")
    public CoordinatorConfig setConfigRepoBases(String configRepoBases)
    {
        this.configRepoBases = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(configRepoBases));
        return this;
    }

    public String getLocalConfigRepo()
    {
        return localConfigRepo;
    }

    @Config("coordinator.config-repo.local")
    public void setLocalConfigRepo(String localConfigRepo)
    {
        this.localConfigRepo = localConfigRepo;
    }

    @NotNull
    public Duration getStatusExpiration()
    {
        return statusExpiration;
    }

    @Config("coordinator.status.expiration")
    public CoordinatorConfig setStatusExpiration(Duration statusExpiration)
    {
        this.statusExpiration = statusExpiration;
        return this;
    }
}
