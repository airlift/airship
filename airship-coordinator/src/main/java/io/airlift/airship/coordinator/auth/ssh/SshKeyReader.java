package io.airlift.airship.coordinator.auth.ssh;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;

public class SshKeyReader
{
    private final DataInputStream out;

    public SshKeyReader(byte[] data)
    {
        out = new DataInputStream(new ByteArrayInputStream(data));
    }

    public BigInteger readBigInteger()
    {
        byte[] data = readEntry();
        if (data.length == 0) {
            return BigInteger.ZERO;
        }
        return new BigInteger(data);
    }

    public String readString()
    {
        return new String(readEntry(), Charsets.UTF_8);
    }

    public byte[] readEntry()
    {
        try {
            int length = out.readInt();
            byte[] data = new byte[length];
            out.readFully(data);
            return data;
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public boolean isComplete()
    {
        try {
            return out.available() == 0;
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
