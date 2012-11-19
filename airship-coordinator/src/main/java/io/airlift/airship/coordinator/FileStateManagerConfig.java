package io.airlift.airship.coordinator;

import io.airlift.configuration.Config;

import javax.validation.constraints.NotNull;

public class FileStateManagerConfig
{
    private String expectedStateDir = "expected-state";

    @NotNull
    public String getExpectedStateDir()
    {
        return expectedStateDir;
    }

    @Config("coordinator.expected-state.dir")
    public FileStateManagerConfig setExpectedStateDir(String expectedStateDir)
    {
        this.expectedStateDir = expectedStateDir;
        return this;
    }
}
