package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.io.InputSupplier;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
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

public class GitUtils
{
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

    public static InputSupplier<InputStream> getBlob(final Repository repository, final ObjectId objectId)
    {
        Preconditions.checkArgument(repository.hasObject(objectId), "object id '%s' not found in git repository", objectId.getName());

        return new InputSupplier<InputStream>()
        {
            @Override
            public InputStream getInput()
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

    public static Function<? super ObjectId, InputSupplier<InputStream>> inputStreamSupplierFunction(final Repository repository)
    {
        return new Function<ObjectId, InputSupplier<InputStream>>()
        {
            public InputSupplier<InputStream> apply(ObjectId input)
            {
                return getBlob(repository, input);
            }
        };
    }

}
