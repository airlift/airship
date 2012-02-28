package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.proofpoint.json.JsonCodec;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
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
import java.util.Map;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Iterables.find;
import static com.google.common.io.CharStreams.newReaderSupplier;
import static com.proofpoint.galaxy.configbundler.GitUtils.getBlob;

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

    public String getCurrentComponent()
            throws IOException
    {
        String currentBranch = git.getRepository().getRef(Constants.HEAD).getTarget().getName();
        if (currentBranch != null) {
            return Repository.shortenRefName(currentBranch);
        }

        return null;
    }

    public Ref getBranchForComponent(String component)
    {
        return find(git.branchList().call(), named(component), null);
    }

    public Integer getLatestVersionForComponent(String component)
    {
        Ref latestTag = getLatestTag(component);
        if (latestTag != null) {
            return extractVersion(latestTag.getName());
        }

        return null;
    }

    public Ref getLatestTag(String component)
    {
        Map<String, Ref> forComponent = Maps.filterKeys(git.getRepository().getTags(), startsWith(component + "-"));

        if (!forComponent.isEmpty()) {
            String latest = Ordering.from(new VersionTagComparator()).max(forComponent.keySet());
            return forComponent.get(latest);
        }

        return null;
    }

    public static int extractVersion(String tagName)
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
}
