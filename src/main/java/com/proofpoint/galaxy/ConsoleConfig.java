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

import com.proofpoint.configuration.Config;

import javax.validation.constraints.NotNull;

public class ConsoleConfig
{
    private String binaryRepoBase;
    private String configRepoBase;

    @NotNull
    public String getBinaryRepoBase()
    {
        return binaryRepoBase;
    }

    @Config("console.binary-repo")
    public ConsoleConfig setBinaryRepoBase(String binaryRepoBase)
    {
        this.binaryRepoBase = binaryRepoBase;
        return this;
    }

    @NotNull
    public String getConfigRepoBase()
    {
        return configRepoBase;
    }

    @Config("console.config-repo")
    public ConsoleConfig setConfigRepoBase(String configRepoBase)
    {
        this.configRepoBase = configRepoBase;
        return this;
    }
}
