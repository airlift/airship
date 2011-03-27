package com.proofpoint.galaxy;

import com.proofpoint.galaxy.agent.Installation;

public class InstallationHelper
{
    public static final Installation APPLE_INSTALLATION = new Installation(AssignmentHelper.APPLE_ASSIGNMENT, RepoHelper.MOCK_BINARY_REPO, RepoHelper.MOCK_CONFIG_REPO);
    public static final Installation BANANA_INSTALLATION = new Installation(AssignmentHelper.BANANA_ASSIGNMENT, RepoHelper.MOCK_BINARY_REPO, RepoHelper.MOCK_CONFIG_REPO);
}
