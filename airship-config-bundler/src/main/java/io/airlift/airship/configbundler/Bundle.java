package io.airlift.airship.configbundler;

import com.google.common.base.Preconditions;

class Bundle
{
    private final String name;
    private final int version;
    private final boolean snapshot;

    public Bundle(String name, int version, boolean snapshot)
    {
        Preconditions.checkNotNull(name, "name is null");

        this.name = name;
        this.version = version;
        this.snapshot = snapshot;
    }

    public String getName()
    {
        return name;
    }

    public int getVersion()
    {
        return version;
    }

    public String getVersionString()
    {
        if (snapshot) {
            return version + "-SNAPSHOT";
        }

        return Integer.toString(version);
    }

    public boolean isSnapshot()
    {
        return snapshot;
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

        if (snapshot != bundle.snapshot) {
            return false;
        }
        if (version != bundle.version) {
            return false;
        }
        if (!name.equals(bundle.name)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = name.hashCode();
        result = 31 * result + version;
        result = 31 * result + (snapshot ? 1 : 0);
        return result;
    }
}
