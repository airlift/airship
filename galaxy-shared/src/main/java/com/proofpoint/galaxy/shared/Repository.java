package com.proofpoint.galaxy.shared;

import java.net.URI;

public interface Repository
{
    BinarySpec resolve(BinarySpec binarySpec);
    URI getUri(BinarySpec binarySpec);
}
