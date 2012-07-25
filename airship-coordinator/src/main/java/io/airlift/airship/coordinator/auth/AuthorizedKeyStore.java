package io.airlift.airship.coordinator.auth;

public interface AuthorizedKeyStore
{
    AuthorizedKey get(Fingerprint fingerprint);
}
