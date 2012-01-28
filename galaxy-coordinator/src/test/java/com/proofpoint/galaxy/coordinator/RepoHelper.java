package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.BinarySpec;

import java.net.URI;

public class RepoHelper
{
    public static final Repository MOCK_REPO = new Repository()
    {
        @Override
        public URI getUri(BinarySpec binarySpec)
        {
            return URI.create("fake://localhost/" + binarySpec);
        }

        @Override
        public BinarySpec resolve(BinarySpec binarySpec)
        {
            return binarySpec;
        }
    };
}
