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
package io.airlift.airship.coordinator;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.LegacyConfig;
import io.airlift.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CoordinatorConfig
{
    public static final String DEFAULT_HTTP_SHORT_NAME_PATTERN = "([^\\/]+?)(?:-[0-9][0-9.]*(?:-SNAPSHOT)?)?(?:\\.config)?$";

    private Duration statusExpiration = new Duration(5, TimeUnit.SECONDS);

    private String serviceInventoryCacheDir = "service-inventory-cache";

    private boolean allowDuplicateInstallationsOnAnAgent;

    private List<String> repositories = ImmutableList.of();
    private List<String> defaultRepositoryGroupId = ImmutableList.of();

    private String httpShortNamePattern = DEFAULT_HTTP_SHORT_NAME_PATTERN;
    private String httpRepoBinaryVersionPattern;
    private String httpRepoConfigVersionPattern;
    private String s3RepoBucket;
    private String s3RepoKeyPrefix;

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
    public String getServiceInventoryCacheDir()
    {
        return serviceInventoryCacheDir;
    }

    @Config("coordinator.service-inventory.cache-dir")
    public CoordinatorConfig setServiceInventoryCacheDir(String serviceInventoryCacheDir)
    {
        this.serviceInventoryCacheDir = serviceInventoryCacheDir;
        return this;
    }

    @NotNull
    public List<String> getRepositories()
    {
        return repositories;
    }

    @Config("coordinator.allow-duplicate-installations-on-an-agent")
    @ConfigDescription("Default config for provisioned agents")
    public CoordinatorConfig setAllowDuplicateInstallationsOnAnAgent(boolean allowDuplicateInstallationsOnAnAgent)
    {
        this.allowDuplicateInstallationsOnAnAgent = allowDuplicateInstallationsOnAnAgent;
        return this;
    }

    public boolean isAllowDuplicateInstallationsOnAnAgent()
    {
        return allowDuplicateInstallationsOnAnAgent;
    }

    @Config("coordinator.repository")
    @LegacyConfig("coordinator.binary-repo")
    public CoordinatorConfig setRepositories(String repositories)
    {
        this.repositories = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(repositories));
        return this;
    }

    public CoordinatorConfig setRepositories(Iterable<String> repositories)
    {
        this.repositories = ImmutableList.copyOf(repositories);
        return this;
    }

    public List<String> getDefaultRepositoryGroupId()
    {
        return defaultRepositoryGroupId;
    }

    @Config("coordinator.default-group-id")
    public CoordinatorConfig setDefaultRepositoryGroupId(String defaultRepositoryGroupId)
    {
        this.defaultRepositoryGroupId = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(defaultRepositoryGroupId));
        return this;
    }

    public CoordinatorConfig setDefaultRepositoryGroupId(List<String> defaultRepositoryGroupId)
    {
        this.defaultRepositoryGroupId = ImmutableList.copyOf(defaultRepositoryGroupId);
        return this;
    }

    public String getHttpShortNamePattern()
    {
        return httpShortNamePattern;
    }

    @Config("coordinator.http-repo.short-name-pattern")
    public CoordinatorConfig setHttpShortNamePattern(String httpShortNamePattern)
    {
        this.httpShortNamePattern = httpShortNamePattern;
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

    @Config("coordinator.s3-repo.bucket")
    public CoordinatorConfig setS3RepoBucket(String s3RepoBucket)
    {
        this.s3RepoBucket = s3RepoBucket;
        return this;
    }

    public String getS3RepoBucket()
    {
        return s3RepoBucket;
    }

    @Config("coordinator.s3-repo.keyPrefix")
    public CoordinatorConfig setS3RepoKeyPrefix(String s3RepoKeyPrefix)
    {
        this.s3RepoKeyPrefix = s3RepoKeyPrefix;
        return this;
    }

    public String getS3RepoKeyPrefix()
    {
        return s3RepoKeyPrefix;
    }
}
