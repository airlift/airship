package io.airlift.airship.coordinator.auth;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;

public class FileAuthorizedKeyStoreConfig
{
    private String authorizedKeysDir = "authorized-keys";

    @NotNull
    public String getAuthorizedKeysDir()
    {
        return authorizedKeysDir;
    }

    @Config("coordinator.auth.authorized-keys-dir")
    @ConfigDescription("Directory of files named as the userid and containing authorized keys")
    public FileAuthorizedKeyStoreConfig setAuthorizedKeysDir(String authorizedKeysDir)
    {
        this.authorizedKeysDir = authorizedKeysDir;
        return this;
    }
}
