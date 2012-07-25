package io.airlift.airship.agent;

import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.Installation;

import java.net.URI;
import java.util.UUID;

public interface Slot
{
    UUID getId();

    URI getSelf();

    URI getExternalUri();

    SlotStatus terminate();

    SlotStatus assign(Installation installation);

    SlotStatus getLastSlotStatus();

    SlotStatus status();

    SlotStatus start();

    SlotStatus restart();

    SlotStatus stop();
}
