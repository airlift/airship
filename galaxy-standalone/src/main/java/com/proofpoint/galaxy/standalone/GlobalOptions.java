package com.proofpoint.galaxy.standalone;

import com.google.common.base.Objects;
import org.iq80.cli.Option;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class GlobalOptions
{
    @Option(options = "--environment", description = "Galaxy environment")
    public String environment;

    @Option(options = "--coordinator", description = "Galaxy coordinator host (overrides GALAXY_COORDINATOR)")
    public String coordinator = Objects.firstNonNull(System.getenv("GALAXY_COORDINATOR"), "http://localhost:64000");

    @Option(options = "--binary-repository", description = "Binary repository to use in standalone mode")
    public final List<String> binaryRepository = newArrayList();

    @Option(options = "--config-repository", description = "Configuration repository to use in standalone mode")
    public final List<String> configRepository = newArrayList();

    @Option(options = "--debug", description = "Enable debug messages")
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
