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

import com.google.common.base.Preconditions;
import com.proofpoint.configuration.Config;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ConfigurationRepositoryConfig
{
    private URI coordinatorBaseURI;

    @NotNull
    public URI getCoordinatorBaseURI()
    {
        return coordinatorBaseURI;
    }

    @Config("configuration-repository.coordinator-uri")
    public ConfigurationRepositoryConfig setCoordinatorBaseURI(URI coordinatorBaseURI)
    {
        this.coordinatorBaseURI = coordinatorBaseURI;
        return this;
    }
}
