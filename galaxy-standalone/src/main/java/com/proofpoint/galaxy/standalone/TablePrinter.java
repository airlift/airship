package com.proofpoint.galaxy.standalone;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.Maps.newLinkedHashMap;

public class TablePrinter
{
    private final List<Column> columns;
    private final Record headerRecord;
    private final String columnSeparator;

    public TablePrinter(Column... columns)
    {
        this("  ", ImmutableList.copyOf(columns));
    }

    public TablePrinter(String columnSeparator, Iterable<Column> columns)
    {
        Preconditions.checkNotNull(columnSeparator, "columnSeparator is null");
        Preconditions.checkNotNull(columns, "headers is null");

        this.columnSeparator = columnSeparator;
        this.columns = ImmutableList.copyOf(columns);
        SimpleRecord.Builder builder = SimpleRecord.builder();
        for (Column column : columns) {
            builder = builder.addValue(column, column.getHeader());
        }
        this.headerRecord = builder.build();
    }

    public void print(Iterable<Record> records)
    {
        Map<Column, Integer> columns = newLinkedHashMap();

        for (Column column : this.columns) {
            int columnSize = column.getHeader().length();
            for (Record record : records) {
                String value = record.getValue(column);
                if (value != null) {
                    columnSize = Math.max(value.length(), columnSize);
                }
            }
            columns.put(column, columnSize);
        }

        for (Record header : Iterables.concat(ImmutableList.of(headerRecord), records)) {
            boolean first = true;
            for (Entry<Column, Integer> column : columns.entrySet()) {
                if (!first) {
                    System.out.print(columnSeparator);
                }
                first = false;

                Column columnName = column.getKey();
                int columnSize = column.getValue();

                String value = Objects.firstNonNull(header.getValue(columnName), "");
                String colorizedValue = Objects.firstNonNull(header.getColorizedValue(columnName), "");

                System.out.print(colorizedValue);
                System.out.print(spaces(columnSize - value.length()));
            }
            System.out.println();
        }
    }

    private static String spaces(int count)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(" ");
        }
        return result.toString();
    }

}
