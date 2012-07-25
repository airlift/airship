package io.airlift.airship.coordinator.auth;

import com.proofpoint.configuration.Config;

public class AuthConfig
{
    private boolean enabled = false;

    public boolean isEnabled()
    {
        return enabled;
    }

    @Config("coordinator.auth.enabled")
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
