package com.proofpoint.galaxy.agent;

import com.google.inject.Inject;

import java.io.File;

public class DirectoryDeploymentManagerFactory implements DeploymentManagerFactory
{
    private final AgentConfig config;

    @Inject
    public DirectoryDeploymentManagerFactory(AgentConfig config)
    {
        this.config = config;
    }

    @Override
    public DirectoryDeploymentManager createDeploymentManager(File baseDir)
    {
        return new DirectoryDeploymentManager(config, baseDir);
    }
}
