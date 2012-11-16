package io.airlift.airship.shared;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;

public class TestSlotStatusRepresentation
{
    private final JsonCodec<SlotStatusRepresentation> codec = jsonCodec(SlotStatusRepresentation.class);

    private final SlotStatusRepresentation expected = new SlotStatusRepresentation(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "aaaaa",
            URI.create("internal://apple"),
            URI.create("external://apple"),
            "instance",
            "/test/location/apple",
            "/location/apple",
            AssignmentHelper.APPLE_ASSIGNMENT.getBinary(),
            AssignmentHelper.APPLE_ASSIGNMENT.getBinary(),
            AssignmentHelper.APPLE_ASSIGNMENT.getConfig(),
            AssignmentHelper.APPLE_ASSIGNMENT.getConfig(),
            STOPPED.toString(),
            "abc",
            null,
            "/apple",
            ImmutableMap.of("memory", 512),
            "food.fruit:apple:1.0",
            "@prod:apple:1.0",
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
