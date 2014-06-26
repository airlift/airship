package io.airlift.airship.coordinator.auth;

import io.airlift.airship.coordinator.auth.ssh.DerWriter;
import io.airlift.airship.coordinator.auth.ssh.SshDsaPublicKey;
import io.airlift.airship.coordinator.auth.ssh.SshKeyReader;

import java.io.IOException;
import java.security.Signature;
import java.util.Arrays;

import static io.airlift.airship.coordinator.auth.ssh.DerType.SEQUENCE;

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
            // strip ssh envelope
            SshKeyReader reader = new SshKeyReader(signature);
            String signatureType = reader.readString();
            if (!"ssh-dss".equals(signatureType)) {
                throw new IllegalArgumentException("Signature is not a ssh-dss signature, but is " + signatureType + " signature");
            }
            signature = reader.readEntry();
        }

        if (signature.length != 40) {
            throw new IllegalArgumentException("Invalid ssh dsa signature");
        }

        // signature is two big endian unsigned 20-bit integers
        byte[] r = Arrays.copyOf(signature, 20);
        byte[] s = Arrays.copyOfRange(signature, 20, 40);

        // encode the signature in Java's DER format
        return toJavaDsaSignature(r, s);
    }

    private static byte[] toJavaDsaSignature(byte[] r, byte[] s)
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
