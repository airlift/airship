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
package com.proofpoint.galaxy.shared;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Immutable
public class ConfigSpec
{
    public static final String CONFIG_SPEC_REGEX = "^@([^:]+):([^:]+)(?::([^:]+))?:([^:]+)$";
    private static final Pattern CONFIG_SPEC_PATTERN = Pattern.compile(CONFIG_SPEC_REGEX);
    private final String environment;
    private final String component;
    private final String pool;
    private final String version;

    public static ConfigSpec valueOf(String configSpec)
    {
        Matcher matcher = CONFIG_SPEC_PATTERN.matcher(configSpec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid config spec: " + configSpec);
        }
        String environment = matcher.group(1);
        String component = matcher.group(2);
        String pool = matcher.group(3);
        String version = matcher.group(4);
        return new ConfigSpec(environment, component, version, pool);
    }

    public ConfigSpec(String environment, String component, String version)
    {
        this(environment, component, version, null);
    }

    public ConfigSpec(String environment, String component, String version, String pool)
    {
        Preconditions.checkNotNull(environment, "groupId is null");
        Preconditions.checkNotNull(component, "artifactId is null");
        Preconditions.checkNotNull(version, "version is null");

        this.environment = environment;
        this.component = component;
        this.pool = pool;
        this.version = version;
    }

    public String getEnvironment()
    {
        return environment;
    }

    public String getComponent()
    {
        return component;
    }

    public String getPool()
    {
        return pool;
    }

    public String getVersion()
    {
        return version;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConfigSpec that = (ConfigSpec) o;

        if (!component.equals(that.component)) {
            return false;
        }
        if (pool != null ? !pool.equals(that.pool) : that.pool != null) {
            return false;
        }
        if (!environment.equals(that.environment)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = environment.hashCode();
        result = 31 * result + component.hashCode();
        result = 31 * result + (pool != null ? pool.hashCode() : 0);
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append('@');
        sb.append(environment).append(':');
        sb.append(component).append(':');
        if (pool != null) {
            sb.append(pool).append(':');
        }
        sb.append(version);
        return sb.toString();
    }
}
