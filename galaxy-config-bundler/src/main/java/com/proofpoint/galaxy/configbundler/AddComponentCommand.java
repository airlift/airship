package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.iq80.cli.Arguments;
import org.iq80.cli.Command;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "add", description = "Add config for a component")
public class AddComponentCommand
    implements Callable<Void>
{
    @Arguments(required = true)
    public String component;

    @Override
    public Void call()
            throws Exception
    {
        Preconditions.checkNotNull(component, "component is null");

        Git git = Git.open(new File("."));
        Repository repository = git.getRepository();

        Preconditions.checkState(repository.getRef(component) == null, "Branch for component %s already exists", component);

        Ref templateBranch = repository.getRef("template");

        Preconditions.checkNotNull(templateBranch, "'template' branch not found");

        RevCommit templateCommit = GitUtils.getCommit(repository, templateBranch);

        git.branchCreate()
                .setName(component)
                .setStartPoint(templateCommit)
                .call();

        git.checkout().setName(component).call();

        return null;
    }
}
