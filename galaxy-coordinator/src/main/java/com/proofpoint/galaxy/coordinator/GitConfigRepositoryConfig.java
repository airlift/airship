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

import com.proofpoint.configuration.Config;

import javax.validation.constraints.NotNull;

public class GitConfigRepositoryConfig
{
    private String remoteUri;
    private String localConfigRepo = "git-config-repo";

    public String getRemoteUri()
    {
        return remoteUri;
    }

    @Config("coordinator.git-config-repo.uri")
    public void setRemoteUri(String remoteUri)
    {
        this.remoteUri = remoteUri;
    }

    @NotNull
    public String getLocalConfigRepo()
    {
        return localConfigRepo;
    }

    @Config("coordinator.git-config-repo.local")
    public void setLocalConfigRepo(String localConfigRepo)
    {
        this.localConfigRepo = localConfigRepo;
    }

}
