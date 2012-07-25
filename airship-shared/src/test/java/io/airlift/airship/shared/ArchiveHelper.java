package io.airlift.airship.shared;

import com.google.common.io.Files;

import java.io.File;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.newInputStreamSupplier;
import static io.airlift.airship.shared.FileUtils.createTar;
import static io.airlift.airship.shared.FileUtils.createTempDir;

public class ArchiveHelper
{
    public static void createArchive(File archive)
            throws Exception
    {
        File tempDir = createTempDir("archive");
        try {
            // copy launcher to bin dir
            File binDir = new File(tempDir, "bin");
            binDir.mkdirs();
            File launcher = new File(binDir, "launcher");
            Files.copy(newInputStreamSupplier(getResource(ArchiveHelper.class, "launcher")), launcher);

            // make launcher executable
            launcher.setExecutable(true, true);

            // add a readme file
            Files.write(ArchiveHelper.class.getName() + " test archive", new File(tempDir, "README.txt"), UTF_8);

            // tar up the archive
            createTar(tempDir, archive);
        } finally {
            FileUtils.deleteRecursively(tempDir);
        }

    }
}
