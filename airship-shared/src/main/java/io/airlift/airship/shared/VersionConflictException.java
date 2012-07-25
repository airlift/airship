package com.proofpoint.galaxy.shared;

public class VersionConflictException extends RuntimeException
{
    private final String name;
    private final String version;

    public VersionConflictException(String name, String version)
    {
        super("The state of this environment has changed. Please, retry this operation.");
        this.name = name;
        this.version = version;
    }

    public String getName()
    {
        return name;
    }

    public String getVersion()
    {
        return version;
    }
}
