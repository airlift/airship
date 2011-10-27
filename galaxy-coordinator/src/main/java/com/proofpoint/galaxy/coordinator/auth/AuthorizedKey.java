package com.proofpoint.galaxy.coordinator.auth;

public class AuthorizedKey
{
    private final String userId;
    private final PublicKey publicKey;

    public AuthorizedKey(String userId, PublicKey publicKey)
    {
        this.userId = userId;
        this.publicKey = publicKey;
    }

    public String getUserId()
    {
        return userId;
    }

    public PublicKey getPublicKey()
    {
        return publicKey;
    }
}
