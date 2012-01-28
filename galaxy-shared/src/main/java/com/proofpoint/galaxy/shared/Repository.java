package com.proofpoint.galaxy.shared;

import java.net.URI;

public interface Repository
{
    MavenCoordinates resolve(MavenCoordinates binarySpec);
    URI getUri(MavenCoordinates binarySpec);
}
