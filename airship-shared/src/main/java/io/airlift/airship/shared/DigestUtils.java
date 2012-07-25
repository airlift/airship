package com.proofpoint.galaxy.shared;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;

import java.security.MessageDigest;

public class DigestUtils
{
    static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String md5Hex(String data)
    {
        try {
            byte[] digest = ByteStreams.getDigest(ByteStreams.newInputStreamSupplier(data.getBytes(Charsets.UTF_8)), MessageDigest.getInstance("MD5"));
            return toHex(digest);
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static String toHex(byte[] data) {
        char[] chars = new char[data.length * 2];

        int charIndex = 0;
        for (byte b : data) {
            chars[charIndex++] = HEX[(0xF0 & b) >>> 4];
            chars[charIndex++] = HEX[(0x0F & b)];
        }
        return new String(chars);
    }
}
