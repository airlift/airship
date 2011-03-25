package com.proofpoint.galaxy;

import com.google.common.base.Predicate;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.SlotFilterBuilder.BinarySpecPredicate;
import com.proofpoint.galaxy.SlotFilterBuilder.ConfigSpecPredicate;
import com.proofpoint.galaxy.SlotFilterBuilder.GlobPredicate;
import com.proofpoint.galaxy.SlotFilterBuilder.HostPredicate;
import com.proofpoint.galaxy.SlotFilterBuilder.IpPredicate;
import com.proofpoint.galaxy.SlotFilterBuilder.RegexPredicate;
import com.proofpoint.galaxy.SlotFilterBuilder.SetPredicate;
import com.proofpoint.galaxy.SlotFilterBuilder.StatePredicate;
import junit.framework.TestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.UNKNOWN;

public class TestSlotFilterBuilder extends TestCase
{
    private final SlotStatus status = new SlotStatus(UUID.randomUUID(),
            "test",
            URI.create("fake://localhost"),
            BinarySpec.valueOf("fruit:apple:1.0"),
            ConfigSpec.valueOf("@prod:apple:1.0"),
            UNKNOWN);
    private final Slot slot = new HttpRemoteSlot(status, new AsyncHttpClient());

    private Predicate<Slot> buildFilter(String key, String value)
    {
        return SlotFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value));
    }

    @Test
    public void testEmptyFilter()
    {
        Assert.assertTrue(SlotFilterBuilder.build(MockUriInfo.from("fake://localhost")).apply(slot));
    }

    @Test
    public void testStateSpecPredicate()
    {
        Assert.assertTrue(new StatePredicate(UNKNOWN).apply(status));
        Assert.assertTrue(buildFilter("state", "unknown").apply(slot));
        Assert.assertTrue(buildFilter("state", "u").apply(slot));
        Assert.assertTrue(buildFilter("state", "UnKnown").apply(slot));
        Assert.assertTrue(buildFilter("state", "U").apply(slot));
        Assert.assertFalse(new StatePredicate(RUNNING).apply(status));
        Assert.assertFalse(buildFilter("state", "running").apply(slot));
        Assert.assertFalse(buildFilter("state", "r").apply(slot));
    }

    @Test
    public void testSetSpecPredicate()
    {
        Assert.assertTrue(new SetPredicate(SlotSet.ALL).apply(status));
        Assert.assertTrue(buildFilter("set", "all").apply(slot));
        Assert.assertTrue(buildFilter("set", "AlL").apply(slot));
        Assert.assertTrue(buildFilter("set", "a").apply(slot));
        Assert.assertTrue(buildFilter("set", "A").apply(slot));
        Assert.assertTrue(new SetPredicate(SlotSet.TAKEN).apply(status));
        Assert.assertTrue(buildFilter("set", "taken").apply(slot));
        Assert.assertTrue(buildFilter("set", "TakeN").apply(slot));
        Assert.assertTrue(buildFilter("set", "t").apply(slot));
        Assert.assertTrue(buildFilter("set", "T").apply(slot));
        Assert.assertFalse(new SetPredicate(SlotSet.EMPTY).apply(status));
        Assert.assertFalse(buildFilter("set", "empty").apply(slot));
        Assert.assertFalse(buildFilter("set", "EmPty").apply(slot));
        Assert.assertFalse(buildFilter("set", "e").apply(slot));
        Assert.assertFalse(buildFilter("set", "E").apply(slot));
    }

    @Test
    public void testHostSpecPredicate()
    {
        Assert.assertTrue(new HostPredicate("localhost").apply(status));
        Assert.assertTrue(buildFilter("host", "localhost").apply(slot));
        Assert.assertTrue(new HostPredicate("LOCALHOST").apply(status));
        Assert.assertTrue(buildFilter("host", "LOCALHOST").apply(slot));
        Assert.assertTrue(new HostPredicate("LocalHost").apply(status));
        Assert.assertTrue(buildFilter("host", "LocalHost").apply(slot));
        Assert.assertTrue(new HostPredicate("local*").apply(status));
        Assert.assertTrue(buildFilter("host", "local*").apply(slot));
        Assert.assertTrue(new HostPredicate("LocAL*").apply(status));
        Assert.assertTrue(buildFilter("host", "LocAL*").apply(slot));
        Assert.assertFalse(new HostPredicate("foo").apply(status));
        Assert.assertFalse(buildFilter("host", "foo").apply(slot));
    }

    @Test
    public void testIpSpecPredicate()
    {
        Assert.assertTrue(new IpPredicate("127.0.0.1").apply(status));
        Assert.assertTrue(buildFilter("ip", "127.0.0.1").apply(slot));
        Assert.assertFalse(new IpPredicate("10.1.2.3").apply(status));
        Assert.assertFalse(buildFilter("ip", "10.1.2.3").apply(slot));
    }

    @Test
    public void testBinarySpecPredicate()
    {
        Assert.assertTrue(new BinarySpecPredicate("*:*:*").apply(status));
        Assert.assertTrue(buildFilter("binary", "*:*:*").apply(slot));
        Assert.assertTrue(new BinarySpecPredicate("fruit:apple:1.0").apply(status));
        Assert.assertTrue(buildFilter("binary", "fruit:apple:1.0").apply(slot));
        Assert.assertTrue(new BinarySpecPredicate("fruit:apple:tar.gz:1.0").apply(status));
        Assert.assertTrue(buildFilter("binary", "fruit:apple:tar.gz:1.0").apply(slot));
        Assert.assertTrue(new BinarySpecPredicate("*:apple:1.0").apply(status));
        Assert.assertTrue(buildFilter("binary", "*:apple:1.0").apply(slot));
        Assert.assertTrue(new BinarySpecPredicate("fruit:*:1.0").apply(status));
        Assert.assertTrue(buildFilter("binary", "fruit:*:1.0").apply(slot));
        Assert.assertTrue(new BinarySpecPredicate("fruit:apple:*").apply(status));
        Assert.assertTrue(buildFilter("binary", "fruit:apple:*").apply(slot));
        Assert.assertTrue(new BinarySpecPredicate("f*:a*:1.*").apply(status));
        Assert.assertTrue(buildFilter("binary", "f*:a*:1.*").apply(slot));
        Assert.assertFalse(new BinarySpecPredicate("x:apple:1.0").apply(status));
        Assert.assertFalse(buildFilter("binary", "x:apple:1.0").apply(slot));
        Assert.assertFalse(new BinarySpecPredicate("fruit:apple:zip:1.0").apply(status));
        Assert.assertFalse(buildFilter("binary", "fruit:apple:zip:1.0").apply(slot));
        Assert.assertFalse(new BinarySpecPredicate("fruit:apple:tar.gz:x:1.0").apply(status));
        Assert.assertFalse(buildFilter("binary", "fruit:apple:tar.gz:x:1.0").apply(slot));
    }

    @Test
    public void testConfigSpecPredicate()
    {
        Assert.assertTrue(new ConfigSpecPredicate("@*:*:*").apply(status));
        Assert.assertTrue(buildFilter("config", "@*:*:*").apply(slot));
        Assert.assertTrue(new ConfigSpecPredicate("@prod:apple:1.0").apply(status));
        Assert.assertTrue(buildFilter("config", "@prod:apple:1.0").apply(slot));
        Assert.assertTrue(new ConfigSpecPredicate("@*:apple:1.0").apply(status));
        Assert.assertTrue(buildFilter("config", "@*:apple:1.0").apply(slot));
        Assert.assertTrue(new ConfigSpecPredicate("@prod:*:1.0").apply(status));
        Assert.assertTrue(buildFilter("config", "@prod:*:1.0").apply(slot));
        Assert.assertTrue(new ConfigSpecPredicate("@prod:apple:*").apply(status));
        Assert.assertTrue(buildFilter("config", "@prod:apple:*").apply(slot));
        Assert.assertTrue(new ConfigSpecPredicate("@p*:a*:1.*").apply(status));
        Assert.assertTrue(buildFilter("config", "@p*:a*:1.*").apply(slot));
        Assert.assertFalse(new ConfigSpecPredicate("@x:apple:1.0").apply(status));
        Assert.assertFalse(buildFilter("config", "@x:apple:1.0").apply(slot));
        Assert.assertFalse(new ConfigSpecPredicate("@prod:apple:x:1.0").apply(status));
        Assert.assertFalse(buildFilter("config", "@prod:apple:x:1.0").apply(slot));
    }

    @Test
    public void testRegexPredicate()
    {
        RegexPredicate matchAllPredicate = new RegexPredicate(Pattern.compile(".*"));
        Assert.assertTrue(matchAllPredicate.apply("text"));
        Assert.assertTrue(matchAllPredicate.apply(""));
        Assert.assertFalse(matchAllPredicate.apply(null));

        RegexPredicate regexPredicate = new RegexPredicate(Pattern.compile("a+b*"));
        Assert.assertTrue(regexPredicate.apply("a"));
        Assert.assertTrue(regexPredicate.apply("ab"));
        Assert.assertFalse(regexPredicate.apply(null));
        Assert.assertFalse(regexPredicate.apply("x"));
        Assert.assertFalse(regexPredicate.apply("xab"));
        Assert.assertFalse(regexPredicate.apply("abX"));
        Assert.assertFalse(regexPredicate.apply("XabX"));
    }

    @Test
    public void testGlobPredicate()
    {
        GlobPredicate globPredicate = new GlobPredicate("*");
        Assert.assertTrue(globPredicate.apply("text"));
        Assert.assertTrue(globPredicate.apply(""));
        Assert.assertFalse(globPredicate.apply(null));

        globPredicate = new GlobPredicate("a*b*");
        Assert.assertTrue(globPredicate.apply("aXbX"));
        Assert.assertFalse(globPredicate.apply(null));
        Assert.assertFalse(globPredicate.apply("x"));
        Assert.assertFalse(globPredicate.apply("xab"));

        globPredicate = new GlobPredicate("*.txt");
        Assert.assertTrue(globPredicate.apply("readme.txt"));
        Assert.assertTrue(globPredicate.apply(".txt"));
        Assert.assertTrue(globPredicate.apply(" .txt"));
        Assert.assertFalse(globPredicate.apply(null));
        Assert.assertFalse(globPredicate.apply("txt"));
        Assert.assertFalse(globPredicate.apply("readme.txts"));

        globPredicate = new GlobPredicate("[abc].txt");
        Assert.assertTrue(globPredicate.apply("a.txt"));
        Assert.assertTrue(globPredicate.apply("b.txt"));
        Assert.assertTrue(globPredicate.apply("c.txt"));
        Assert.assertFalse(globPredicate.apply(null));
        Assert.assertFalse(globPredicate.apply("x.txt"));
        Assert.assertFalse(globPredicate.apply("a.tt"));
        Assert.assertFalse(globPredicate.apply("aa.txt"));
        Assert.assertFalse(globPredicate.apply(" .txt"));

        globPredicate = new GlobPredicate("*.{txt,html}");
        Assert.assertTrue(globPredicate.apply("readme.txt"));
        Assert.assertTrue(globPredicate.apply("readme.html"));
        Assert.assertTrue(globPredicate.apply(".txt"));
        Assert.assertTrue(globPredicate.apply(".html"));
        Assert.assertTrue(globPredicate.apply(" .txt"));
        Assert.assertTrue(globPredicate.apply(" .html"));
        Assert.assertFalse(globPredicate.apply(null));
        Assert.assertFalse(globPredicate.apply("txt"));
        Assert.assertFalse(globPredicate.apply("html"));
        Assert.assertFalse(globPredicate.apply("*.{txt,html}"));
        Assert.assertFalse(globPredicate.apply("readme.txthtml"));

    }
}
