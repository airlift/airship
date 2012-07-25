package com.proofpoint.galaxy.cli;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.Files;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class Config implements Iterable<Entry<String, Collection<String>>>
{
    private final LinkedListMultimap<String, String> configuration;
    private final File file;

    public static Config loadConfig(File file)
            throws IOException
    {
        LinkedListMultimap<String, String> config = LinkedListMultimap.create();

        if (file.exists()) {
            Splitter commentSplitter = Splitter.on('#').limit(2);
            Splitter keyValueSplitter = Splitter.on('=').trimResults().limit(2);
            for (String line : Files.readLines(file, Charsets.UTF_8)) {
                line = Iterables.getFirst(commentSplitter.split(line), "");
                List<String> keyValue = ImmutableList.copyOf(keyValueSplitter.split(line));
                if (keyValue.size() != 2) {
                    continue;
                }
                config.put(keyValue.get(0), keyValue.get(1));
            }
        }

        return new Config(config, file);
    }

    @VisibleForTesting
    public Config()
    {
        this.configuration = LinkedListMultimap.create();
        this.file = null;
    }

    public Config(LinkedListMultimap<String, String> configuration, File file)
    {
        this.configuration = configuration;
        this.file = file;
    }

    @Override
    public Iterator<Entry<String, Collection<String>>> iterator()
    {
        return ImmutableListMultimap.copyOf(configuration).asMap().entrySet().iterator();
    }

    public String get(String key)
    {
        return Iterables.getFirst(configuration.get(key), null);
    }

    public List<String> getAll(String key)
    {
        return configuration.get(key);
    }

    public void set(String key, @Nullable String value)
    {
        configuration.replaceValues(key, ImmutableList.of(value));
    }

    public void unset(String key)
    {
        configuration.removeAll(key);
    }

    public void add(String key, String value)
    {
        configuration.put(key, value);
    }

    public void save()
            throws IOException
    {
        if (file != null) {
            file.getAbsoluteFile().getParentFile().mkdirs();
            String data = Joiner.on('\n').withKeyValueSeparator(" = ").useForNull("").join(configuration.entries()) + "\n";
            Files.write(data, file, Charsets.UTF_8);
        }
    }

    @Override
    public String toString()
    {
        return configuration.toString();
    }
}
