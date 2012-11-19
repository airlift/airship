package io.airlift.airship.cli;

import io.airlift.command.Option;

import static io.airlift.command.OptionType.GLOBAL;

public class GlobalOptions
{
    @Option(type = GLOBAL, name = {"-e", "--environment"}, description = "Airship environment")
    public String environment;

    @Option(type = GLOBAL, name = "--batch", description = "Do not prompt")
    public boolean batch;

    @Option(type = GLOBAL, name = "--debug", description = "Enable debug messages")
    public boolean debug = false;

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("GlobalOptions");
        sb.append("{environment=").append(environment);
        sb.append(", batch=").append(batch);
        sb.append(", debug=").append(debug);
        sb.append('}');
        return sb.toString();
    }
}
