package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Charsets.UTF_8;
import static com.proofpoint.galaxy.shared.FileUtils.copyRecursively;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;

public class TestingConfigRepository extends SimpleConfigRepository
{
    private final File targetRepo;

    public TestingConfigRepository()
            throws Exception
    {
        this(createConfigRepoDir());
    }

    public TestingConfigRepository(File targetRepo)
    {
        super("prod", targetRepo.toURI());
        this.targetRepo = targetRepo;
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public static File createConfigRepoDir()
            throws Exception
    {
        File targetRepo = null;
        try {
            targetRepo = createTempDir("config");

            createConfig(new File(targetRepo, "prod/apple/1.0"), "apple");
            createConfig(new File(targetRepo, "prod/apple/2.0"), "apple");
            createConfig(new File(targetRepo, "prod/banana/2.0-SNAPSHOT"), "banana");

            return targetRepo;
        }
        catch (Exception e) {
            if (targetRepo != null) {
                deleteRecursively(targetRepo);
            }
            throw e;
        }
    }

    public static void createConfig(File dir, String name)
            throws IOException
    {
        dir.mkdirs();

        // text file
        Files.write(name, new File(dir, name + ".txt"), UTF_8);

        // config.properties
        Files.write(
                "http-server.http.port=0\n" +
                        "config=" + name,
                new File(dir, "config.properties"), UTF_8);

        // jvm.config
        new File(dir, "jvm.config").createNewFile();

        // config-map.json
        Map<String, String> configMap = ImmutableMap.<String, String>builder()
                .put("etc/" + name + ".txt", name + ".txt")
                .put("etc/config.properties", "config.properties")
                .put("etc/jvm.config", "jvm.config")
                .build();
        Files.write(new ObjectMapper().writeValueAsString(configMap), new File(dir, "config-map.json"), UTF_8);
    }
}
