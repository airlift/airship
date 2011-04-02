package com.proofpoint.galaxy.coordinator;

import com.google.common.io.Files;
import com.proofpoint.galaxy.shared.ArchiveHelper;

import java.io.File;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.newInputStreamSupplier;
import static com.proofpoint.galaxy.shared.ArchiveHelper.createArchive;
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
            targetRepo = createTempDir("repo");

            // copy maven-metadata.xml files
            File appleMavenMetadata = new File(targetRepo, "food/fruit/apple/maven-metadata.xml");
            appleMavenMetadata.getParentFile().mkdirs();
            Files.copy(newInputStreamSupplier(getResource(TestingBinaryRepository.class, "apple-maven-metadata.xml")), appleMavenMetadata);

            File bananaMavenMetadata = new File(targetRepo, "food/fruit/banana/2.0-SNAPSHOT/maven-metadata.xml");
            bananaMavenMetadata.getParentFile().mkdirs();
            Files.copy(newInputStreamSupplier(getResource(TestingBinaryRepository.class, "banana-maven-metadata.xml")), bananaMavenMetadata);

            // tar up the archive and add them to the repository
            File appleArchive = new File(targetRepo, "food/fruit/apple/1.0/apple-1.0.tar.gz");
            File bananaArchive = new File(targetRepo, "food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz");
            createArchive(appleArchive);
            Files.copy(appleArchive, bananaArchive);

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
