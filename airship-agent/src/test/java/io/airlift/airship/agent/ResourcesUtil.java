package io.airlift.airship.agent;

import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class ResourcesUtil
{
    private ResourcesUtil()
    {
    }

    public static Map<String, Integer> TEST_RESOURCES = ImmutableMap.<String, Integer>builder()
            .put("cpu", 8)
            .put("memory", 1024)
            .build();

    public static void writeResources(Map<String, Integer> resources, File resourcesFile)
            throws IOException
    {
        Properties properties = new Properties();
        for (Entry<String, Integer> entry : resources.entrySet()) {
            properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        resourcesFile.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(resourcesFile);
        try {
            properties.store(out, "");
        }
        finally {
            out.close();
        }
    }
}
