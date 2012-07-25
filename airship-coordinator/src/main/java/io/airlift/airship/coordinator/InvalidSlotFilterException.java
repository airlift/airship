package com.proofpoint.galaxy.coordinator;

public class InvalidSlotFilterException extends RuntimeException
{
    public InvalidSlotFilterException()
    {
        super("A filter is required");
    }
}
