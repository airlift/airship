package com.proofpoint.galaxy.configbundler;

import com.google.common.primitives.Ints;
import org.eclipse.jgit.revwalk.RevTag;

import java.util.Comparator;

class VersionTagComparator
        implements Comparator<RevTag>
{
    @Override
    public int compare(RevTag tag1, RevTag tag2)
    {
        return Ints.compare(ReleaseCommand.extractVersion(tag1), ReleaseCommand.extractVersion(tag2));
    }
}
