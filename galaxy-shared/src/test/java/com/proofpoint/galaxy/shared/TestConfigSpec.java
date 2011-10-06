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

import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

public class TestConfigSpec
{
    @Test
    public void fullConfigSpec()
    {
        ConfigSpec spec = ConfigSpec.valueOf("@component:pool:version");
        Assert.assertEquals(spec.getComponent(), "component");
        Assert.assertEquals(spec.getPool(), "pool");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new ConfigSpec("component", "version", "pool"));
        Assert.assertEquals(spec.toString(), "@component:pool:version");
    }

    @Test
    public void simpleSpec()
    {
        ConfigSpec spec = ConfigSpec.valueOf("@component:version");
        Assert.assertEquals(spec.getComponent(), "component");
        Assert.assertNull(spec.getPool());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new ConfigSpec("component", "version"));
        Assert.assertEquals(spec.toString(), "@component:version");
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(ConfigSpec.valueOf("@component:version"), new ConfigSpec("component", "version"), new ConfigSpec("component", "version", null)),
                asList(ConfigSpec.valueOf("@component:pool:version"), new ConfigSpec("component", "version", "pool")));
    }
}
