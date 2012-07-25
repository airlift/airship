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
package io.airlift.airship.shared;

import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

import static io.airlift.airship.shared.MavenCoordinates.fromConfigGAV;
import static io.airlift.airship.shared.MavenCoordinates.toConfigGAV;

public class TestConfigSpec
{
    @Test
    public void fullConfigSpec()
    {
        MavenCoordinates spec = fromConfigGAV("@group:component:version");
        Assert.assertEquals(spec.getGroupId(), "group");
        Assert.assertEquals(spec.getArtifactId(), "component");
        Assert.assertEquals(spec.getPackaging(), "config");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(toConfigGAV(spec), "@group:component:version");
        Assert.assertEquals(spec, new MavenCoordinates("group", "component", "version", "config", null, null));
        Assert.assertEquals(spec.toString(), "group:component:config:version");
        Assert.assertEquals(toConfigGAV(spec), "@group:component:version");
    }

    @Test
    public void simpleSpec()
    {
        MavenCoordinates spec = fromConfigGAV("@component:version");
        Assert.assertNull(spec.getGroupId());
        Assert.assertEquals(spec.getArtifactId(), "component");
        Assert.assertEquals(spec.getPackaging(), "config");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates(null, "component", "version", "config", null, null));
        Assert.assertEquals(spec.toString(), "component:config:version");
        Assert.assertEquals(toConfigGAV(spec), "@component:version");
    }

    @Test
    public void fullSpec()
    {
        MavenCoordinates spec = fromConfigGAV("@group:component:packaging:classifier:2.0-SNAPSHOT");
        Assert.assertEquals(spec.getGroupId(), "group");
        Assert.assertEquals(spec.getArtifactId(), "component");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertEquals(spec.getClassifier(), "classifier");
        Assert.assertEquals(spec.getVersion(), "2.0-SNAPSHOT");
        Assert.assertEquals(spec.getFileVersion(), "2.0-SNAPSHOT");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("group", "component", "2.0-SNAPSHOT", "packaging", "classifier", null));
        Assert.assertEquals(spec.toString(), "group:component:packaging:classifier:2.0-SNAPSHOT");
        Assert.assertEquals(toConfigGAV(spec), "@group:component:packaging:classifier:2.0-SNAPSHOT");
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.equivalenceTester()
                .addEquivalentGroup(
                        fromConfigGAV("@component:version"),
                        new MavenCoordinates(null, "component", "version", "config", null, null))
                .addEquivalentGroup(
                        fromConfigGAV("@group:component:version"),
                        new MavenCoordinates("group", "component", "version", "config", null, null))
                .check();
    }
}
