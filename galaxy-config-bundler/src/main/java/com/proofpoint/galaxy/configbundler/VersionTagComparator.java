package com.proofpoint.galaxy.configbundler;

import com.google.common.primitives.Ints;
import org.eclipse.jgit.revwalk.RevTag;

import java.util.Comparator;

class VersionTagComparator
        implements Comparator<String>
{
    @Override
    public int compare(String tag1, String tag2)
    {
        return Ints.compare(ReleaseCommand.extractVersion(tag1), ReleaseCommand.extractVersion(tag2));
    }
}
