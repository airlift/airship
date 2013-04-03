package io.airlift.airship.agent;

import io.airlift.airship.shared.Installation;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public abstract class AbstractDeploymentManagerTest
{
    protected DeploymentManager manager;
    protected Installation appleInstallation;
    protected Installation bananaInstallation;

    @Test
    public void testInstall()
    {
        // no deployment by default
        assertNull(manager.getDeployment());

        // install apple deployment
        Deployment appleDeployment = manager.install(appleInstallation);
        assertNotNull(appleDeployment);
        assertEquals(manager.getDeployment(), appleDeployment);
        assertEquals(appleDeployment.getAssignment(), appleInstallation.getAssignment());

        // install banana
        Deployment bananaDeployment = manager.install(bananaInstallation);
        assertNotNull(bananaDeployment);
        assertEquals(manager.getDeployment(), bananaDeployment);
        assertEquals(bananaDeployment.getAssignment(), bananaInstallation.getAssignment());
    }
}
