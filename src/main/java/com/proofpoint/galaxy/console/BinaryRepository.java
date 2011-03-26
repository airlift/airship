package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.BinarySpec;

import java.net.URI;

public interface BinaryRepository
{
    URI getBinaryUri(BinarySpec binarySpec);
}
