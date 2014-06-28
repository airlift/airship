package io.airlift.airship.configbundler;

import com.google.common.io.ByteSource;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.util.FS;

import java.util.Map;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Command(name = "snapshot", description = "Deploy a snapshot config bundle")
public class SnapshotCommand
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

        checkState(!model.isDirty(), "Cannot deploy snapshot with a dirty working tree");

        Bundle bundle;

        if (component == null) {
            bundle = model.getActiveBundle();
        }
        else {
            bundle = model.getBundle(component);
        }

        Maven maven = new Maven(metadata.getSnapshotsRepository(), metadata.getReleasesRepository());

        checkState(bundle.isSnapshot(), "There are no pending changes for bundle %s. Use released version %s:%s:%s instead",
                groupId, bundle.getName(), bundle.getName(), bundle.getVersionString());

        // get entries from tag
        final Map<String, ByteSource> entries = model.getEntries(bundle);

        if (entries.isEmpty()) {
            throw new RuntimeException("Cannot build an empty config package");
        }

        if (maven.upload(groupId, bundle.getName(), bundle.getVersionString(), ReleaseCommand.ARTIFACT_TYPE, new ZipPackager(entries))) {
            System.out.printf("Uploaded %s:%s-%s%n", groupId, bundle.getName(), bundle.getVersionString());
        }
        else {
            System.out.printf("Installed %s:%s-%s locally%n", groupId, bundle.getName(), bundle.getVersionString());
        }

        return null;
    }
}
