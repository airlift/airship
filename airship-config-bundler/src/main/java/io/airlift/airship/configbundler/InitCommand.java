package io.airlift.airship.configbundler;

import io.airlift.airline.Command;
import io.airlift.airline.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.File;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkState;

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

        checkState(!exists, "A git repository already exists in the current directory");

        Model model = new Model(Git.init().call());

        if (releasesRepositoryId != null) {
            checkState(releasesRepositoryUri != null, "releaseRepositoryId requires releaseRepositoryUri!");

            // If the same id is given for release repository and snapshot repository, then the snapshot uri can
            // be empty (in that case, the release repository uri is used).
            if (releasesRepositoryId.equals(snapshotsRepositoryId) && snapshotsRepositoryUri == null) {
                snapshotsRepositoryUri = releasesRepositoryUri;
            }
        }
        else {
            System.out.println("No release repository id given! Releases can only be used locally!");
        }

        if (snapshotsRepositoryId != null) {
            checkState(snapshotsRepositoryId != null, "snapshotsRepositoryId requires snapshotsRepositoryUri!");
        }
        else {
            System.out.println("No snapshot repository id given! Snapshots can only be used locally!");
        }

        Metadata metadata = new Metadata(groupId,
                Metadata.Repository.getRepository(snapshotsRepositoryId, snapshotsRepositoryUri),
                Metadata.Repository.getRepository(releasesRepositoryId, releasesRepositoryUri));

        model.initialize(metadata);
        model.checkoutTemplateBranch();

        return null;
    }
}
