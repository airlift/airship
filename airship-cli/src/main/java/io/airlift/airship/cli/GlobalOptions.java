package io.airlift.airship.cli;

import org.iq80.cli.Option;
import org.iq80.cli.OptionType;

import static com.google.common.collect.Lists.newArrayList;
import static org.iq80.cli.OptionType.GLOBAL;

public class GlobalOptions
{
    @Option(type = GLOBAL, name = {"-e", "--environment"}, description = "Airship environment")
    public String environment;

    @Option(type = OptionType.GLOBAL, name = "--batch", description = "Do not prompt")
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
