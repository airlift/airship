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
import static java.lang.String.format;

@Command(name = "release", description = "Build and release a config bundle")
public class ReleaseCommand
        implements Callable<Void>
{
    public static final String ARTIFACT_TYPE = "config";

    @Arguments
    public String component;

    @Override
    public Void call()
            throws Exception
    {
        Model model = new Model(Git.wrap(new RepositoryBuilder().findGitDir().setFS(FS.DETECTED).build()));
        Metadata metadata = model.readMetadata();

        String groupId = checkNotNull(metadata.getGroupId(), "GroupId missing from .metadata file");

        checkState(!model.isDirty(), "Cannot deploy release with a dirty working tree");

        Bundle bundle;

        if (component == null) {
            bundle = model.getActiveBundle();
        }
        else {
            bundle = model.getBundle(component);
        }

        Maven maven = new Maven(metadata.getSnapshotsRepository(), metadata.getReleasesRepository());
        // TODO: handle errors getting uploader (e.g., repo does not exist, does not have credentials)

        if (!bundle.isSnapshot()) {
            if (maven.contains(groupId, bundle.getName(), bundle.getVersionString(), ARTIFACT_TYPE)) {
                throw new RuntimeException(format("%s:%s-%s has already been released", groupId, bundle.getName(), bundle.getVersionString()));
            }

            // re-publish version that has already been tagged
        }
        else {
            bundle = model.createNewVersion(bundle);
        }

        // get entries from tag
        final Map<String, ByteSource> entries = model.getEntries(bundle);

        if (entries.isEmpty()) {
            throw new RuntimeException("Cannot build an empty config package");
        }

        if (maven.upload(groupId, bundle.getName(), bundle.getVersionString(), ARTIFACT_TYPE, new ZipPackager(entries))) {
            System.out.printf("Uploaded %s:%s:%s%n", groupId, bundle.getName(), bundle.getVersionString());
        }
        else {
            System.out.printf("Installed %s:%s:%s locally%n", groupId, bundle.getName(), bundle.getVersionString());
        }

        return null;
    }
}
