package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.proofpoint.galaxy.DeploymentUtils.deleteRecursively;

public class DirectoryDeploymentManager implements DeploymentManager
{
    private static final Logger log = Logger.get(DirectoryDeploymentManager.class);

    private final Duration tarTimeout;
    private final File baseDir;

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<String, Deployment> deployments = new TreeMap<String, Deployment>();
    private Deployment activeDeployment;

    public DirectoryDeploymentManager(AgentConfig config, File baseDir)
    {
        Preconditions.checkNotNull(config, "config is null");
        this.tarTimeout = config.getTarTimeout();

        Preconditions.checkNotNull(baseDir, "baseDir is null");
        baseDir.mkdirs();
        Preconditions.checkArgument(baseDir.isDirectory(), "baseDir is not a directory");
        this.baseDir = baseDir;
    }

    @Override
    public Deployment install(Assignment assignment)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");

        String deploymentId = "Deployment-" + nextId.getAndIncrement();
        File deploymentDir = new File(baseDir, deploymentId);

        File tempDir = DeploymentUtils.createTempDir(baseDir, "tmp-install");
        try {
            // download the binary
            File binary = new File(tempDir, "galaxy-binary.tar.gz");
            try {
                Files.copy(Resources.newInputStreamSupplier(DeploymentUtils.toURL(assignment.getBinaryFile())), binary);
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to download binary " + assignment.getBinary() + " from " + assignment.getBinaryFile());
            }

            // unpack the binary into a temp unpack dir
            File unpackDir = new File(tempDir, "unpack");
            unpackDir.mkdirs();
            try {
                DeploymentUtils.extractTar(binary, unpackDir, tarTimeout);
            }
            catch (CommandFailedException e) {
                throw new RuntimeException("Unable to extract tar file " + assignment.getBinary() + ": " + e.getMessage());
            }

            // find the archive root dir (it should be the only file in the temp unpack dir)
            List<File> files = listFiles(unpackDir);
            if (files.size() != 1) {
                throw new RuntimeException("Invalid tar file: file does not have a root directory " + assignment.getBinary());
            }
            File binaryRootDir = files.get(0);

            // copy config files from config repository
            for (Entry<String, URI> entry : assignment.getConfigFiles().entrySet()) {
                String configFile = entry.getKey();
                URI configUri = entry.getValue();
                try {
                    Files.copy(Resources.newInputStreamSupplier(DeploymentUtils.toURL(configUri)), new File(binaryRootDir, configFile));
                }
                catch (IOException e) {
                    throw new RuntimeException(String.format("Unable to download config file %s from %s for config %s",
                            configFile,
                            configUri,
                            assignment.getConfig()));
                }
            }

            // move the binary root directory to the final target
            try {
                Files.move(binaryRootDir, deploymentDir);
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
        Deployment deployment = deployments.remove(deploymentId);
        if (deployment != null) {
            deleteRecursively(deployment.getDeploymentDir());
        }
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
