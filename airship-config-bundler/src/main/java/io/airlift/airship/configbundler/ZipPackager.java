package com.proofpoint.galaxy.configbundler;

import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ZipPackager
{
    public static void packageEntries(OutputStream output, Map<String, InputSupplier<InputStream>> entries)
            throws IOException
    {
        ZipOutputStream out = new ZipOutputStream(output);

        for (Map.Entry<String, InputSupplier<InputStream>> entry : entries.entrySet()) {
            String path = entry.getKey();
            ZipEntry fileEntry = new ZipEntry(path);
            out.putNextEntry(fileEntry);
            ByteStreams.copy(entry.getValue(), out);
        }
        out.finish();
    }

}
