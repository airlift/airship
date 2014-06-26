package io.airlift.airship.coordinator.auth;

import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import com.google.common.primitives.Bytes;
import io.airlift.airship.coordinator.auth.ssh.DerReader;
import io.airlift.airship.coordinator.auth.ssh.PemDecoder;
import io.airlift.airship.coordinator.auth.ssh.PemDsaPrivateKey;
import io.airlift.airship.coordinator.auth.ssh.PemRsaPrivateKey;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;

import static com.google.common.base.Charsets.UTF_8;
import static io.airlift.airship.coordinator.auth.ssh.DerType.SEQUENCE;
import static org.apache.commons.codec.binary.Hex.encodeHexString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
    Change to the resource directory:

       cd src/test/resources/io/airlift/airship/coordinator/auth

    Generate test keys:

       ssh-keygen -t rsa -C testkey -N '' -f testkey.rsa
       ssh-keygen -t dsa -C testkey -N '' -f testkey.dsa

    Add test keys to SSH agent:

        ssh-add testkey.{rsa,dsa}

    Create signatures using the agent in Ruby:

        require 'ssh/key/signer'
        signer = SSH::Key::Signer.new
        x = signer.sign('Hello world')
        rsa = x.find {|i| i.identity.comment == 'testkey.rsa' }
        dsa = x.find {|i| i.identity.comment == 'testkey.dsa' }
        File.open('signature.rsa', 'wb') {|f| f.write(rsa.signature) }
        File.open('signature.dsa', 'wb') {|f| f.write(dsa.signature) }
*/

public class TestPublicKey
{
    private static final byte[] message = "Hello world".getBytes(UTF_8);
    private PublicKey rsaPublicKey;
    private PublicKey dsaPublicKey;
    private PemRsaPrivateKey rsaPrivateKey;
    private PemDsaPrivateKey dsaPrivateKey;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        rsaPublicKey = PublicKey.valueOf(loadResource("testkey.rsa.pub"));
        dsaPublicKey = PublicKey.valueOf(loadResource("testkey.dsa.pub"));
        rsaPrivateKey = (PemRsaPrivateKey) PemDecoder.decodeSshPrivateKey(loadResource("testkey.rsa"));
        dsaPrivateKey = (PemDsaPrivateKey) PemDecoder.decodeSshPrivateKey(loadResource("testkey.dsa"));
    }

    @Test
    public void testRsaVerify()
            throws IOException
    {
        byte[] signature = generateRsaSignature(message);
        assertTrue(rsaPublicKey.verifySignature(signature, message));
    }

    @Test
    public void testDsaVerify()
            throws IOException
    {
        byte[] signature = generateDsaSignature(message);
        assertTrue(dsaPublicKey.verifySignature(signature, message));
    }

    @Test
    public void testRsaVerifyFromAgent()
            throws IOException
    {
        byte[] signature = loadBinaryResource("signature.rsa");
        assertTrue(rsaPublicKey.verifySignature(signature, message));
    }

    @Test
    public void testDsaVerifyFromAgent()
            throws IOException
    {
        byte[] signature = loadBinaryResource("signature.dsa");
        assertTrue(dsaPublicKey.verifySignature(signature, message));
    }

    @Test
    public void testDsaVerifyFromAgentWithEnvelope()
            throws IOException
    {
        byte[] signature = loadBinaryResource("signature-envelope.dsa");
        assertTrue(dsaPublicKey.verifySignature(signature, message));
    }

    private byte[] generateRsaSignature(byte[] message)
            throws IOException
    {
        return sign(rsaPrivateKey, message, "SHA1withRSA");
    }

    private byte[] generateDsaSignature(byte[] message)
    {
        try {
            byte[] javaSignature = sign(dsaPrivateKey, message, "SHA1withDSA");
            return toSshDsaRawSignature(javaSignature);
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private byte[] toSshDsaRawSignature(byte[] javaSignature)
            throws IOException
    {
        // strip off the sequence envelope
        byte[] sequence = new DerReader(javaSignature).readEntry(SEQUENCE);

        // read the two integers of the key
        DerReader derReader = new DerReader(sequence);
        ByteArrayOutputStream rawSignature = new ByteArrayOutputStream();
        rawSignature.write(getBytes(derReader.readBigInteger()));
        rawSignature.write(getBytes(derReader.readBigInteger()));
        return rawSignature.toByteArray();
    }

    public byte[] sign(PrivateKey key, byte[] message, String algorithm)
    {
        try {
            Signature signature = Signature.getInstance(algorithm);

            signature.initSign(key);
            signature.update(message);
            return signature.sign();
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String loadResource(String name)
            throws IOException
    {
        return Resources.toString(getClass().getResource(name), UTF_8);
    }

    private byte[] loadBinaryResource(String name)
            throws IOException
    {
        return Resources.toByteArray(getClass().getResource(name));
    }

    private static byte[] getBytes(BigInteger n)
    {
        byte[] bytes = n.toByteArray();
        if ((bytes.length == 21) && (bytes[0] == 0)) {
            bytes = Arrays.copyOfRange(bytes, 1, 21);
        }
        while (bytes.length < 20) {
            bytes = Bytes.concat(new byte[] {0}, bytes);
        }
        assertEquals(bytes.length, 20, encodeHexString(bytes));
        assertEquals(new BigInteger(1, bytes), n, encodeHexString(bytes));
        return bytes;
    }
}
