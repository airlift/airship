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

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("AuthorizedKey");
        sb.append("{userId='").append(userId).append('\'');
        sb.append(", publicKey=").append(publicKey);
        sb.append('}');
        return sb.toString();
    }
}
