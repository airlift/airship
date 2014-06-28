package io.airlift.airship.configbundler;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.io.ByteSource;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static com.google.common.base.Objects.firstNonNull;

class GitUtils
{
    public static Ref getBranch(Repository repository, String name)
            throws IOException
    {
        return repository.getRefDatabase().getRefs(Constants.R_HEADS).get(name);
    }

    public static RevCommit getCommit(Repository repository, Ref ref)
    {
        RevWalk revWalk = new RevWalk(repository);
        try {
            return revWalk.parseCommit(firstNonNull(ref.getPeeledObjectId(), ref.getObjectId()));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
        finally {
            revWalk.release();
        }
    }

    public static ByteSource getBlob(final Repository repository, final ObjectId objectId)
    {
        Preconditions.checkArgument(repository.hasObject(objectId), "object id '%s' not found in git repository", objectId.getName());

        return new ByteSource()
        {
            @Override
            public InputStream openStream()
                    throws IOException
            {
                return repository.open(objectId).openStream();
            }
        };
    }

    public static Map<String, ObjectId> getEntries(Repository repository, RevTree tree)
            throws IOException
    {
        TreeWalk walk = new TreeWalk(repository);
        walk.addTree(tree);
        walk.setRecursive(true);
        walk.setFilter(new IgnoreHiddenFilter());

        ImmutableSortedMap.Builder<String, ObjectId> builder = ImmutableSortedMap.naturalOrder();
        while (walk.next()) {
            String path = walk.getTree(0, CanonicalTreeParser.class).getEntryPathString();
            ObjectId objectId = walk.getObjectId(0);
            builder.put(path, objectId);
        }

        return builder.build();
    }

    public static ObjectId findFileObject(Repository repository, RevCommit commit, String fileName)
            throws IOException
    {
        TreeWalk walk = new TreeWalk(repository);
        walk.setRecursive(true);
        walk.setFilter(PathFilterGroup.createFromStrings(fileName));
        walk.addTree(commit.getTree());

        if (walk.next()) {
            return walk.getObjectId(0);
        }

        return null;
    }

    public static Function<? super ObjectId, ByteSource> byteSourceFunction(final Repository repository)
    {
        return new Function<ObjectId, ByteSource>()
        {
            public ByteSource apply(ObjectId input)
            {
                return getBlob(repository, input);
            }
        };
    }
}
