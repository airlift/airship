package io.airlift.airship.coordinator.auth.ssh;

public enum DerType
{
    INTEGER((byte) 0x02),
    SEQUENCE((byte) 0x30);

    private final byte value;

    DerType(byte value)
    {
        this.value = value;
    }

    public byte getValue()
    {
        return value;
    }
}
