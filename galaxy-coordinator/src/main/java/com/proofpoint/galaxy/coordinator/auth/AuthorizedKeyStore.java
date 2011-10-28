package com.proofpoint.galaxy.coordinator.auth;

public interface AuthorizedKeyStore
{
    AuthorizedKey get(Fingerprint fingerprint);
}
