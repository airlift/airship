package io.airlift.airship.configbundler;

import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.IOException;

class IgnoreHiddenFilter
    extends TreeFilter
{
    @Override
    public boolean include(TreeWalk walker)
            throws IOException
    {
        return !walker.getNameString().startsWith(".");
    }

    @Override
    public boolean shouldBeRecursive()
    {
        return false;
    }

    @Override
    public TreeFilter clone()
    {
        return this;
    }

}
