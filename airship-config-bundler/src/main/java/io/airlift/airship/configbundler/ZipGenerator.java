package io.airlift.airship.configbundler;

import com.google.common.io.InputSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

class ZipGenerator
        implements Generator
{
    private final Map<String, InputSupplier<InputStream>> entries;

    public ZipGenerator(Map<String, InputSupplier<InputStream>> entries)
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
