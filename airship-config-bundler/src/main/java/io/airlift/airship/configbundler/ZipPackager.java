package io.airlift.airship.configbundler;

import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ZipPackager
{
    public static void packageEntries(OutputStream output, Map<String, ByteSource> entries)
            throws IOException
    {
        ZipOutputStream out = new ZipOutputStream(output);

        for (Map.Entry<String, ByteSource> entry : entries.entrySet()) {
            String path = entry.getKey();
            ZipEntry fileEntry = new ZipEntry(path);
            out.putNextEntry(fileEntry);
            entry.getValue().copyTo(out);
        }
        out.finish();
    }
}
