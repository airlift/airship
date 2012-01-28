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

import static com.proofpoint.galaxy.shared.BinarySpec.createBinarySpec;
import static com.proofpoint.galaxy.shared.ConfigSpec.DEFAULT_CONFIG_PACKAGING;
import static com.proofpoint.galaxy.shared.ConfigSpec.createConfigSpec;
import static com.proofpoint.galaxy.shared.ConfigSpec.parseConfigSpec;
import static com.proofpoint.galaxy.shared.ConfigSpec.toConfigGAV;
import static java.util.Arrays.asList;

public class TestConfigSpec
{
    @Test
    public void fullConfigSpec()
    {
        MavenCoordinates spec = parseConfigSpec("@component-pool:version");
        Assert.assertNull(spec.getGroupId());
        Assert.assertEquals(spec.getArtifactId(), "component-pool");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, createConfigSpec("component-pool", "version"));
        Assert.assertEquals(spec, createBinarySpec(null, "component-pool", "version", DEFAULT_CONFIG_PACKAGING, null));
        Assert.assertEquals(spec.toString(), "component-pool:config:version");
        Assert.assertEquals(toConfigGAV(spec), "@component-pool:version");
    }

    @Test
    public void simpleSpec()
    {
        MavenCoordinates spec = parseConfigSpec("@component:version");
        Assert.assertNull(spec.getGroupId());
        Assert.assertEquals(spec.getArtifactId(), "component");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, createConfigSpec("component", "version"));
        Assert.assertEquals(spec, createBinarySpec(null, "component", "version", DEFAULT_CONFIG_PACKAGING, null));
        Assert.assertEquals(spec.toString(), "component:config:version");
        Assert.assertEquals(toConfigGAV(spec), "@component:version");
    }

    @Test
    public void snapshotSpec()
    {
        MavenCoordinates spec = createConfigSpec(null, "component", "2.0-SNAPSHOT", "2.0-12345678.123456-1");
        Assert.assertNull(spec.getGroupId());
        Assert.assertEquals(spec.getArtifactId(), "component");
        Assert.assertEquals(spec.getVersion(), "2.0-SNAPSHOT");
        Assert.assertEquals(spec.getFileVersion(), "2.0-12345678.123456-1");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, createConfigSpec(null, "component", "2.0-SNAPSHOT", "2.0-12345678.123456-1"));
        Assert.assertEquals(spec, createBinarySpec(null, "component", "2.0-SNAPSHOT", DEFAULT_CONFIG_PACKAGING, null, "2.0-12345678.123456-1"));
        Assert.assertEquals(spec.toString(), "component:config:2.0-SNAPSHOT(2.0-12345678.123456-1)");
        Assert.assertEquals(toConfigGAV(spec), "@component:2.0-12345678.123456-1");
    }

    @Test
    public void fullSpec()
    {
        MavenCoordinates spec = createConfigSpec("group", "component", "2.0-SNAPSHOT", "2.0-12345678.123456-1");
        Assert.assertEquals(spec.getGroupId(), "group");
        Assert.assertEquals(spec.getArtifactId(), "component");
        Assert.assertEquals(spec.getVersion(), "2.0-SNAPSHOT");
        Assert.assertEquals(spec.getFileVersion(), "2.0-12345678.123456-1");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, createConfigSpec("group", "component", "2.0-SNAPSHOT", "2.0-12345678.123456-1"));
        Assert.assertEquals(spec, createBinarySpec("group", "component", "2.0-SNAPSHOT", DEFAULT_CONFIG_PACKAGING, null, "2.0-12345678.123456-1"));
        Assert.assertEquals(spec.toString(), "group:component:config:2.0-SNAPSHOT(2.0-12345678.123456-1)");
        Assert.assertEquals(toConfigGAV(spec), "@group:component:2.0-12345678.123456-1");
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(parseConfigSpec("@component:version"), createConfigSpec("component", "version"), createConfigSpec("component", "version")),
                asList(parseConfigSpec("@component-pool:version"), createConfigSpec("component-pool", "version")));
    }
}
