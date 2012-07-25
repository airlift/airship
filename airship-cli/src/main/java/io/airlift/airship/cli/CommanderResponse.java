package io.airlift.airship.cli;

public class CommanderResponse<T>
{
    public static <T> CommanderResponse<T> createCommanderResponse(String version, T value)
    {
        return new CommanderResponse<T>(version, value);
    }

    private final String version;
    private final T value;

    private CommanderResponse(String version, T value)
    {
        this.version = version;
        this.value = value;
    }

    public String getVersion()
    {
        return version;
    }

    public T getValue()
    {
        return value;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("CommanderResponse");
        sb.append("{version='").append(version).append('\'');
        sb.append(", value=").append(value);
        sb.append('}');
        return sb.toString();
    }
}
