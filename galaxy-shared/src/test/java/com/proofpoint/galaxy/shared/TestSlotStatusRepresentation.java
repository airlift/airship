package com.proofpoint.galaxy.shared;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static org.testng.Assert.assertEquals;

public class TestSlotStatusRepresentation
{
    private final JsonCodec<SlotStatusRepresentation> codec = jsonCodec(SlotStatusRepresentation.class);

    private final SlotStatusRepresentation expected = new SlotStatusRepresentation(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "aaaaa",
            "slot1",
            URI.create("fake://apple"),
            "location/apple",
            AssignmentHelper.APPLE_ASSIGNMENT.getBinary().toString(),
            AssignmentHelper.APPLE_ASSIGNMENT.getConfig().toString(),
            STOPPED.toString(),
            "abc",
            null,
            "/apple",
            "Xfood.fruit:apple:1.0",
            "@Xapple:1.0",
            UNKNOWN.toString());

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        SlotStatusRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("slot-status.json"), Charsets.UTF_8);
        SlotStatusRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
