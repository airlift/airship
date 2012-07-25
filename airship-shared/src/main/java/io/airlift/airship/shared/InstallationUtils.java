package com.proofpoint.galaxy.shared;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.InputSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import static com.proofpoint.galaxy.shared.ConfigUtils.newConfigEntrySupplier;

public class InstallationUtils
{
    public static Installation toInstallation(Repository repository, Assignment assignment)
    {
        assignment = resolveAssignment(repository, assignment);


        // load resources
        Map<String, Integer> resources = readResources(repository, assignment);

        // create installation
        URI binaryUri = repository.binaryToHttpUri(assignment.getBinary());
        Preconditions.checkNotNull(binaryUri, "Unknown binary %s", binaryUri);
        URI configUri = repository.configToHttpUri(assignment.getConfig());
        Preconditions.checkNotNull(configUri, "Unknown config %s", configUri);
        return new Installation(
                repository.configShortName(assignment.getConfig()),
                assignment,
                binaryUri,
                configUri,
                resources);
    }

    public static Assignment resolveAssignment(Repository repository, Assignment assignment)
    {
        // resolve assignment
        String resolvedBinary = repository.binaryResolve(assignment.getBinary());
        Preconditions.checkArgument(resolvedBinary != null, "Unknown binary " + assignment.getBinary());

        String resolvedConfig = repository.configResolve(assignment.getConfig());
        Preconditions.checkArgument(resolvedConfig != null, "Unknown config " + assignment.getConfig());

        assignment = new Assignment(resolvedBinary, resolvedConfig);
        return assignment;
    }

    public static boolean resourcesAreAvailable(Map<String, Integer> availableResources, Map<String, Integer> requiredResources)
    {
        for (Entry<String, Integer> entry : requiredResources.entrySet()) {
            int available = Objects.firstNonNull(availableResources.get(entry.getKey()), 0);
            if (available < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static Map<String, Integer> getAvailableResources(AgentStatus agentStatus)
    {
        Map<String, Integer> availableResources = new TreeMap<String, Integer>(agentStatus.getResources());
        for (SlotStatus slotStatus : agentStatus.getSlotStatuses()) {
            for (Entry<String, Integer> entry : slotStatus.getResources().entrySet()) {
                int value = Objects.firstNonNull(availableResources.get(entry.getKey()), 0);
                availableResources.put(entry.getKey(), value - entry.getValue());
            }
        }
        return availableResources;
    }

    public static Map<String, Integer> readResources(Repository repository, Assignment assignment)
    {
        ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();

        InputSupplier<? extends InputStream> resourcesFile = newConfigEntrySupplier(repository, assignment.getConfig(), "galaxy-resources.properties");
        if (resourcesFile != null) {
            try {
                Properties resources = new Properties();
                resources.load(resourcesFile.getInput());
                for (Entry<Object, Object> entry : resources.entrySet()) {
                    builder.put((String) entry.getKey(), Integer.valueOf((String) entry.getValue()));
                }
            }
            catch (IOException ignored) {
            }
        }
        return builder.build();
    }
}
