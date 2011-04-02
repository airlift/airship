package com.proofpoint.galaxy.coordinator;

import java.io.File;

import static com.proofpoint.galaxy.shared.FileUtils.copyRecursively;
import static com.proofpoint.galaxy.shared.FileUtils.createTar;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;

public class TestingBinaryRepository extends MavenBinaryRepository
{
    private final File targetRepo;

    public TestingBinaryRepository()
            throws Exception
    {
        this(createBinaryRepoDir());
    }

    public TestingBinaryRepository(File targetRepo)
    {
        super(targetRepo.toURI());
        this.targetRepo = targetRepo;
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public static File createBinaryRepoDir()
            throws Exception
    {
        File targetRepo = null;
        try {
            File sourceRepo = new File("src/test/repo/binary/");
            if (!sourceRepo.isDirectory()) {
                throw new IllegalStateException("Expected source repository to exist: " + sourceRepo.getAbsolutePath());
            }
            targetRepo = createTempDir("repo");

            // copy the source repository
            copyRecursively(sourceRepo, targetRepo);

            // tar up the archive and add them to the repository
            createTar(new File("src/test/archives/good"), new File(targetRepo, "food/fruit/apple/1.0/apple-1.0.tar.gz"));
            createTar(new File("src/test/archives/good"), new File(targetRepo, "food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));

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
