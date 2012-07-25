package com.proofpoint.galaxy.shared;

import com.proofpoint.json.JsonCodec;
import org.testng.annotations.Test;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static org.testng.Assert.assertEquals;

public class TestUpgradeVersion
{
    private final JsonCodec<UpgradeVersions> codec = jsonCodec(UpgradeVersions.class);

    private final UpgradeVersions expected = new UpgradeVersions("1.1", "2.2");

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        UpgradeVersions actual = codec.fromJson(json);
        assertUpgradeVersionsEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        String json = "{\"binaryVersion\":\"1.1\",\"configVersion\":\"2.2\"}";
        UpgradeVersions actual = codec.fromJson(json);

        assertUpgradeVersionsEquals(actual, expected);
    }

    private void assertUpgradeVersionsEquals(UpgradeVersions actual, UpgradeVersions expected)
    {
        assertEquals(actual.getBinaryVersion(), expected.getBinaryVersion());
        assertEquals(actual.getConfigVersion(), this.expected.getConfigVersion());
    }
}
