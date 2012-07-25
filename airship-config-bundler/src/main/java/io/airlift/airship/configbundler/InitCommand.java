package io.airlift.airship.configbundler;

import com.google.common.base.Preconditions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.iq80.cli.Command;
import org.iq80.cli.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "init", description = "Initialize a git config repository")
public class InitCommand
        implements Callable<Void>
{
    @Option(name = "--groupId", description = "Maven group id")
    public String groupId;

    @Option(name = "--releasesRepository", description = "Default maven releases repository id")
    public String releasesRepositoryId;

    @Option(name = "--releasesRepositoryUri", description = "Default maven releases repository URI")
    public String releasesRepositoryUri;

    @Option(name = "--snapshotsRepository", description = "Default maven releases repository id")
    public String snapshotsRepositoryId;

    @Option(name = "--snapshotsRepositoryUri", description = "Default maven snapshots repository URI")
    public String snapshotsRepositoryUri;

    @Override
    public Void call()
            throws Exception
    {
        // 1. If git repo does not exist, create it
        // 2. If master branch does not exist, create it from current branch
        // 3. If template branch does not exist, create it from current branch
        // 4. If metadata file does not exist in master branch, add it. Otherwise update it
        // 5. If template branch was created in this run, check it out

        boolean exists = true;
        try {
            Git.open(new File("."));
        }
        catch (RepositoryNotFoundException e) {
            exists = false;
        }

        Preconditions.checkState(!exists, "A git repository already exists in the current directory");

        Model model = new Model(Git.init().call());

        Metadata metadata = new Metadata(groupId,
                new Metadata.Repository(snapshotsRepositoryId, snapshotsRepositoryUri),
                new Metadata.Repository(releasesRepositoryId, releasesRepositoryUri));

        model.initialize(metadata);
        model.checkoutTemplateBranch();

        return null;
    }
}
