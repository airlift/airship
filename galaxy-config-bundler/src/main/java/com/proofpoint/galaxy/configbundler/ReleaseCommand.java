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

import static com.proofpoint.galaxy.configbundler.GitUtils.getEntries;
import static com.proofpoint.galaxy.configbundler.GitUtils.inputStreamSupplierFunction;
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
        Git git = Git.open(new File("."));
        Model model = new Model(git);
        
        Metadata metadata = model.readMetadata();
        Metadata.Repository releasesRepository = metadata.getReleasesRepository();
        String groupId = metadata.getGroupId();

        Preconditions.checkNotNull(releasesRepository, "Releases repository missing from .metadata file");
        Preconditions.checkNotNull(groupId, "GroupId missing from .metadata file");

        Preconditions.checkState(!model.isDirty(), "Cannot release with a dirty working tree");

        Bundle bundle;

        if (component == null) {
            bundle = model.getActiveBundle();
        }
        else {
            bundle = model.getBundle(component);
        }

        Maven maven = new Maven(metadata.getSnapshotsRepository(), metadata.getReleasesRepository());
        // TODO: handle errors getting uploader (e.g., repo does not exist, does not have credentials)

        if (!model.hasPendingChanges(bundle.getName())) {
            if (maven.contains(groupId, bundle.getName(), Integer.toString(bundle.getVersion()), ARTIFACT_TYPE)) {
                throw new RuntimeException(format("%s-%s has already been released", bundle.getName(), bundle.getVersion()));
            }

            // re-publish version that has already been tagged
        }
        else {
            bundle = model.createNewVersion(bundle);
        }

        // get entries from tag
        final Map<String,InputSupplier<InputStream>> entries = model.getEntries(bundle);

        if (entries.isEmpty()) {
            throw new RuntimeException("Cannot build an empty config package");
        }

        maven.upload(groupId, bundle.getName(), Integer.toString(bundle.getVersion()), ARTIFACT_TYPE, new BodyGenerator()
        {
            public void write(OutputStream out)
                    throws Exception
            {
                ZipPackager.packageEntries(out, entries);
            }
        });

        System.out.println(format("Uploaded %s-%s", bundle.getName(), bundle.getVersion()));

        return null;
    }


}
