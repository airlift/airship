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
package com.proofpoint.galaxy.configuration;

import com.proofpoint.configuration.Config;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

public class GitConfigurationRepositoryConfig
{
    private String remoteUri;
    private String localConfigRepo = "git-config-repo";
    private Duration refreshInterval = new Duration(10, TimeUnit.SECONDS);

    public String getRemoteUri()
    {
        return remoteUri;
    }

    @Config("configuration-repository.git.uri")
    public void setRemoteUri(String remoteUri)
    {
        this.remoteUri = remoteUri;
    }

    @NotNull
    public String getLocalConfigRepo()
    {
        return localConfigRepo;
    }

    @Config("configuration-repository.git.local")
    public void setLocalConfigRepo(String localConfigRepo)
    {
        this.localConfigRepo = localConfigRepo;
    }

    public Duration getRefreshInterval()
    {
        return refreshInterval;
    }

    @Config("configuration-repository.git.refresh-interval")
    public GitConfigurationRepositoryConfig setRefreshInterval(Duration refreshInterval)
    {
        this.refreshInterval = refreshInterval;
        return this;
    }
}
