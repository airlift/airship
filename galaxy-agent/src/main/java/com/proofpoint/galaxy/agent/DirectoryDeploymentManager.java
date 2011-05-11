package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import com.google.common.io.Resources;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.CommandFailedException;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Charsets.UTF_8;
import static com.proofpoint.experimental.json.JsonCodec.jsonCodec;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.FileUtils.extractTar;
import static com.proofpoint.galaxy.shared.FileUtils.listFiles;
import static java.lang.Math.max;

public class DirectoryDeploymentManager implements DeploymentManager
{
    private static final Logger log = Logger.get(DirectoryDeploymentManager.class);
    private final JsonCodec<DeploymentRepresentation> jsonCodec = jsonCodec(DeploymentRepresentation.class);

    private final UUID slotId;
    private final String slotName;
    private final Duration tarTimeout;
    private final File baseDir;

    private final Map<String, Deployment> deployments = new TreeMap<String, Deployment>();
    private Deployment activeDeployment;
    private File activeDeploymentFile;

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

        activeDeploymentFile = new File(baseDir, "galaxy-active-deployment.txt");
        if (activeDeploymentFile.exists()) {
            Preconditions.checkArgument(activeDeploymentFile.canRead(), "can not read " + activeDeploymentFile.getAbsolutePath());
            Preconditions.checkArgument(activeDeploymentFile.canWrite(), "can not write " + activeDeploymentFile.getAbsolutePath());
        }

        // load deployments
        for (File deploymentFile : listFiles(baseDir, new PatternFilenameFilter("galaxy-deployment\\d+-deployment.json"))) {
            try {
                Deployment deployment = load(deploymentFile);
                if (deployment.getDeploymentDir().isDirectory()) {
                    deployments.put(deployment.getDeploymentId(), deployment);
                }
                else {
                    log.warn(deploymentFile.getAbsolutePath() + " references a deployment that no longer exists: deleting");
                    deploymentFile.delete();
                }
            }
            catch (IOException e) {
                log.error(e, "Invalid deployment file: " + deploymentFile.getAbsolutePath());
            }
        }

        // warn about unknown directories
        for (File file : listFiles(baseDir)) {
            if (file.isDirectory() && !deployments.containsKey(file.getName())) {
                log.warn("Unknown directory in slot: " + file.getAbsolutePath());
            }
        }

        // load active deployment
        if (activeDeploymentFile.exists()) {
            try {
                String activeDeploymentId = Files.toString(activeDeploymentFile, UTF_8).trim();
                Deployment deployment = deployments.get(activeDeploymentId);
                if (deployment != null) {
                    activeDeployment = deployment;
                }
                else {
                    log.warn("The active deployment [" + activeDeploymentId + "] missing");
                    activeDeploymentFile.delete();
                }
            }
            catch (IOException e) {
                Preconditions.checkArgument(activeDeploymentFile.canRead(), "can not read " + activeDeploymentFile.getAbsolutePath());
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

        String deploymentId = getNextDeploymentId();
        File deploymentDir = new File(baseDir, deploymentId);

        Assignment assignment = installation.getAssignment();
        Deployment deployment = new Deployment(deploymentId, slotName, UUID.randomUUID(), deploymentDir, assignment);
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

        deployments.put(deploymentId, deployment);
        return deployment;
    }

    private static final Pattern DEPLOYMENT_ID_PATTERN = Pattern.compile("deployment(\\d+)");

    private String getNextDeploymentId()
    {
        int nextId = 1;
        for (String deploymentId : deployments.keySet()) {
            Matcher matcher = DEPLOYMENT_ID_PATTERN.matcher(deploymentId);
            if (matcher.matches()) {
                try {
                    int id = Integer.parseInt(matcher.group(1));
                    nextId = max(id, nextId + 1);
                }
                catch (NumberFormatException ignored) {
                }
            }
        }

        for (int i = 0; i < 10000; i++) {
            String deploymentId = "deployment" + nextId++;
            if (!new File(baseDir, deploymentId).exists()) {
                return deploymentId;
            }
        }
        throw new IllegalStateException("Could not find an valid deployment directory");
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

        try {
            Files.write(deploymentId, activeDeploymentFile, UTF_8);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to save active deployment file: " + activeDeploymentFile, e);
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
            activeDeploymentFile.delete();
        }
        Deployment deployment = deployments.remove(deploymentId);
        if (deployment != null) {
            getDeploymentAssignmentFile(deployment).delete();
            deleteRecursively(deployment.getDeploymentDir());
        }
    }

    public void save(Deployment deployment)
            throws IOException
    {
        String json = jsonCodec.toJson(DeploymentRepresentation.from(deployment));
        Files.write(json, getDeploymentAssignmentFile(deployment), UTF_8);
    }

    public Deployment load(File deploymentFile)
            throws IOException
    {
        String json = Files.toString(deploymentFile, UTF_8);
        DeploymentRepresentation data = jsonCodec.fromJson(json);
        Deployment deployment = data.toDeployment(new File(baseDir, data.getDeploymentId()));
        return deployment;
    }

    private File getDeploymentAssignmentFile(Deployment deployment)
    {
        return new File(baseDir, String.format("galaxy-%s-deployment.json", deployment.getDeploymentId()));
    }
}
