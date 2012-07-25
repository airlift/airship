package io.airlift.airship.cli;

public interface Record
{
    String getValue(Column column);

    String getColorizedValue(Column column);
}
