package com.proofpoint.galaxy.agent;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import java.io.File;
import java.util.List;

import static com.proofpoint.galaxy.shared.FileUtils.listFiles;

public class DirectoryDeploymentManagerFactory implements DeploymentManagerFactory
{
    private final AgentConfig config;
    private final File slotDir;

    @Inject
    public DirectoryDeploymentManagerFactory(AgentConfig config)
    {
        this.config = config;

        this.slotDir = new File(config.getSlotsDir());

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
            if (dir.isDirectory() && new File(dir, "galaxy-slot-id.txt").canRead()) {
                builder.add(createDeploymentManager(dir.getName()));
            }
        }
        return builder.build();
    }

    @Override
    public DirectoryDeploymentManager createDeploymentManager(String slotName)
    {
        return new DirectoryDeploymentManager(config, slotName, new File(slotDir, slotName));
    }
}
