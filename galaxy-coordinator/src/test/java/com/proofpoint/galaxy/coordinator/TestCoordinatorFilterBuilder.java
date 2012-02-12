package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Predicate;
import com.proofpoint.galaxy.coordinator.CoordinatorFilterBuilder.HostPredicate;
import com.proofpoint.galaxy.coordinator.CoordinatorFilterBuilder.StatePredicate;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.MockUriInfo;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.galaxy.shared.CoordinatorLifecycleState.OFFLINE;
import static com.proofpoint.galaxy.shared.CoordinatorLifecycleState.ONLINE;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestCoordinatorFilterBuilder
{
    CoordinatorStatus status = new CoordinatorStatus("coordinator-id",
            ONLINE,
            URI.create("internal://10.0.0.1"),
            URI.create("external://localhost"),
            "unknown/location",
            "instance.type");

    private Predicate<CoordinatorStatus> buildFilter(String key, String value)
    {
        return CoordinatorFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value));
    }

    @Test
    public void testStateSpecPredicate()
    {
        assertTrue(new StatePredicate(ONLINE).apply(status));
        assertTrue(buildFilter("state", "online").apply(status));
        assertFalse(new StatePredicate(OFFLINE).apply(status));
        assertFalse(buildFilter("state", "offline").apply(status));
    }

    @Test
    public void testHostSpecPredicate()
    {
        assertTrue(new HostPredicate("localhost").apply(status));
        assertTrue(buildFilter("host", "localhost").apply(status));
        assertTrue(new HostPredicate("LOCALHOST").apply(status));
        assertTrue(buildFilter("host", "LOCALHOST").apply(status));
        assertTrue(new HostPredicate("LocalHost").apply(status));
        assertTrue(buildFilter("host", "LocalHost").apply(status));
        assertTrue(new HostPredicate("local*").apply(status));
        assertTrue(buildFilter("host", "local*").apply(status));
        assertTrue(new HostPredicate("LocAL*").apply(status));
        assertTrue(buildFilter("host", "LocAL*").apply(status));
        assertFalse(new HostPredicate("foo").apply(status));
        assertFalse(buildFilter("host", "foo").apply(status));

        assertTrue(new HostPredicate("127.0.0.1").apply(status));
        assertTrue(buildFilter("host", "127.0.0.1").apply(status));
        assertTrue(new HostPredicate("10.0.0.1").apply(status));
        assertTrue(buildFilter("host", "10.0.0.1").apply(status));
        assertFalse(new HostPredicate("10.1.2.3").apply(status));
        assertFalse(buildFilter("host", "10.1.2.3").apply(status));
    }
}
