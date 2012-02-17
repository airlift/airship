package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Throwables;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

import static com.google.common.base.Objects.firstNonNull;

public class GitUtils
{
    public static RevCommit getCommit(Repository repository, Ref tag)
    {
        RevWalk revWalk = new RevWalk(repository);
        try {
            return revWalk.parseCommit(firstNonNull(tag.getPeeledObjectId(), tag.getObjectId()));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
        finally {
            revWalk.release();
        }
    }
}
