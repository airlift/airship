package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.DeploymentUtils;

import java.io.File;

import static com.proofpoint.galaxy.DeploymentUtils.createTempDir;
import static com.proofpoint.galaxy.DeploymentUtils.deleteRecursively;

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
        super(targetRepo.toURI());
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
            File sourceRepo = new File("src/test/repo/config/");
            if (!sourceRepo.isDirectory()) {
                throw new IllegalStateException("Expected source repository to exist: " + sourceRepo.getAbsolutePath());
            }
            targetRepo = createTempDir("config");

            // copy the source repository
            DeploymentUtils.copyRecursively(sourceRepo, targetRepo);

            return targetRepo;
        }
        catch (Exception e) {
            if (targetRepo != null) {
                deleteRecursively(targetRepo);
            }
            throw e;
        }
    }
}
