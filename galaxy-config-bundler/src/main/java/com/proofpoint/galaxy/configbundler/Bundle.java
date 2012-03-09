package com.proofpoint.galaxy.configbundler;

public class Bundle
{
    private final String name;
    private final Integer version;

    public Bundle(String name, Integer version)
    {
        this.name = name;
        this.version = version;
    }

    public String getName()
    {
        return name;
    }

    public Integer getVersion()
    {
        return version;
    }
    
    public int getNextVersion()
    {
        if (version == null) {
            return 1;
        }

        return version + 1;
    }
}
