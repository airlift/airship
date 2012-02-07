package com.proofpoint.galaxy.cli;

import org.iq80.cli.Option;

import static com.google.common.collect.Lists.newArrayList;
import static org.iq80.cli.OptionType.GLOBAL;

public class GlobalOptions
{
    @Option(type = GLOBAL, name = {"-e", "--environment"}, description = "Galaxy environment")
    public String environment;

    @Option(type = GLOBAL, name = "--debug", description = "Enable debug messages")
    public boolean debug = false;

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("GlobalOptions");
        sb.append("{environment=").append(environment);
        sb.append(", debug=").append(debug);
        sb.append('}');
        return sb.toString();
    }
}
