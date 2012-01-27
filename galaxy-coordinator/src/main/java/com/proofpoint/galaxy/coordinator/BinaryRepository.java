package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.BinarySpec;

import java.net.URI;

public interface BinaryRepository
{
    BinarySpec resolveBinarySpec(BinarySpec binarySpec);
    URI getBinaryUri(BinarySpec binarySpec);
}
