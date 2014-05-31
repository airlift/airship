package io.airlift.airship.coordinator;

import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.SlotStatus;

import java.util.UUID;

public interface RemoteSlot
{
    UUID getId();

    SlotStatus terminate();

    SlotStatus assign(Installation installation);

    SlotStatus status();

    SlotStatus start();

    SlotStatus restart();

    SlotStatus stop();

    SlotStatus kill();
}
