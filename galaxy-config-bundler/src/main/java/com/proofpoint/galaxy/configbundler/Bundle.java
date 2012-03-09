package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;

public class Bundle
{
    private final String name;
    private final Integer version;

    public Bundle(String name, Integer version)
    {
        Preconditions.checkNotNull(name, "name is null");

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

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Bundle bundle = (Bundle) o;

        if (!name.equals(bundle.name)) {
            return false;
        }
        if (version != null ? !version.equals(bundle.version) : bundle.version != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = name.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }
}
