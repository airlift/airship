package com.proofpoint.galaxy.standalone;

public interface Record
{
    String getValue(Column column);

    String getColorizedValue(Column column);
}
