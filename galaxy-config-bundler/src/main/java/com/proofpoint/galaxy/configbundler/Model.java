package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.common.primitives.Ints;
import com.proofpoint.json.JsonCodec;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidTagNameException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Map;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.io.CharStreams.newReaderSupplier;
import static com.proofpoint.galaxy.configbundler.GitUtils.getBlob;
import static com.proofpoint.galaxy.configbundler.GitUtils.inputStreamSupplierFunction;
import static java.lang.String.format;

public class Model
{
    private static final String TEMPLATE_BRANCH = "template";
    private static final String METADATA_BRANCH = "master";
    private static final String METADATA_FILE = ".metadata";

    private final Git git;

    public Model(Git git)
    {
        this.git = git;
    }

    public Metadata readMetadata()
            throws IOException
    {
        Repository repository = git.getRepository();

        Ref branch = repository.getRef(METADATA_BRANCH);
        RevCommit head = new RevWalk(repository).parseCommit(branch.getObjectId());

        ObjectId objectId = GitUtils.findFileObject(repository, head, METADATA_FILE);

        String json = CharStreams.toString(newReaderSupplier(getBlob(repository, objectId), UTF_8));
        Metadata metadata = JsonCodec.jsonCodec(Metadata.class).fromJson(json);

        Preconditions.checkNotNull(metadata, ".metadata file not found in master branch");

        return metadata;
    }

    public void initialize(Metadata metadata)
            throws GitAPIException, IOException
    {
        // create primordial commit
        Files.write("", new File(".gitignore"), UTF_8);
        git.add().addFilepattern(".gitignore").call();
        RevCommit commit = git.commit().setMessage("Initialize").call();

        // mark commit as the template for new components
        git.branchCreate()
                .setName(TEMPLATE_BRANCH)
                .setStartPoint(commit)
                .call();

        git.checkout().setName(METADATA_BRANCH).call();

        metadata.save(new File(METADATA_FILE));
        git.add().addFilepattern(METADATA_FILE).call();

        git.commit().setMessage("Update metadata").call();
    }

    
    public Bundle getActiveBundle()
            throws IOException
    {
        String currentBranch = git.getRepository().getRef(Constants.HEAD).getTarget().getName();
        if (currentBranch != null) {
            String name = Repository.shortenRefName(currentBranch);
            Integer version = getLatestVersionForComponent(name);
            return new Bundle(name, version);
        }

        return null;
    }
    
    public Bundle getBundle(String name)
    {
        Ref branch = getBranchForComponent(name);
        if (branch != null) {
            Integer version = getLatestVersionForComponent(name);
            return new Bundle(name, version);
        }

        return null;

    }
    
    public Map<String, InputSupplier<InputStream>> getEntries(Bundle bundle)
            throws IOException
    {
        Ref ref;
        if (bundle.getVersion() == null) {
            ref = getBranchForComponent(bundle.getName());
        }
        else {
            ref = git.getRepository().getTags().get(bundle.getName() + "-" + bundle.getVersion());
            Preconditions.checkNotNull(ref, "cannot find tag for bundle %s:%s", bundle.getName(), bundle.getVersion());
        }

        RevCommit commit = GitUtils.getCommit(git.getRepository(), ref);
        
        return Maps.transformValues(GitUtils.getEntries(git.getRepository(), commit.getTree()), inputStreamSupplierFunction(git.getRepository()));
    }
    
    public Bundle createNewVersion(Bundle bundle)
            throws NoHeadException, ConcurrentRefUpdateException, InvalidTagNameException
    {
        Integer version = bundle.getVersion();
        
        Integer latestVersion = getLatestVersionForComponent(bundle.getName());

        if ((version == null || latestVersion == null) && version != latestVersion ||
                version != null && !version.equals(latestVersion)) {
            throw new IllegalArgumentException(format("Bundle specifier (%s:%s) is stale. Latest version is %s:%s", bundle.getName(), version, bundle.getName(), latestVersion));
        }

        version = bundle.getNextVersion();

        RevCommit commit = GitUtils.getCommit(git.getRepository(), getBranchForComponent(bundle.getName()));
        git.tag().setObjectId(commit)
                .setName(bundle.getName() + "-" + version)
                .call();
        
        return new Bundle(bundle.getName(), version);
    }

    public boolean hasPendingChanges(String bundleName)
    {
        Ref latestTag = getLatestTag(bundleName);

        final Repository repository = git.getRepository();
        
        Ref branch = getBranchForComponent(bundleName);
        RevCommit headCommit = GitUtils.getCommit(repository, branch);

        return (latestTag == null || !GitUtils.getCommit(git.getRepository(), latestTag).equals(headCommit));
    }
    
    private Ref getBranchForComponent(String component)
    {
        return find(git.branchList().call(), named(component), null);
    }

    private Integer getLatestVersionForComponent(String component)
    {
        Ref latestTag = getLatestTag(component);
        if (latestTag != null) {
            return extractVersion(latestTag.getName());
        }

        return null;
    }

    private Ref getLatestTag(String component)
    {
        Map<String, Ref> forComponent = Maps.filterKeys(git.getRepository().getTags(), startsWith(component + "-"));

        if (!forComponent.isEmpty()) {
            String latest = Ordering.from(new VersionTagComparator()).max(forComponent.keySet());
            return forComponent.get(latest);
        }

        return null;
    }

    private static int extractVersion(String tagName)
    {
        int dash = tagName.lastIndexOf("-");
        Preconditions.checkArgument(dash != -1, "Tag does not follow <component>-<version> convention");

        return Integer.parseInt(tagName.substring(dash + 1));
    }

    private static Predicate<String> startsWith(final String prefix)
    {
        return new Predicate<String>()
        {
            public boolean apply(String input)
            {
                return input.startsWith(prefix);
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

    public void checkoutTemplateBranch()
            throws GitAPIException
    {
        git.checkout()
                .setName(TEMPLATE_BRANCH)
                .call();
    }

    public boolean isDirty()
            throws IOException
    {
        Status status = git.status().call();

        return !(status.getAdded().isEmpty() &&
                status.getRemoved().isEmpty() &&
                status.getChanged().isEmpty() &&
                status.getMissing().isEmpty() &&
                status.getModified().isEmpty() &&
                status.getConflicting().isEmpty());
    }

    static class VersionTagComparator
            implements Comparator<String>
    {
        @Override
        public int compare(String tag1, String tag2)
        {
            return Ints.compare(extractVersion(tag1), extractVersion(tag2));
        }
    }

}
