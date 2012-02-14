package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.proofpoint.json.JsonCodec;
import org.codehaus.jackson.annotate.JsonProperty;

import java.io.File;
import java.io.IOException;

public class Metadata
{
    private final String groupId;
    private final String repositoryId;

    public Metadata(@JsonProperty("groupId") String groupId,
            @JsonProperty("repository") String repositoryId)
    {
        this.groupId = groupId;
        this.repositoryId = repositoryId;
    }

    @JsonProperty
    public String getGroupId()
    {
        return groupId;
    }

    @JsonProperty
    public String getRepository()
    {
        return repositoryId;
    }

    public void save(File file)
            throws IOException
    {
        String json = JsonCodec.jsonCodec(Metadata.class).toJson(this);
        Files.write(json + "\n", file, Charsets.UTF_8);
    }
    
    public static Metadata load(File file)
            throws IOException
    {
        String json = Files.toString(file, Charsets.UTF_8);
        return JsonCodec.jsonCodec(Metadata.class).fromJson(json);
    }
}
