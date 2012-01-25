package com.proofpoint.galaxy.shared;

import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.net.URI;

import static com.proofpoint.galaxy.shared.ArchiveHelper.createArchive;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;

public class InstallationHelper
{
    public static final Installation APPLE_INSTALLATION = new Installation(APPLE_ASSIGNMENT,
            URI.create("fake://localhost/apple.tar.gz"),
            URI.create("fake://localhost/apple.config"),
            ImmutableMap.of("memory", 512));
    public static final Installation BANANA_INSTALLATION = new Installation(BANANA_ASSIGNMENT,
            URI.create("fake://localhost/banana.tar.gz"),
            URI.create("fake://localhost/banana.config"),
            ImmutableMap.of("cpu", 1));

    private final File targetRepo;
    private final Installation appleInstallation;
    private final Installation bananaInstallation;

    public InstallationHelper()
            throws Exception
    {
        File targetRepo = null;
        File binaryFile;
        try {
            targetRepo = createTempDir("repo");
            binaryFile = new File(targetRepo, "binary.tar.gz");
            createArchive(binaryFile);
        }
        catch (Exception e) {
            if (targetRepo != null) {
                deleteRecursively(targetRepo);
            }
            throw e;
        }
        this.targetRepo = targetRepo;

        appleInstallation = new Installation(APPLE_ASSIGNMENT, binaryFile.toURI(), new File("README.txt").toURI(), ImmutableMap.of("memory", 512));
        bananaInstallation = new Installation(BANANA_ASSIGNMENT, binaryFile.toURI(), new File("README.txt").toURI(), ImmutableMap.of("cpu", 1));
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public Installation getAppleInstallation()
    {
        return appleInstallation;
    }

    public Installation getBananaInstallation()
    {
        return bananaInstallation;
    }
}
