package io.airlift.airship.configbundler;

import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.util.FS;

import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

@Command(name = "info", description = "Show metadata ")
public class InfoCommand
        implements Callable<Void>
{
    @Arguments
    public String component;

    @Override
    public Void call()
            throws Exception
    {
        Model model = new Model(Git.wrap(new RepositoryBuilder().findGitDir().setFS(FS.DETECTED).build()));
        Metadata metadata = model.readMetadata();

        String groupId = checkNotNull(metadata.getGroupId(), "GroupId missing from .metadata file");

        System.out.printf("Metadata Group Id:       %s%n", metadata.getGroupId());

        if (metadata.getReleasesRepository() == null) {
            System.out.println("No releases repository configured, only local install possible.");
        }
        else {
            System.out.printf("Release Repository Id:   %s%n", metadata.getReleasesRepository().getId());
            System.out.printf("Release Repository URL:  %s%n", metadata.getReleasesRepository().getUri());
        }

        if (metadata.getSnapshotsRepository() == null) {
            System.out.println("No snapshots repository configured, only local install possible.");
        }
        else {
            System.out.printf("Snapshot Repository Id:  %s%n", metadata.getSnapshotsRepository().getId());
            System.out.printf("Snapshot Repository URL: %s%n", metadata.getSnapshotsRepository().getUri());
        }

        if (model.isDirty()) {
            System.out.println("Local repository is modified.");
        }

        return null;
    }
}