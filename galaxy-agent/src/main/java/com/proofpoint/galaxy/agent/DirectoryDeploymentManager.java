package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.CommandFailedException;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import static com.google.common.base.Charsets.UTF_8;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.FileUtils.extractTar;
import static com.proofpoint.galaxy.shared.FileUtils.listFiles;

public class DirectoryDeploymentManager implements DeploymentManager
{
    private static final Logger log = Logger.get(DirectoryDeploymentManager.class);
    private final JsonCodec<DeploymentRepresentation> jsonCodec = jsonCodec(DeploymentRepresentation.class);

    private final UUID slotId;
    private final String slotName;
    private final Duration tarTimeout;
    private final File baseDir;

    private final File deploymentFile;
    private Deployment deployment;

    public DirectoryDeploymentManager(AgentConfig config, String slotName, File baseDir)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(slotName, "slotName is null");
        this.slotName = slotName;
        this.tarTimeout = config.getTarTimeout();

        Preconditions.checkNotNull(baseDir, "baseDir is null");
        baseDir.mkdirs();
        Preconditions.checkArgument(baseDir.isDirectory(), "baseDir is not a directory: " + baseDir.getAbsolutePath());
        this.baseDir = baseDir;

        // verify deployment file is readable and writable
        deploymentFile = new File(baseDir, "galaxy-deployment.json");
        if (deploymentFile.exists()) {
            Preconditions.checkArgument(deploymentFile.canRead(), "can not read " + deploymentFile.getAbsolutePath());
            Preconditions.checkArgument(deploymentFile.canWrite(), "can not write " + deploymentFile.getAbsolutePath());
        }

        // load deployments
        if (deploymentFile.exists()) {
            try {
                Deployment deployment = load(deploymentFile);
                if (deployment.getDeploymentDir().isDirectory()) {
                    this.deployment = deployment;
                }
                else {
                    // todo this is totally borked
                    log.warn(deploymentFile.getAbsolutePath() + " references a deployment that no longer exists: deleting");
                    deploymentFile.delete();
                }
            }
            catch (IOException e) {
                log.error(e, "Invalid deployment file: " + deploymentFile.getAbsolutePath());
            }
        }

        File slotIdFile = new File(baseDir, "galaxy-slot-id.txt");
        UUID uuid = null;
        if (slotIdFile.exists()) {
            Preconditions.checkArgument(slotIdFile.canRead(), "can not read " + slotIdFile.getAbsolutePath());
            try {
                String slotIdString = Files.toString(slotIdFile, UTF_8).trim();
                try {
                    uuid = UUID.fromString(slotIdString);
                }
                catch (IllegalArgumentException e) {

                }
                if (uuid == null) {
                    log.warn("Invalid slot id [" + slotIdString + "]: attempting to delete galaxy-slot-id.txt file and recreating a new one");
                    slotIdFile.delete();
                }
            }
            catch (IOException e) {
                Preconditions.checkArgument(slotIdFile.canRead(), "can not read " + slotIdFile.getAbsolutePath());
            }
        }

        if (uuid == null) {
            uuid = UUID.randomUUID();
            try {
                Files.write(uuid.toString(), slotIdFile, UTF_8);
            }
            catch (IOException e) {
                Preconditions.checkArgument(slotIdFile.canRead(), "can not write " + slotIdFile.getAbsolutePath());
            }
        }
        slotId = uuid;
    }

    @Override
    public String getSlotName()
    {
        return slotName;
    }

    @Override
    public UUID getSlotId()
    {
        return slotId;
    }

    @Override
    public Deployment install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        Preconditions.checkState(deployment == null, "slot has an active deployment");

        File deploymentDir = new File(baseDir, "deployment");

        Assignment assignment = installation.getAssignment();

        File dataDir = getDataDir(assignment);

        Deployment deployment = new Deployment("deployment", slotName, UUID.randomUUID(), deploymentDir, dataDir, assignment);
        File tempDir = createTempDir(baseDir, "tmp-install");
        try {
            // download the binary
            File binary = new File(tempDir, "galaxy-binary.tar.gz");
            try {
                Files.copy(Resources.newInputStreamSupplier(installation.getBinaryFile().toURL()), binary);
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to download binary " + assignment.getBinary() + " from " + installation.getBinaryFile());
            }

            // unpack the binary into a temp unpack dir
            File unpackDir = new File(tempDir, "unpack");
            unpackDir.mkdirs();
            try {
                extractTar(binary, unpackDir, tarTimeout);
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
            for (Entry<String, URI> entry : installation.getConfigFiles().entrySet()) {
                String configFile = entry.getKey();
                URI configUri = entry.getValue();
                try {
                    File targetFile = new File(binaryRootDir, configFile);
                    targetFile.getParentFile().mkdirs();
                    Files.copy(Resources.newInputStreamSupplier(configUri.toURL()), targetFile);
                }
                catch (IOException e) {
                    throw new RuntimeException(String.format("Unable to download config file %s from %s for config %s",
                            configFile,
                            configUri,
                            assignment.getConfig()));
                }
            }
            try {
                save(deployment);
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to save deployment file", e);
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
            if (!deleteRecursively(tempDir)) {
                log.warn("Unable to delete temp directory: %s", tempDir.getAbsolutePath());
            }
        }

        this.deployment = deployment;
        return deployment;
    }

    @Override
    public Deployment getDeployment()
    {
        return deployment;
    }

    @Override
    public void clear()
    {
        if (deployment == null) {
            return;
        }
        deploymentFile.delete();
        deleteRecursively(deployment.getDeploymentDir());
        deployment = null;
    }

    @Override
    public void terminate()
    {
        deleteRecursively(baseDir);
        deployment = null;
    }

    public void save(Deployment deployment)
            throws IOException
    {
        String json = jsonCodec.toJson(DeploymentRepresentation.from(deployment));
        Files.write(json, deploymentFile, UTF_8);
    }

    public Deployment load(File deploymentFile)
            throws IOException
    {
        String json = Files.toString(deploymentFile, UTF_8);
        DeploymentRepresentation data = jsonCodec.fromJson(json);
        Deployment deployment = data.toDeployment(new File(baseDir, data.getDeploymentId()), getDataDir(data.getAssignment().toAssignment()));
        return deployment;
    }

    private File getDataDir(Assignment assignment)
    {
        String pool = assignment.getConfig().getPool();
        if (pool == null) {
            pool = "general";
        }
        File dataDir = new File(new File(new File(baseDir, "data"), assignment.getConfig().getComponent()), pool);
        dataDir.mkdirs();
        if (!dataDir.isDirectory()) {
            throw new RuntimeException(String.format("Unable to create data dir %s", dataDir.getAbsolutePath()));
        }
        return dataDir;
    }
}
