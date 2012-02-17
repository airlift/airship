package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.iq80.cli.Command;
import org.iq80.cli.Option;

import java.io.File;
import java.util.concurrent.Callable;

import static com.google.common.base.Charsets.UTF_8;

@Command(name = "init", description = "Initialize a git config repository")
public class InitCommand
        implements Callable<Void>
{
    @Option(name = "--groupId", description = "Maven group id", required = true)
    public String groupId;

    @Option(name = "--repository", description = "Default maven repository id")
    public String repositoryId;

    @Override
    public Void call()
            throws Exception
    {
        boolean exists = true;
        try {
            Git.open(new File("."));
        }
        catch (RepositoryNotFoundException e) {
            exists = false;
        }

        Preconditions.checkState(!exists, "A git repository already exists in the current directory");
        Preconditions.checkNotNull(groupId, "Group id is required");

        Git git = Git.init().call();

        // create primordial commit
        Files.write("", new File(".gitignore"), UTF_8);
        git.add().addFilepattern(".gitignore")
                .call();
        RevCommit commit = git.commit().setMessage("Initialize")
                .call();

        // keep metadata in master branch
        Metadata metadata = new Metadata(groupId, repositoryId);
        metadata.save(new File(".metadata"));

        git.add().addFilepattern(".metadata")
                .call();
        git.commit().setMessage("Initialize metadata")
                .call();

        git.commit().setMessage(format("Initialize with groupId '%s'", groupId))
                .call();

        return null;
    }
}
