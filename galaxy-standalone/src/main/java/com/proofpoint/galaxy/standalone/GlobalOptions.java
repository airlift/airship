package com.proofpoint.galaxy.standalone;

import com.google.common.base.Objects;
import org.iq80.cli.Option;
import org.iq80.cli.OptionType;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.iq80.cli.OptionType.GLOBAL;

public class GlobalOptions
{
    @Option(type = GLOBAL, name = "--environment", description = "Galaxy environment")
    public String environment;

    @Option(type = GLOBAL, name = "--coordinator", description = "Galaxy coordinator host (overrides GALAXY_COORDINATOR)")
    public String coordinator = Objects.firstNonNull(System.getenv("GALAXY_COORDINATOR"), "http://localhost:64000");

    @Option(type = GLOBAL, name = "--repository", description = "Repository to use in standalone mode")
    public final List<String> repository = newArrayList();

    @Option(type = GLOBAL, name = "--debug", description = "Enable debug messages")
    public boolean debug = false;

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("GlobalOptions");
        sb.append("{environment=").append(environment);
        sb.append(", coordinator='").append(coordinator).append('\'');
        sb.append(", debug=").append(debug);
        sb.append('}');
        return sb.toString();
    }
}
