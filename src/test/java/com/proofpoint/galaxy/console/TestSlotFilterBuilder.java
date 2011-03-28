package com.proofpoint.galaxy.console;

import com.google.common.base.Predicate;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.MockUriInfo;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.console.SlotFilterBuilder.BinarySpecPredicate;
import com.proofpoint.galaxy.console.SlotFilterBuilder.ConfigSpecPredicate;
import com.proofpoint.galaxy.console.SlotFilterBuilder.GlobPredicate;
import com.proofpoint.galaxy.console.SlotFilterBuilder.HostPredicate;
import com.proofpoint.galaxy.console.SlotFilterBuilder.IpPredicate;
import com.proofpoint.galaxy.console.SlotFilterBuilder.RegexPredicate;
import com.proofpoint.galaxy.console.SlotFilterBuilder.SlotNamePredicate;
import com.proofpoint.galaxy.console.SlotFilterBuilder.StatePredicate;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.proofpoint.galaxy.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.UNKNOWN;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestSlotFilterBuilder
{
    private final SlotStatus status = new SlotStatus(UUID.randomUUID(),
            "slotName",
            URI.create("fake://localhost"),
            UNKNOWN,
            APPLE_ASSIGNMENT);
    private final RemoteSlot slot = new HttpRemoteSlot(status, new AsyncHttpClient());

    private Predicate<RemoteSlot> buildFilter(String key, String value)
    {
        return SlotFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value));
    }

    @Test
    public void testEmptyFilter()
    {
        assertTrue(SlotFilterBuilder.build(MockUriInfo.from("fake://localhost")).apply(slot));
    }

    @Test
    public void testStateSpecPredicate()
    {
        assertTrue(new StatePredicate(UNKNOWN).apply(status));
        assertTrue(buildFilter("state", "unknown").apply(slot));
        assertTrue(buildFilter("state", "u").apply(slot));
        assertTrue(buildFilter("state", "UnKnown").apply(slot));
        assertTrue(buildFilter("state", "U").apply(slot));
        assertFalse(new StatePredicate(RUNNING).apply(status));
        assertFalse(buildFilter("state", "running").apply(slot));
        assertFalse(buildFilter("state", "r").apply(slot));
    }

    @Test
    public void testSlotNamePredicate()
    {
        assertTrue(new SlotNamePredicate("slotName").apply(status));
        assertTrue(buildFilter("name", "slotName").apply(slot));
        assertTrue(new SlotNamePredicate("SLOTNAME").apply(status));
        assertTrue(buildFilter("name", "SLOTNAME").apply(slot));
        assertTrue(new SlotNamePredicate("SlotName").apply(status));
        assertTrue(buildFilter("name", "SlotName").apply(slot));
        assertTrue(new SlotNamePredicate("slot*").apply(status));
        assertTrue(buildFilter("name", "slot*").apply(slot));
        assertTrue(new SlotNamePredicate("SLOT*").apply(status));
        assertTrue(buildFilter("name", "SLOT*").apply(slot));
        assertFalse(new SlotNamePredicate("foo").apply(status));
        assertFalse(buildFilter("name", "foo").apply(slot));
    }

    @Test
    public void testHostSpecPredicate()
    {
        assertTrue(new HostPredicate("localhost").apply(status));
        assertTrue(buildFilter("host", "localhost").apply(slot));
        assertTrue(new HostPredicate("LOCALHOST").apply(status));
        assertTrue(buildFilter("host", "LOCALHOST").apply(slot));
        assertTrue(new HostPredicate("LocalHost").apply(status));
        assertTrue(buildFilter("host", "LocalHost").apply(slot));
        assertTrue(new HostPredicate("local*").apply(status));
        assertTrue(buildFilter("host", "local*").apply(slot));
        assertTrue(new HostPredicate("LocAL*").apply(status));
        assertTrue(buildFilter("host", "LocAL*").apply(slot));
        assertFalse(new HostPredicate("foo").apply(status));
        assertFalse(buildFilter("host", "foo").apply(slot));
    }

    @Test
    public void testIpSpecPredicate()
    {
        assertTrue(new IpPredicate("127.0.0.1").apply(status));
        assertTrue(buildFilter("ip", "127.0.0.1").apply(slot));
        assertFalse(new IpPredicate("10.1.2.3").apply(status));
        assertFalse(buildFilter("ip", "10.1.2.3").apply(slot));
    }

    @Test
    public void testBinarySpecPredicate()
    {
        assertTrue(new BinarySpecPredicate("*:*:*").apply(status));
        assertTrue(buildFilter("binary", "*:*:*").apply(slot));
        assertTrue(new BinarySpecPredicate("food.fruit:apple:1.0").apply(status));
        assertTrue(buildFilter("binary", "food.fruit:apple:1.0").apply(slot));
        assertTrue(new BinarySpecPredicate("food.fruit:apple:tar.gz:1.0").apply(status));
        assertTrue(buildFilter("binary", "food.fruit:apple:tar.gz:1.0").apply(slot));
        assertTrue(new BinarySpecPredicate("*:apple:1.0").apply(status));
        assertTrue(buildFilter("binary", "*:apple:1.0").apply(slot));
        assertTrue(new BinarySpecPredicate("food.fruit:*:1.0").apply(status));
        assertTrue(buildFilter("binary", "food.fruit:*:1.0").apply(slot));
        assertTrue(new BinarySpecPredicate("food.fruit:apple:*").apply(status));
        assertTrue(buildFilter("binary", "food.fruit:apple:*").apply(slot));
        assertTrue(new BinarySpecPredicate("f*:a*:1.*").apply(status));
        assertTrue(buildFilter("binary", "f*:a*:1.*").apply(slot));
        assertFalse(new BinarySpecPredicate("x:apple:1.0").apply(status));
        assertFalse(buildFilter("binary", "x:apple:1.0").apply(slot));
        assertFalse(new BinarySpecPredicate("food.fruit:apple:zip:1.0").apply(status));
        assertFalse(buildFilter("binary", "food.fruit:apple:zip:1.0").apply(slot));
        assertFalse(new BinarySpecPredicate("food.fruit:apple:tar.gz:x:1.0").apply(status));
        assertFalse(buildFilter("binary", "food.fruit:apple:tar.gz:x:1.0").apply(slot));
    }

    @Test
    public void testConfigSpecPredicate()
    {
        assertTrue(new ConfigSpecPredicate("@*:*:*").apply(status));
        assertTrue(buildFilter("config", "@*:*:*").apply(slot));
        assertTrue(new ConfigSpecPredicate("@prod:apple:1.0").apply(status));
        assertTrue(buildFilter("config", "@prod:apple:1.0").apply(slot));
        assertTrue(new ConfigSpecPredicate("@*:apple:1.0").apply(status));
        assertTrue(buildFilter("config", "@*:apple:1.0").apply(slot));
        assertTrue(new ConfigSpecPredicate("@prod:*:1.0").apply(status));
        assertTrue(buildFilter("config", "@prod:*:1.0").apply(slot));
        assertTrue(new ConfigSpecPredicate("@prod:apple:*").apply(status));
        assertTrue(buildFilter("config", "@prod:apple:*").apply(slot));
        assertTrue(new ConfigSpecPredicate("@p*:a*:1.*").apply(status));
        assertTrue(buildFilter("config", "@p*:a*:1.*").apply(slot));
        assertFalse(new ConfigSpecPredicate("@x:apple:1.0").apply(status));
        assertFalse(buildFilter("config", "@x:apple:1.0").apply(slot));
        assertFalse(new ConfigSpecPredicate("@prod:apple:x:1.0").apply(status));
        assertFalse(buildFilter("config", "@prod:apple:x:1.0").apply(slot));
    }

    @Test
    public void testRegexPredicate()
    {
        RegexPredicate matchAllPredicate = new RegexPredicate(Pattern.compile(".*"));
        assertTrue(matchAllPredicate.apply("text"));
        assertTrue(matchAllPredicate.apply(""));
        assertFalse(matchAllPredicate.apply(null));

        RegexPredicate regexPredicate = new RegexPredicate(Pattern.compile("a+b*"));
        assertTrue(regexPredicate.apply("a"));
        assertTrue(regexPredicate.apply("ab"));
        assertFalse(regexPredicate.apply(null));
        assertFalse(regexPredicate.apply("x"));
        assertFalse(regexPredicate.apply("xab"));
        assertFalse(regexPredicate.apply("abX"));
        assertFalse(regexPredicate.apply("XabX"));
    }

    @Test
    public void testGlobPredicate()
    {
        GlobPredicate globPredicate = new GlobPredicate("*");
        assertTrue(globPredicate.apply("text"));
        assertTrue(globPredicate.apply(""));
        assertFalse(globPredicate.apply(null));

        globPredicate = new GlobPredicate("a*b*");
        assertTrue(globPredicate.apply("aXbX"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("x"));
        assertFalse(globPredicate.apply("xab"));

        globPredicate = new GlobPredicate("*.txt");
        assertTrue(globPredicate.apply("readme.txt"));
        assertTrue(globPredicate.apply(".txt"));
        assertTrue(globPredicate.apply(" .txt"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("txt"));
        assertFalse(globPredicate.apply("readme.txts"));

        globPredicate = new GlobPredicate("[abc].txt");
        assertTrue(globPredicate.apply("a.txt"));
        assertTrue(globPredicate.apply("b.txt"));
        assertTrue(globPredicate.apply("c.txt"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("x.txt"));
        assertFalse(globPredicate.apply("a.tt"));
        assertFalse(globPredicate.apply("aa.txt"));
        assertFalse(globPredicate.apply(" .txt"));

        globPredicate = new GlobPredicate("*.{txt,html}");
        assertTrue(globPredicate.apply("readme.txt"));
        assertTrue(globPredicate.apply("readme.html"));
        assertTrue(globPredicate.apply(".txt"));
        assertTrue(globPredicate.apply(".html"));
        assertTrue(globPredicate.apply(" .txt"));
        assertTrue(globPredicate.apply(" .html"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("txt"));
        assertFalse(globPredicate.apply("html"));
        assertFalse(globPredicate.apply("*.{txt,html}"));
        assertFalse(globPredicate.apply("readme.txthtml"));

    }
}
