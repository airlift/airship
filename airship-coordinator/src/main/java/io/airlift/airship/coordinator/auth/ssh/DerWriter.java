package io.airlift.airship.coordinator.auth.ssh;

import com.google.common.base.Throwables;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public class DerWriter
{
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public DerWriter writeEntry(DerType tag, byte[] data)
    {
        try {
            out.write(tag.getValue());
            writeLength(data.length);
            out.write(data);
            return this;
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public DerWriter writeInteger(BigInteger data)
    {
        return writeInteger(data.toByteArray());
    }

    public DerWriter writeInteger(byte[] data)
    {
        return writeEntry(DerType.INTEGER, data);
    }

    public byte[] toByteArray()
    {
        return out.toByteArray();
    }

    private void writeLength(int length)
    {
        if (length < (1 << 7) && length >= 0) {
            // single byte (no length)
            out.write((byte) length);
        }
        else if (length < (1 << 8) && length > 0) {
            // length + one byte number
            out.write((byte) 0x81);
            out.write((byte) length);
        }
        else if (length < (1 << 16) && length > 0) {
            // length + two byte number
            out.write((byte) 0x82);
            out.write((byte) (length >>> 8));
            out.write((byte) length);
        }
        else if (length < (1 << 24) && length > 0) {
            // length + three byte number
            out.write((byte) 0x83);
            out.write((byte) (length >>> 16));
            out.write((byte) (length >>> 8));
            out.write((byte) length);
        }
        else {
            // length + four byte number
            out.write((byte) 0x84);
            out.write((byte) (length >>> 24));
            out.write((byte) (length >>> 16));
            out.write((byte) (length >>> 8));
            out.write((byte) length);
        }
    }
}
