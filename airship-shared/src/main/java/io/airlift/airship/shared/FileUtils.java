package io.airlift.airship.shared;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.airlift.command.Command;
import io.airlift.command.CommandFailedException;
import io.airlift.units.Duration;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FileUtils
{
    private static final int TEMP_DIR_ATTEMPTS = 10000;
    private static final Executor executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("tar-command-%s").build());

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

    public static void extractTar(File tarFile, File targetDirectory, Duration timeLimit)
            throws CommandFailedException
    {
        Preconditions.checkNotNull(tarFile, "tarFile is null");
        Preconditions.checkNotNull(targetDirectory, "targetDirectory is null");
        Preconditions.checkArgument(targetDirectory.isDirectory(), "targetDirectory is not a directory: " + targetDirectory.getAbsolutePath());

        new Command("tar", "zxf", tarFile.getAbsolutePath())
                .setDirectory(targetDirectory)
                .setTimeLimit(timeLimit)
                .execute(executor);
    }

    public static void createSymbolicLink(File source, File target)
            throws CommandFailedException
    {
        Preconditions.checkNotNull(source, "source is null");
        Preconditions.checkArgument(source.exists(), "source does not exist: " + source.getAbsolutePath());
        Preconditions.checkNotNull(target, "source is null");
        Preconditions.checkArgument(!target.exists(), "target already exists: " + target.getAbsolutePath());

        target.getParentFile().mkdirs();
        new Command("ln", "-s", source.getAbsolutePath(), target.getAbsolutePath())
                .setDirectory(target.getParent())
                .setTimeLimit(5, TimeUnit.MINUTES)
                .execute(executor);
    }

    public static boolean isSymbolicLink(File file)
    {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                    // or the canonical parent path is not the same as the files parent path
                    !canonicalFile.getParent().equals(absoluteFile.getParentFile().getCanonicalPath());
        }
        catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    public static ImmutableList<File> listFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    public static ImmutableList<File> listFiles(File dir, FilenameFilter filter)
    {
        File[] files = dir.listFiles(filter);
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
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
            if (tempDir.mkdirs()) {
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

        // Don't delete symbolic link directories
        if (isSymbolicLink(directory)) {
            return false;
        }

        boolean success = true;
        for (File file : listFiles(directory)) {
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

        // Don't delete symbolic link directories
        if (isSymbolicLink(src)) {
            return false;
        }

        target.mkdirs();
        Preconditions.checkArgument(target.isDirectory(), "Target dir is not a directory: %s", src);

        boolean success = true;
        for (File file : listFiles(src)) {
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

    public static File newFile(String parent, String... paths)
    {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        return newFile(new File(parent), ImmutableList.copyOf(paths));
    }

    public static File newFile(File parent, String... paths)
    {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        return newFile(parent, ImmutableList.copyOf(paths));
    }

    public static File newFile(File parent, Iterable<String> paths)
    {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        File result = parent;
        for (String path : paths) {
            result = new File(result, path);
        }
        return result;
    }
}
