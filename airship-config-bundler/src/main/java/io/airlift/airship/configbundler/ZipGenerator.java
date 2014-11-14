package io.airlift.airship.configbundler;

import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

class ZipGenerator
        implements Generator
{
    private final Map<String, ByteSource> entries;

    public ZipGenerator(Map<String, ByteSource> entries)
    {
        this.entries = entries;
    }

    @Override
    public void write(OutputStream out)
            throws IOException
    {
        ZipPackager.packageEntries(out, entries);
    }
}
