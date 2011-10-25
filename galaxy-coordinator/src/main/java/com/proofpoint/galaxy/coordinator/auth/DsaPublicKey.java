package com.proofpoint.galaxy.coordinator.auth;

import com.proofpoint.galaxy.coordinator.auth.ssh.DerWriter;
import com.proofpoint.galaxy.coordinator.auth.ssh.SshDsaPublicKey;

import java.io.IOException;
import java.math.BigInteger;
import java.security.Signature;
import java.util.Arrays;

import static com.proofpoint.galaxy.coordinator.auth.ssh.DerType.SEQUENCE;

public class DsaPublicKey
        extends PublicKey
{
    private SshDsaPublicKey sshDsaPublicKey;

    public DsaPublicKey(String encodedKey, String comment)
    {
        super(encodedKey, comment);
        sshDsaPublicKey = new SshDsaPublicKey(key);
    }

    public String getType()
    {
        return "ssh-dss";
    }

    public boolean verifySignature(byte[] signature, byte[] message)
    {
        try {
            Signature sha1withDSA = Signature.getInstance("SHA1withDSA");

            sha1withDSA.initVerify(sshDsaPublicKey);
            sha1withDSA.update(message);
            byte[] decodedSignature = toJavaDsaSignature(signature);
            return sha1withDSA.verify(decodedSignature);
        }
        catch (Exception e) {
            return false;
        }
    }

    public static byte[] toJavaDsaSignature(byte[] signature)
            throws IOException
    {
        if (signature.length != 40) {
            throw new IllegalArgumentException("Invalid ssh dsa signature");
        }

        // signature is two big endian unsigned 20-bit integers
        BigInteger r = new BigInteger(1, Arrays.copyOf(signature, 20));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(signature, 20, 40));

        // encode the signature in Java's DER format
        return toJavaDsaSignature(r, s);
    }

    private static byte[] toJavaDsaSignature(BigInteger r, BigInteger s)
            throws IOException
    {
        byte[] encodedNumbers = new DerWriter()
                .writeInteger(r)
                .writeInteger(s)
                .toByteArray();

        return new DerWriter()
                .writeEntry(SEQUENCE, encodedNumbers)
                .toByteArray();
    }

}
