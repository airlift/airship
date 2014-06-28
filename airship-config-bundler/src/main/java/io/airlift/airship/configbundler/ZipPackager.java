package io.airlift.airship.configbundler;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

class ZipPackager
        extends ByteSource
{
    public final Map<String, ByteSource> entries;

    ZipPackager(Map<String, ByteSource> entries)
    {
        this.entries = checkNotNull(entries, "entries is null");
    }

    @Override
    public InputStream openStream()
            throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ZipOutputStream out = new ZipOutputStream(baos);

        for (Map.Entry<String, ByteSource> entry : entries.entrySet()) {
            String path = entry.getKey();
            ZipEntry fileEntry = new ZipEntry(path);
            out.putNextEntry(fileEntry);
            ByteStreams.copy(entry.getValue(), out);
        }
        out.finish();

        return new ByteArrayInputStream(baos.toByteArray());
    }
}
