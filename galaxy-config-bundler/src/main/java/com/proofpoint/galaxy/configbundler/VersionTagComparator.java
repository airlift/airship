package com.proofpoint.galaxy.configbundler;

import com.google.common.primitives.Ints;

import java.util.Comparator;

import static com.proofpoint.galaxy.configbundler.Model.extractVersion;

class VersionTagComparator
        implements Comparator<String>
{
    @Override
    public int compare(String tag1, String tag2)
    {
        return Ints.compare(extractVersion(tag1), extractVersion(tag2));
    }
}
