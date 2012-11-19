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

import io.airlift.bootstrap.Bootstrap;
import io.airlift.discovery.client.DiscoveryModule;
import io.airlift.event.client.HttpEventModule;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.jmx.JmxModule;
import io.airlift.json.JsonModule;
import io.airlift.log.Logger;
import io.airlift.node.NodeModule;
import org.weakref.jmx.guice.MBeanModule;

import static io.airlift.airship.coordinator.ConditionalModule.installIfPropertyEquals;


public class CoordinatorMain
{
    private static final Logger log = Logger.get(CoordinatorMain.class);

    public static void main(String[] args)
            throws Exception
    {
        try {
            Bootstrap app = new Bootstrap(
                    new NodeModule(),
                    new HttpServerModule(),
                    new HttpEventModule(),
                    new DiscoveryModule(),
                    new JsonModule(),
                    new JaxrsModule(),
                    new MBeanModule(),
                    new JmxModule(),
                    new CoordinatorMainModule(),
                    installIfPropertyEquals(new FixedProvisionerModule(), "coordinator.provisioner", "local"),
                    installIfPropertyEquals(new StaticProvisionerModule(), "coordinator.provisioner", "static"),
                    installIfPropertyEquals(new AwsProvisionerModule(), "coordinator.provisioner", "aws"));

            app.strictConfig().initialize();
        }
        catch (Throwable e) {
            log.error(e, "Startup failed");
            System.exit(1);
        }
    }
}
