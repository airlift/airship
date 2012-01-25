package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Charsets.UTF_8;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;

public class TestingConfigRepository extends ConfigInBinaryRepository
{
    private final File targetRepo;

    public TestingConfigRepository()
            throws Exception
    {
        this(createConfigRepoDir());
    }

    public TestingConfigRepository(File targetRepo)
            throws Exception
    {
        super(new MavenBinaryRepository(targetRepo.toURI()), "prod");
        this.targetRepo = targetRepo;
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public static File createConfigRepoDir()
            throws Exception
    {
        File targetRepo = createTempDir("config");
        try {

            initConfigRepo(targetRepo);

            return targetRepo;
        }
        catch (Exception e) {
            if (targetRepo != null) {
                deleteRecursively(targetRepo);
            }
            throw e;
        }
    }

    public static void initConfigRepo(File targetRepo)
            throws IOException
    {
        createConfig(targetRepo, new ConfigSpec("apple", "1.0"));
        createConfig(targetRepo, new ConfigSpec("apple", "2.0"));
        createConfig(targetRepo, new ConfigSpec("banana", "2.0-SNAPSHOT"));
    }

    public static void createConfig(File dir, ConfigSpec configSpec)
            throws IOException
    {
        String name = configSpec.getComponent();
        String artifactId = configSpec.getComponent() + "-" + configSpec.getPool();
        File configFile = FileUtils.newFile(dir, "prod", artifactId, configSpec.getVersion(), artifactId + "-" + configSpec.getVersion() + ".config");

        configFile.getParentFile().mkdirs();

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(configFile));

        // text file
        out.putNextEntry(new ZipEntry(name + ".txt"));
        out.write(name.getBytes(UTF_8));

        // config.properties
        out.putNextEntry(new ZipEntry("config.properties"));
        String properties = "http-server.http.port=0\n" +
                "config=" + name;
        out.write(properties.getBytes(UTF_8));


        // jvm.config
        out.putNextEntry(new ZipEntry("jvm.config"));

        // config.properties
        String resources;
        if ("apple".equals(name)) {
            resources = "memory=512\n" +
                    "cpu=1";
        }
        else {
            resources = "memory=1024\n" +
                    "cpu=2";
        }
        out.putNextEntry(new ZipEntry("galaxy-resources.properties"));
        out.write(resources.getBytes(UTF_8));

        out.close();
    }
}
