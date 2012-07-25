package io.airlift.airship.coordinator.auth;

import io.airlift.airship.coordinator.auth.ssh.SshRsaPublicKey;

import java.security.Signature;

public class RsaPublicKey
        extends PublicKey
{
    private SshRsaPublicKey sshRsaPublicKey;

    public RsaPublicKey(String encodedKey, String comment)
    {
        super(encodedKey, comment);
        sshRsaPublicKey = new SshRsaPublicKey(key);
    }

    @Override
    public String getType()
    {
        return "ssh-rsa";
    }

    @Override
    public boolean verifySignature(byte[] signature, byte[] message)
    {
        try {
            Signature sha1withRSA = Signature.getInstance("SHA1withRSA");

            sha1withRSA.initVerify(sshRsaPublicKey);
            sha1withRSA.update(message);
            return sha1withRSA.verify(signature);
        }
        catch (Exception e) {
            return false;
        }
    }
}
