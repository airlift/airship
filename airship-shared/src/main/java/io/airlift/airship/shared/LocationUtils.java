package io.airlift.airship.shared;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class LocationUtils
{
    public static String extractMachineId(String location)
    {
        return extractMachineId(null);
    }

    public static String extractMachineId(String location, String defaultMachineId)
    {
        Preconditions.checkNotNull(location, "location is null");
        List<String> parts = ImmutableList.copyOf(Splitter.on('/').trimResults().omitEmptyStrings().split(location));
        if (parts.size() < 2) {
            return defaultMachineId;
        }
        return parts.get(parts.size() - 2);
    }
}
