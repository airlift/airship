package io.airlift.airship.shared;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.proofpoint.configuration.ConfigurationFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Maps.newLinkedHashMap;

public class ConfigUtils
{
    public static void packConfig(File outputFile, String rootPath, File... inputDirs)
            throws IOException
    {
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        try {
            packConfig(outputStream, rootPath, inputDirs);
        }
        catch (IOException e) {
            outputStream.close();
        }
    }

    public static void packConfig(OutputStream outputStream, String rootPath, File... inputDirs)
            throws IOException
    {
        ZipOutputStream out = new ZipOutputStream(outputStream);
        try {
            zipDirectory(out, rootPath, inputDirs);
        }
        finally {
            out.finish();
            out.flush();
        }
    }

    private static void zipDirectory(ZipOutputStream out, String rootPath, File... inputDirs)
            throws IOException
    {
        LinkedHashMap<String, File> files = newLinkedHashMap();
        for (File inputDir : Lists.reverse(Arrays.asList(inputDirs))) {
            listFilesRecursive(rootPath, files, inputDir);
        }
        for (Entry<String, File> entry : files.entrySet()) {
            String path = entry.getKey();
            File file = entry.getValue();
            if (file.isDirectory()) {
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                ZipEntry dirEntry = new ZipEntry(path);
                dirEntry.setTime(file.lastModified());
                out.putNextEntry(dirEntry);
            }
            else {
                ZipEntry fileEntry = new ZipEntry(path);
                fileEntry.setTime(file.lastModified());
                out.putNextEntry(fileEntry);
                Files.copy(file, out);
            }
        }
    }

    private static void listFilesRecursive(String path, LinkedHashMap<String, File> files, File dir)
    {
        if (!path.isEmpty() && !path.endsWith("/")) {
            path = path + "/";
        }

        for (File file : firstNonNull(dir.listFiles(), new File[0])) {
            String filePath = path + file.getName();
            if (file.isDirectory()) {
                files.put(filePath, file);
                listFilesRecursive(filePath, files, file);
            }
            else {
                files.put(filePath, file);
            }
        }
    }

    public static void unpackConfig(InputSupplier<? extends InputStream> inputSupplier, File outputDir)
            throws IOException
    {
        ZipInputStream in = new ZipInputStream(inputSupplier.getInput());
        try {
            for (ZipEntry zipEntry = in.getNextEntry(); zipEntry != null; zipEntry = in.getNextEntry()) {
                File file = new File(outputDir, zipEntry.getName());
                if (zipEntry.getName().endsWith("/")) {
                    // this is a directory
                    file.mkdirs();
                }
                else {
                    file.getParentFile().mkdirs();
                    ByteStreams.copy(in, Files.newOutputStreamSupplier(file));
                    file.setLastModified(zipEntry.getTime());
                }
            }
        }
        finally {
            in.close();
        }
    }

    public static InputSupplier<InputStream> newConfigEntrySupplier(Repository repository, String config, final String entryName)
    {
        URI uri = repository.configToHttpUri(config);
        if (uri == null) {
            return null;
        }

        URL configUrl;
        try {
            configUrl = uri.toURL();
        }
        catch (MalformedURLException e) {
            throw new RuntimeException("Invalid config bundle location " + uri);
        }

        return ConfigUtils.newConfigEntrySupplier(Resources.newInputStreamSupplier(configUrl), entryName);
    }

    public static InputSupplier<InputStream> newConfigEntrySupplier(final URI configBundle, String entryName)
    {
        InputSupplier<InputStream> configBundleInput = new InputSupplier<InputStream>()
        {
            @Override
            public InputStream getInput()
                    throws IOException
            {
                URL url = configBundle.toURL();

                URLConnection connection = url.openConnection();
                if (connection instanceof HttpURLConnection) {
                    HttpURLConnection httpConnection = (HttpURLConnection) connection;
                    httpConnection.addRequestProperty("User-Agent", "User-Agent: Apache-Maven/3.0.3 (Java 1.6.0_29; Mac OS X 10.7.2)");
                }
                InputStream in = connection.getInputStream();
                return in;
            }
        };
        return newConfigEntrySupplier(configBundleInput, entryName);
    }

    private static InputSupplier<InputStream> newConfigEntrySupplier(final InputSupplier<? extends InputStream> configBundle, final String entryName)
    {
        return new InputSupplier<InputStream>()
        {
            @Override
            public InputStream getInput()
                    throws IOException
            {
                boolean success = false;
                ZipInputStream in = new ZipInputStream(configBundle.getInput());
                try {
                    ZipEntry zipEntry = in.getNextEntry();
                    while (zipEntry != null && !zipEntry.getName().equals(entryName)) {
                        zipEntry = in.getNextEntry();
                    }
                    if (zipEntry == null) {
                        throw new FileNotFoundException(entryName);
                    }

                    // wrap with buffer to make it difficult to mess with the zip stream
                    BufferedInputStream stream = new BufferedInputStream(in);

                    success = true;
                    return stream;
                }
                finally {
                    if (!success) {
                        in.close();
                    }
                }
            }
        };
    }

    public static ConfigurationFactory createConfigurationFactory(Repository repository, String config)
    {
        try {
            URI configUri = repository.configToHttpUri(config);
            Preconditions.checkNotNull(configUri, "Unknown configuration: " + config);
            return createConfigurationFactory(configUri);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to read configuration " + config);
        }
    }

    public static ConfigurationFactory createConfigurationFactory(URI configUri)
            throws IOException
    {
        Properties properties = new Properties();
        InputSupplier<InputStream> configPropertiesStream = newConfigEntrySupplier(configUri, "etc/config.properties");
        InputStream input = configPropertiesStream.getInput();
        try {
            properties.load(input);
        } finally {
            input.close();
        }

        return new ConfigurationFactory((Map<String,String>) (Object) properties);
    }
}
