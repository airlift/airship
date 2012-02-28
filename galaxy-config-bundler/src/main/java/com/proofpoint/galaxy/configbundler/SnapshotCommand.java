package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
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

        String component = fromNullable(this.component).or(model.getCurrentComponent());
        Ref branch = model.getBranchForComponent(component);

        Integer version = model.getLatestVersionForComponent(component);
        if (version == null) {
            version = 0;
        }
        ++version;

        Maven maven = new Maven(metadata.getSnapshotsRepository(), metadata.getReleasesRepository());

        // get entries from tag
        final Repository repository = git.getRepository();
        RevCommit headCommit = new RevWalk(repository).parseCommit(branch.getObjectId());

        final Map<String, ObjectId> entries = getEntries(repository, headCommit.getTree());

        if (entries.isEmpty()) {
            throw new RuntimeException("Cannot build an empty config package");
        }

        String versionString = version + "-SNAPSHOT";
        maven.upload(groupId, component, versionString, ReleaseCommand.ARTIFACT_TYPE, new BodyGenerator()
        {
            public void write(OutputStream out)
                    throws Exception
            {
                ZipPackager.packageEntries(out, Maps.transformValues(entries, inputStreamSupplierFunction(repository)));
            }
        });

        System.out.println(format("Uploaded %s-%s", component, versionString));

        return null;

    }
}
