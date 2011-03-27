package com.proofpoint.galaxy;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;

public class TestSlotStatusRepresentation
{
    private final JsonCodec<SlotStatusRepresentation> codec = new JsonCodecBuilder().build(SlotStatusRepresentation.class);

    private final SlotStatusRepresentation expected = new SlotStatusRepresentation(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "slot1",
            URI.create("fake://apple"),
            AssignmentHelper.APPLE_ASSIGNMENT.getBinary().toString(),
            AssignmentHelper.APPLE_ASSIGNMENT.getConfig().toString(),
            STOPPED.toString());

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
