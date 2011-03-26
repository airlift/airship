package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.BinarySpec;

import java.net.URI;

public class MockBinaryRepository implements BinaryRepository
{
    public URI getBinaryUri(BinarySpec binarySpec)
    {
        return URI.create("fake://localhost/" + binarySpec);
    }
}
