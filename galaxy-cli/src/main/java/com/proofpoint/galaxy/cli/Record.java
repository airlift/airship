package com.proofpoint.galaxy.cli;

public interface Record
{
    String getValue(Column column);

    String getColorizedValue(Column column);
}
