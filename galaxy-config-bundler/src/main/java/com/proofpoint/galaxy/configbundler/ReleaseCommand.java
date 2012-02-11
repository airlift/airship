package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.io.InputSupplier;
import com.proofpoint.http.client.BodyGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.iq80.cli.Arguments;
import org.iq80.cli.Command;
import org.iq80.cli.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.find;
import static java.lang.String.format;


@Command(name = "release", description = "Build and release a config bundle")
public class ReleaseCommand
        implements Callable<Void>
{
    private static final String ARTIFACT_TYPE = "config";

    @Arguments
    public String component;

    @Option(name = "--repository", description = "Repository id")
    public String repositoryId;

    @Option(name = "--groupId")
    public String groupId;

    @Override
    public Void call()
            throws Exception
    {
        Metadata metadata = Metadata.load(new File(".metadata"));

        if (groupId == null) {
            groupId = metadata.getGroupId();
        }
        if (repositoryId == null) {
            repositoryId = metadata.getRepository();
        }

        Preconditions.checkNotNull(repositoryId, "Repository missing and no default repository configured");

        Git git = Git.open(new File("."));

        Status status = git.status().call();

        Preconditions.checkState(status.getAdded().isEmpty() &&
                status.getRemoved().isEmpty() &&
                status.getChanged().isEmpty() &&
                status.getMissing().isEmpty() &&
                status.getModified().isEmpty() &&
                status.getConflicting().isEmpty(),
                "Cannot release with a dirty working tree");

        final Repository repository = git.getRepository();
        if (component == null) {
            // infer component from current branch
            String currentBranch = repository.getRef(Constants.HEAD).getTarget().getName();
            Preconditions.checkState(!currentBranch.equals(Constants.HEAD), "Component is missing and no branch is currently checked (detached HEAD)");

            component = Repository.shortenRefName(currentBranch);
        }

        Ref branch = find(git.branchList().call(), named(component), null);
        Preconditions.checkArgument(branch != null, "There's not branch for component '%s'", component);

        RevCommit headCommit = new RevWalk(repository).parseCommit(branch.getObjectId());

        int version = 0;
        Ref latestTag = getLatestTag(repository, component);
        if (latestTag != null) {
            version = extractVersion(latestTag.getName());
        }

        MavenRepository mavenRepository = new Maven().getRepository(repositoryId);
        // TODO: handle errors getting uploader (e.g., repo does not exist, does not have credentials)

        if (latestTag != null && getCommit(repository, latestTag).equals(headCommit)) {
            // check if artifact exists in repo
            if (mavenRepository.contains(groupId, component, Integer.toString(version), ARTIFACT_TYPE)) {
                throw new RuntimeException(format("%s-%s has already been released", component, version));
            }
        }
        else {
            ++version;

            // tag <component>@HEAD with <component>-(<version> + 1)
            git.tag().setObjectId(headCommit)
                    .setName(component + "-" + version)
                    .call();
        }


        // get entries from tag
        final Map<String, ObjectId> entries = getEntries(repository, headCommit.getTree());

        URI uri = mavenRepository.upload(groupId, component, Integer.toString(version), ARTIFACT_TYPE, new BodyGenerator()
        {
            public void write(OutputStream out)
                    throws Exception
            {
                ZipPackager.packageEntries(out, Maps.transformValues(entries, getInputStreamSupplier(repository)));
            }
        });

        System.out.println(format("Uploaded %s to %s", component + "-" + version, uri));

        return null;
    }


    private RevCommit getCommit(Repository repository, Ref tag)
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

    private Ref getLatestTag(Repository repository, String component)
    {
        Map<String, Ref> forComponent = Maps.filterKeys(repository.getTags(), startsWith(component + "-"));
        String latest = Ordering.from(new VersionTagComparator()).max(forComponent.keySet());
        return forComponent.get(latest);
    }

    private Function<? super ObjectId, InputSupplier<InputStream>> getInputStreamSupplier(final Repository repository)
    {
        return new Function<ObjectId, InputSupplier<InputStream>>()
        {
            public InputSupplier<InputStream> apply(ObjectId input)
            {
                return getBlob(repository, input);
            }
        };
    }

    public Map<String, ObjectId> getEntries(Repository repository, RevTree tree)
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

    private InputSupplier<InputStream> getBlob(final Repository repository, final ObjectId objectId)
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

    private Predicate<? super Ref> named(final String component)
    {
        return new Predicate<Ref>()
        {
            public boolean apply(Ref input)
            {
                return Repository.shortenRefName(input.getName()).equals(component);
            }
        };
    }

    public static int extractVersion(String tagName)
    {
        int dash = tagName.lastIndexOf("-");
        Preconditions.checkArgument(dash != -1, "Tag does not follow <component>-<version> convention");

        return Integer.parseInt(tagName.substring(dash + 1));
    }

    private Predicate<String> startsWith(final String prefix)
    {
        return new Predicate<String>()
        {
            public boolean apply(String input)
            {
                return input.startsWith(prefix);
            }
        };
    }
}
