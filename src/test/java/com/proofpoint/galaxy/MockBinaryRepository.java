package com.proofpoint.galaxy;

import java.net.URI;

class MockBinaryRepository implements BinaryRepository
{
    public URI getBinaryUri(BinarySpec binarySpec)
    {
        return URI.create("fake://localhost/" + binarySpec);
    }
}
