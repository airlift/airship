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
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CoordinatorConfig
{
    private String galaxyVersion;
    private Duration statusExpiration = new Duration(30, TimeUnit.SECONDS);

    private List<String> mavenRepoBases = ImmutableList.of();
    private String defaultRepositoryGroupId;

    private List<String> httpRepoBases = ImmutableList.of();
    private String httpRepoBinaryVersionPattern;
    private String httpRepoConfigVersionPattern;

    @NotNull
    public String getGalaxyVersion()
    {
        return galaxyVersion;
    }

    @Config("galaxy.version")
    public CoordinatorConfig setGalaxyVersion(String galaxyVersion)
    {
        this.galaxyVersion = galaxyVersion;
        return this;
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

    @NotNull
    public List<String> getMavenRepoBases()
    {
        return mavenRepoBases;
    }

    @Config("coordinator.maven-repo")
    @LegacyConfig("coordinator.binary-repo")
    public CoordinatorConfig setMavenRepoBases(String mavenRepoBases)
    {
        this.mavenRepoBases = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(mavenRepoBases));
        return this;
    }

    @NotNull
    public String getDefaultRepositoryGroupId()
    {
        return defaultRepositoryGroupId;
    }

    @Config("coordinator.default-group-id")
    public CoordinatorConfig setDefaultRepositoryGroupId(String defaultRepositoryGroupId)
    {
        this.defaultRepositoryGroupId = defaultRepositoryGroupId;
        return this;
    }

    @NotNull
    public List<String> getHttpRepoBases()
    {
        return httpRepoBases;
    }

    @Config("coordinator.http-repo")
    public CoordinatorConfig setHttpRepoBases(String httpRepoBases)
    {
        this.httpRepoBases = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(httpRepoBases));
        return this;
    }

    public String getHttpRepoBinaryVersionPattern()
    {
        return httpRepoBinaryVersionPattern;
    }

    @Config("coordinator.http-repo.binary-version-pattern")
    public CoordinatorConfig setHttpRepoBinaryVersionPattern(String httpRepoBinaryVersionPattern)
    {
        this.httpRepoBinaryVersionPattern = httpRepoBinaryVersionPattern;
        return this;
    }

    public String getHttpRepoConfigVersionPattern()
    {
        return httpRepoConfigVersionPattern;
    }

    @Config("coordinator.http-repo.config-version-pattern")
    public CoordinatorConfig setHttpRepoConfigVersionPattern(String httpRepoConfigVersionPattern)
    {
        this.httpRepoConfigVersionPattern = httpRepoConfigVersionPattern;
        return this;
    }
}
