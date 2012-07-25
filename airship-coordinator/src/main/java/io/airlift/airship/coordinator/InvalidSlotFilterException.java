package io.airlift.airship.coordinator;

public class InvalidSlotFilterException extends RuntimeException
{
    public InvalidSlotFilterException()
    {
        super("A filter is required");
    }
}
