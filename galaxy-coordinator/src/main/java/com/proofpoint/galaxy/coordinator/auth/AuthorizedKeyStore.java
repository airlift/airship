package com.proofpoint.galaxy.coordinator.auth;

public interface AuthorizedKeyStore
{
    AuthorizedKey get(byte[] fingerprint);
}
