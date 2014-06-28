package io.airlift.airship.configbundler;

import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
        checkNotNull(component, "component is null");

        Git git = Git.open(new File("."));

        Model model = new Model(git);

        checkArgument(model.getBundle(component) == null, "Component already exists: %s", component);

        Bundle bundle = model.createBundle(component);
        model.activateBundle(bundle);

        return null;
    }
}
