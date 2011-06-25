package com.proofpoint.galaxy.agent;

import com.proofpoint.galaxy.shared.Installation;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

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

        // remove apple: no active deployment
        manager.clear();
        assertNull(manager.getDeployment());

        // remove apple again: no active deployment
        manager.clear();
        assertNull(manager.getDeployment());

        // install banana
        Deployment bananaDeployment = manager.install(bananaInstallation);
        assertNotNull(bananaDeployment);
        assertEquals(manager.getDeployment(), bananaDeployment);
        assertEquals(bananaDeployment.getAssignment(), bananaInstallation.getAssignment());

        // remove banana
        manager.clear();
        assertNull(manager.getDeployment());
    }

    @Test
    public void testInstallOverExisting()
    {
        // no deployment by default
        assertNull(manager.getDeployment());

        // install apple deployment
        Deployment appleDeployment = manager.install(appleInstallation);
        assertNotNull(appleDeployment);
        assertEquals(manager.getDeployment(), appleDeployment);
        assertEquals(appleDeployment.getAssignment(), appleInstallation.getAssignment());

        // install banana over apple deployment
        try {
            manager.install(bananaInstallation);
            fail("Expected IllegalStateException");
        }
        catch (IllegalStateException expected) {
        }

        // remove banana again: no active deployment
        manager.clear();
        assertNull(manager.getDeployment());

    }
}
