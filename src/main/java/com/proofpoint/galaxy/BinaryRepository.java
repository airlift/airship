package com.proofpoint.galaxy;

import java.net.URI;

public interface BinaryRepository
{
    URI getBinaryUri(BinarySpec binarySpec);
}
