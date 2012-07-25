package io.airlift.airship.agent;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.airlift.airship.shared.FileUtils;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.MavenCoordinates;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Set;

import static io.airlift.airship.shared.FileUtils.listFiles;

public class DirectoryDeploymentManagerFactory implements DeploymentManagerFactory
{
    private final String location;
    private final Duration tarTimeout;
    private final File slotDir;

    @Inject
    public DirectoryDeploymentManagerFactory(NodeInfo nodeInfo, AgentConfig config)
    {
        this(nodeInfo.getLocation(), config.getSlotsDir(), config.getTarTimeout());
    }

    public DirectoryDeploymentManagerFactory(String location, String slotsDir, Duration tarTimeout)
    {
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkNotNull(slotsDir, "slotsDir is null");
        Preconditions.checkNotNull(tarTimeout, "tarTimeout is null");

        this.location = location;
        this.tarTimeout = tarTimeout;

        this.slotDir = new File(slotsDir);

        slotDir.mkdirs();
        if (!slotDir.isDirectory()) {
            throw new IllegalArgumentException("slotDir is not a directory");
        }
    }

    @Override
    public List<DeploymentManager> loadSlots()
    {
        ImmutableList.Builder<DeploymentManager> builder = ImmutableList.builder();
        for (File dir : listFiles(slotDir)) {
            if (dir.isDirectory() && new File(dir, "airship-slot-id.txt").canRead()) {
                DirectoryDeploymentManager deploymentManager = new DirectoryDeploymentManager(dir, location + "/" + dir.getName(), tarTimeout);
                builder.add(deploymentManager);
            }
        }
        return builder.build();
    }

    @Override
    public DirectoryDeploymentManager createDeploymentManager(Installation installation)
    {
        File slotDirectory = getSlotDirectory(installation);
        return new DirectoryDeploymentManager(slotDirectory, location + "/" + slotDirectory.getName(), tarTimeout);
    }

    private synchronized File getSlotDirectory(Installation installation)
    {
        String baseName = toBaseName(installation);
        baseName = baseName.replace("[^a-zA-Z0-9_.-]", "_");

        Set<String> fileNames = ImmutableSet.copyOf(Lists.transform(FileUtils.listFiles(slotDir), new Function<File, String>()
        {
            @Override
            public String apply(@Nullable File file)
            {
                return file.getName();
            }
        }));
        if (!fileNames.contains(baseName)) {
            return new File(slotDir, baseName);
        }

        for (int i = 1; i < 10000; i++) {
            String directoryName = baseName + i;
            if (!fileNames.contains(directoryName)) {
                return new File(slotDir, directoryName);
            }
        }
        throw new IllegalStateException("Could not find an valid slot directory name");
    }

    private String toBaseName(Installation installation)
    {
        String configSpec = installation.getAssignment().getConfig();
        MavenCoordinates mavenCoordinates = MavenCoordinates.fromConfigGAV(configSpec);
        String baseName;
        if (mavenCoordinates != null) {
            baseName = mavenCoordinates.getArtifactId();
        } else if (configSpec.startsWith("@")) {

            baseName = configSpec.substring(1);
        } else {
            baseName = configSpec;
        }
        return baseName;
    }

}
