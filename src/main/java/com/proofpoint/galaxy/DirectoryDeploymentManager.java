package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.proofpoint.log.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DirectoryDeploymentManager implements DeploymentManager
{
    private static final Logger log = Logger.get(DirectoryDeploymentManager.class);
    private final File baseDir;
    private final URI repositoryBase;
    private final Map<String, Deployment> deployments = new TreeMap<String, Deployment>();
    private Deployment activeDeployment;
    private final AtomicInteger nextId = new AtomicInteger(1);

    public DirectoryDeploymentManager(File baseDir, URI repositoryBase)
    {
        Preconditions.checkNotNull(baseDir, "baseDir is null");
        baseDir.mkdirs();
        Preconditions.checkArgument(baseDir.isDirectory(), "baseDir is not a directory");
        this.baseDir = baseDir;

        Preconditions.checkNotNull(repositoryBase, "repositoryBase is null");
        this.repositoryBase = repositoryBase;
    }

    @Override
    public Deployment install(Assignment assignment)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");

        String deploymentId = "Deployment-" + nextId.getAndIncrement();
        File deploymentDir = new File(baseDir, deploymentId);

        URI archivePath = DeploymentUtils.toMavenRepositoryPath(repositoryBase, assignment.getBinary());
        File tempDir = DeploymentUtils.createTempDir(baseDir, "tmp-install");
        try {
            // download the archive
            File archive = new File(tempDir, "galaxy-archive.tar.gz");
            try {
                Files.copy(Resources.newInputStreamSupplier(DeploymentUtils.toURL(archivePath)), archive);
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to download tar file " + assignment.getBinary() + " from " + archivePath);
            }

            // unpack the archive into a temp unpack dir
            File unpackDir = new File(tempDir, "unpack");
            unpackDir.mkdirs();
            try {
                DeploymentUtils.extractTar(archive, unpackDir);
            }
            catch (CommandFailedException e) {
                throw new RuntimeException("Unable to extract tar file " + assignment.getBinary() + ": " + e.getMessage());
            }

            // find the archive root dir (it should be the only file in the temp unpack dir)
            List<File> files = listFiles(unpackDir);
            if (files.size() != 1) {
                throw new RuntimeException("Invalid tar file: file does not have a root directory " + assignment.getBinary());
            }
            File archiveDir = files.get(0);

            // copy config files from config repository
            // config_installer.install(dir)

            // move the archive directory to the final target
            try {
                Files.move(archiveDir, deploymentDir);
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to deployment to final location", e);
            }
        }
        finally {
            if (!DeploymentUtils.deleteRecursively(tempDir)) {
                log.warn("Unable to delete temp directory: %s", tempDir.getAbsolutePath());
            }
        }

        Deployment deployment = new Deployment(deploymentId, deploymentDir, assignment);
        deployments.put(deploymentId, deployment);
        return deployment;
    }

    @Override
    public Deployment getActiveDeployment()
    {
        return activeDeployment;
    }

    @Override
    public Deployment activate(String deploymentId)
    {
        Preconditions.checkNotNull(deploymentId, "deploymentId is null");
        Deployment deployment = deployments.get(deploymentId);
        if (deployment == null) {
            throw new IllegalArgumentException("Unknown deployment id");
        }

        activeDeployment = deployment;
        return deployment;
    }

    @Override
    public void remove(String deploymentId)
    {
        Preconditions.checkNotNull(deploymentId, "deploymentId is null");
        if (activeDeployment != null && deploymentId.equals(activeDeployment.getDeploymentId())) {
            activeDeployment = null;
        }
        deployments.remove(deploymentId);
    }

    public List<File> listFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }
}
