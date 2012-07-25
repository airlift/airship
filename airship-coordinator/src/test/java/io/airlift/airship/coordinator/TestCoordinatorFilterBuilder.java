package io.airlift.airship.coordinator;

import com.google.common.base.Predicate;
import io.airlift.airship.coordinator.CoordinatorFilterBuilder.HostPredicate;
import io.airlift.airship.coordinator.CoordinatorFilterBuilder.MachinePredicate;
import io.airlift.airship.coordinator.CoordinatorFilterBuilder.StatePredicate;
import io.airlift.airship.coordinator.CoordinatorFilterBuilder.UuidPredicate;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.MockUriInfo;
import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.airship.shared.CoordinatorLifecycleState.OFFLINE;
import static io.airlift.airship.shared.CoordinatorLifecycleState.ONLINE;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestCoordinatorFilterBuilder
{
    CoordinatorStatus status = new CoordinatorStatus("coordinator-id",
            ONLINE,
            "coordinator-instance",
            URI.create("internal://10.0.0.1"),
            URI.create("external://localhost"),
            "/unknown/location",
            "instance.type");

    private Predicate<CoordinatorStatus> buildFilter(String key, String value)
    {
        return CoordinatorFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value));
    }

    @Test
    public void testAll()
    {
        assertTrue(CoordinatorFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=online")).apply(status));
        assertTrue(CoordinatorFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=offline")).apply(status));
        assertTrue(CoordinatorFilterBuilder.build(MockUriInfo.from("fake://localhost?all&host=host")).apply(status));
    }

    @Test
    public void testUuidPredicate()
    {
        assertTrue(new UuidPredicate("coordinator-id").apply(status));
        assertTrue(buildFilter("uuid", "coordinator-id").apply(status));
        assertFalse(new UuidPredicate("unknown").apply(status));
        assertFalse(buildFilter("uuid", "unknown").apply(status));
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

    @Test
    public void testMachineSpecPredicate()
    {
        assertTrue(new MachinePredicate("coordinator-instance").apply(status));
        assertTrue(buildFilter("machine", "coordinator-instance").apply(status));
        assertTrue(new MachinePredicate("coo*").apply(status));
        assertTrue(buildFilter("machine", "coo*").apply(status));

        assertFalse(new MachinePredicate("COORDINATOR-INSTANCE").apply(status));
        assertFalse(buildFilter("machine", "COORDINATOR-INSTANCE").apply(status));
        assertFalse(new MachinePredicate("COO*").apply(status));
        assertFalse(buildFilter("machine", "COO*").apply(status));
    }
}
