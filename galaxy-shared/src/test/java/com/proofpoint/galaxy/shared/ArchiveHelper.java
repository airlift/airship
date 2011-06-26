package com.proofpoint.galaxy.shared;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.newInputStreamSupplier;
import static com.proofpoint.galaxy.shared.FileUtils.createTar;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;

public class ArchiveHelper
{
    public static void createArchive(File archive)
            throws Exception
    {
        createArchive(archive, null);
    }

    public static void createArchive(File archive, String deployScriptContents)
            throws Exception
    {
        File tempDir = createTempDir("archive");
        try {
            // create bin directory
            File binDir = new File(tempDir, "bin");
            binDir.mkdirs();

            // copy launcher to bin dir
            File launcher = new File(binDir, "launcher");
            Files.copy(newInputStreamSupplier(getResource(ArchiveHelper.class, "launcher")), launcher);
            launcher.setExecutable(true, true);

            // create deploy script if requested
            if (deployScriptContents != null) {
                File deployScript = new File(binDir, "deploy");
                Files.write(deployScriptContents, deployScript, Charsets.UTF_8);
                deployScript.setExecutable(true, true);
            }

            // add a readme file
            Files.write(ArchiveHelper.class.getName() + " test archive", new File(tempDir, "README.txt"), UTF_8);

            // tar up the archive
            createTar(tempDir, archive);
        } finally {
            FileUtils.deleteRecursively(tempDir);
        }

    }
}
