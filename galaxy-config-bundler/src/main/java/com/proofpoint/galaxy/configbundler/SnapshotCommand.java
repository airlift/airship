package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.io.InputSupplier;
import com.proofpoint.http.client.BodyGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.iq80.cli.Arguments;
import org.iq80.cli.Command;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Iterables.find;
import static com.google.common.io.CharStreams.newReaderSupplier;
import static com.proofpoint.galaxy.configbundler.GitUtils.getEntries;
import static com.proofpoint.galaxy.configbundler.GitUtils.inputStreamSupplierFunction;
import static java.lang.String.format;

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
        Git git = Git.open(new File("."));

        Model model = new Model(git);
        Metadata metadata = model.readMetadata();

        String groupId = metadata.getGroupId();
        Preconditions.checkNotNull(groupId, "GroupId missing from metadata file");

        Preconditions.checkState(!model.isDirty(), "Cannot deploy with a dirty working tree");

        Bundle bundle;

        if (component == null) {
            bundle = model.getActiveBundle();
        }
        else {
            bundle = model.getBundle(component);
        }

        final Map<String, InputSupplier<InputStream>> entries = model.getEntries(bundle); // TODO: get pending changes

        if (entries.isEmpty()) {
            throw new RuntimeException("Cannot build an empty config package");
        }

        String versionString = bundle.getNextVersion() + "-SNAPSHOT";

        Maven maven = new Maven(metadata.getSnapshotsRepository(), metadata.getReleasesRepository());
        maven.upload(groupId, bundle.getName(), versionString, ReleaseCommand.ARTIFACT_TYPE, new BodyGenerator()
        {
            public void write(OutputStream out)
                    throws Exception
            {
                ZipPackager.packageEntries(out, entries);
            }
        });

        System.out.println(format("Uploaded %s-%s", bundle.getName(), versionString));

        return null;

    }
}
