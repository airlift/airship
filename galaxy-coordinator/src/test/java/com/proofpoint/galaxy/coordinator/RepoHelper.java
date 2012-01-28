package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.MavenCoordinates;

import java.net.URI;

public class RepoHelper
{
    public static final Repository MOCK_REPO = new Repository()
    {
        @Override
        public URI getUri(MavenCoordinates binarySpec)
        {
            return URI.create("fake://localhost/" + binarySpec);
        }

        @Override
        public MavenCoordinates resolve(MavenCoordinates binarySpec)
        {
            return binarySpec;
        }
    };
}
