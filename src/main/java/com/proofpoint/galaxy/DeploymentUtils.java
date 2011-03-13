package com.proofpoint.galaxy;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.MavenMetadata.unmarshalMavenMetadata;

public class DeploymentUtils
{
    private static final int TEMP_DIR_ATTEMPTS = 10000;
    private static final Executor executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("tar-command-%s").build());

    public static URI toMavenRepositoryPath(URI repositoryBase, BinarySpec spec)
    {
        String fileVersion = spec.getVersion();
        if (spec.getVersion().contains("SNAPSHOT")) {
            StringBuilder builder = new StringBuilder();
            builder.append(spec.getGroupId().replace('.', '/')).append('/');
            builder.append(spec.getArtifactId()).append('/');
            builder.append(spec.getVersion()).append('/');
            builder.append("maven-metadata.xml");

            URI uri = repositoryBase.resolve(builder.toString());
            try {
                MavenMetadata metadata = unmarshalMavenMetadata(Resources.toString(toURL(uri), Charsets.UTF_8));
                fileVersion = String.format("%s-%s-%s",
                        spec.getVersion().replaceAll("-SNAPSHOT", ""),
                        metadata.versioning.snapshot.timestamp,
                        metadata.versioning.snapshot.buildNumber);
            }
            catch (Exception ignored) {
                // no maven-metadata.xml file... hope this is laid out normally
            }

        }

        StringBuilder builder = new StringBuilder();
        builder.append(spec.getGroupId().replace('.', '/')).append('/');
        builder.append(spec.getArtifactId()).append('/');
        builder.append(spec.getVersion()).append('/');
        builder.append(spec.getArtifactId()).append('-').append(fileVersion);
        if (spec.getClassifier() != null) {
            builder.append('-').append(spec.getArtifactId());
        }
        builder.append('.').append(spec.getPackaging());

        return repositoryBase.resolve(builder.toString());
    }

    public static URL toURL(URI uri)
    {
        try {
            return uri.toURL();
        }
        catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createTar(File sourceDirectory, File tarFile)
            throws CommandFailedException
    {
        Preconditions.checkNotNull(sourceDirectory, "sourceDirectory is null");
        Preconditions.checkArgument(sourceDirectory.isDirectory(), "sourceDirectory is not a directory: " + sourceDirectory.getAbsolutePath());
        Preconditions.checkNotNull(tarFile, "tarFile is null");

        tarFile.getParentFile().mkdirs();
        new Command("tar", "zcf", tarFile.getAbsolutePath(), sourceDirectory.getName())
                .setDirectory(sourceDirectory.getParent())
                .setTimeLimit(5, TimeUnit.MINUTES)
                .execute(executor);
    }

    public static void extractTar(File tarFile, File targetDirectory)
            throws CommandFailedException
    {
        Preconditions.checkNotNull(tarFile, "tarFile is null");
        Preconditions.checkNotNull(targetDirectory, "targetDirectory is null");
        Preconditions.checkArgument(targetDirectory.isDirectory(), "targetDirectory is not a directory: " + targetDirectory.getAbsolutePath());

        new Command("tar", "zxf", tarFile.getAbsolutePath())
                .setDirectory(targetDirectory)
                .setTimeLimit(5, TimeUnit.MINUTES)
                .execute(executor);
    }

    public static File createTempDir(String prefix)
    {
        return createTempDir(new File(System.getProperty("java.io.tmpdir")), prefix);
    }

    public static File createTempDir(File parentDir, String prefix)
    {
        String baseName = "";
        if (prefix != null) {
            baseName += prefix + "-";
        }

        baseName += System.currentTimeMillis() + "-";
        for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
            File tempDir = new File(parentDir, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IllegalStateException("Failed to create directory within "
                + TEMP_DIR_ATTEMPTS + " attempts (tried "
                + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
    }

    public static boolean deleteDirectoryContents(File directory)
    {
        Preconditions.checkArgument(directory.isDirectory(), "Not a directory: %s", directory);

        // Symbolic links will have different canonical and absolute paths
        try {
            if (!directory.getCanonicalPath().equals(directory.getAbsolutePath())) {
                return false;
            }
        }
        catch (IOException e) {
            // something strange is happening, give up
            return false;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }

        boolean success = true;
        for (File file : files) {
            success = deleteRecursively(file) && success;
        }
        return success;
    }

    public static boolean deleteRecursively(File file)
    {
        boolean success = true;
        if (file.isDirectory()) {
            success = deleteDirectoryContents(file);
        }

        return file.delete() && success;
    }

    public static boolean copyDirectoryContents(File src, File target)
    {
        Preconditions.checkArgument(src.isDirectory(), "Source dir is not a directory: %s", src);
        // Symbolic links will have different canonical and absolute paths
        try {
            if (!src.getCanonicalPath().equals(src.getAbsolutePath())) {
                return false;
            }
        }
        catch (IOException e) {
            // something strange is happening, give up
            return false;
        }

        File[] files = src.listFiles();
        if (files == null) {
            return false;
        }

        target.mkdirs();
        Preconditions.checkArgument(target.isDirectory(), "Target dir is not a directory: %s", src);

        boolean success = true;
        for (File file : files) {
            success = copyRecursively(file, new File(target, file.getName())) && success;
        }
        return success;
    }

    public static boolean copyRecursively(File src, File target)
    {
        if (src.isDirectory()) {
            return copyDirectoryContents(src, target);
        }
        else {
            try {
                Files.copy(src, target);
                return true;
            }
            catch (IOException e) {
                return false;
            }
        }
    }
}
