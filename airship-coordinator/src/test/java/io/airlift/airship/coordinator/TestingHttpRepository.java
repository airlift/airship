package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Charsets.UTF_8;
import static io.airlift.airship.shared.ArchiveHelper.createArchive;
import static io.airlift.airship.shared.FileUtils.createTempDir;
import static io.airlift.airship.shared.FileUtils.deleteRecursively;
import static io.airlift.airship.shared.FileUtils.newFile;

public class TestingHttpRepository
        extends HttpRepository
{
    private static final String VERSION_PATTERN = "([0-9][0-9.]*[0-9](?:-SNAPSHOT)?)[^\\/]*$";
    private final File targetRepo;

    public TestingHttpRepository()
            throws Exception
    {
        this(createRepoDir());
    }

    public TestingHttpRepository(File targetRepo)
    {
        super(ImmutableList.<URI>of(targetRepo.toURI()), "^(.*)-[0-9][0-9.]*[0-9](?:-SNAPSHOT)?[^\\/]*$", VERSION_PATTERN, VERSION_PATTERN);
        this.targetRepo = targetRepo;
    }

    public File getTargetRepo()
    {
        return targetRepo;
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public static File createRepoDir()
            throws Exception
    {
        File targetRepo = null;
        try {
            targetRepo = createTempDir("repo");

            // tar up the archive and add them to the repository
            File appleArchiveV1 = new File(targetRepo, "apple-1.0.tar.gz");
            File appleArchiveV2 = new File(targetRepo, "apple-2.0.tar.gz");
            File bananaArchive = new File(targetRepo, "banana-2.0-SNAPSHOT.tar.gz");
            createArchive(appleArchiveV1);
            appleArchiveV2.getParentFile().mkdirs();
            Files.copy(appleArchiveV1, appleArchiveV2);
            bananaArchive.getParentFile().mkdirs();
            Files.copy(appleArchiveV1, bananaArchive);

            // add prod configurations
            createConfig(targetRepo, "apple", "1.0");
            createConfig(targetRepo, "apple", "2.0");
            createConfig(targetRepo, "banana", "2.0-SNAPSHOT");

            return targetRepo;
        }
        catch (Exception e) {
            if (targetRepo != null) {
                deleteRecursively(targetRepo);
            }
            throw e;
        }
    }

    public static void createConfig(File dir, String artifactId, String version)
            throws Exception
    {
        File configFile = newFile(dir, artifactId + "-" + version + ".config");

        configFile.getParentFile().mkdirs();

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(configFile));

        // text file
        out.putNextEntry(new ZipEntry(artifactId + ".txt"));
        out.write(artifactId.getBytes(UTF_8));

        // config.properties
        out.putNextEntry(new ZipEntry("config.properties"));
        String properties = "http-server.http.port=0\n" +
                "config=" + artifactId;
        out.write(properties.getBytes(UTF_8));

        // jvm.config
        out.putNextEntry(new ZipEntry("jvm.config"));

        // config.properties
        String resources;
        if ("apple".equals(artifactId)) {
            resources = "memory=512\n" +
                    "cpu=1";
        }
        else {
            resources = "memory=1024\n" +
                    "cpu=2";
        }
        out.putNextEntry(new ZipEntry("airship-resources.properties"));
        out.write(resources.getBytes(UTF_8));

        out.close();
    }
}
