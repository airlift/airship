package com.proofpoint.galaxy.standalone;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.collect.Maps.newLinkedHashMap;

public class SimpleRecord implements Record
{
    public static Builder builder()
    {
        return new Builder();
    }

    private final Map<Column, String> values;
    private final Map<Column, String> colorizedValues;

    SimpleRecord(Map<Column, String> values, Map<Column, String> colorizedValues)
    {
        this.values = ImmutableMap.copyOf(values);
        this.colorizedValues = ImmutableMap.copyOf(colorizedValues);
    }

    @Override
    public String getValue(Column column)
    {
        return values.get(column);
    }

    @Override
    public String getColorizedValue(Column column)
    {
        return colorizedValues.get(column);
    }

    public static class Builder
    {
        private final Map<Column, String> values = newLinkedHashMap();
        private final Map<Column, String> colorizedValues = newLinkedHashMap();

        public Builder addValue(Column name, Object value)
        {
            String stringValue = value == null ? "" : value.toString();
            values.put(name, stringValue);
            colorizedValues.put(name, stringValue);
            return this;
        }

        public SimpleRecord build()
        {
            return new SimpleRecord(values, colorizedValues);
        }
    }
}
