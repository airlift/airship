package io.airlift.airship.configbundler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.airlift.json.JsonCodec;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class Metadata
{
    private final String groupId;
    private final Repository snapshotsRepository;
    private final Repository releasesRepository;

    @JsonCreator
    public Metadata(@JsonProperty("groupId") String groupId,
            @JsonProperty("snapshotsRepository") Repository snapshotsRepository,
            @JsonProperty("releasesRepository") Repository releasesRepository)
    {
        this.groupId = groupId;
        this.snapshotsRepository = snapshotsRepository;
        this.releasesRepository = releasesRepository;
    }

    @JsonProperty
    public String getGroupId()
    {
        return groupId;
    }

    @JsonProperty
    public Repository getSnapshotsRepository()
    {
        return snapshotsRepository;
    }

    @JsonProperty
    public Repository getReleasesRepository()
    {
        return releasesRepository;
    }

    public void save(File file)
            throws IOException
    {
        String json = JsonCodec.jsonCodec(Metadata.class).toJson(this);
        Files.write(json + "\n", file, Charsets.UTF_8);
    }

    public static class Repository
    {
        private final String id;
        private final String uri;

        @JsonCreator
        public static Repository getRepository(@JsonProperty("id") String id, @JsonProperty("uri") String uri)
        {
            if (id == null) {
                return null;
            }

            return new Repository(id, uri);
        }

        private Repository(@JsonProperty("id") String id, @JsonProperty("uri") String uri)
        {
            this.id = checkNotNull(id, "id is null");
            this.uri = checkNotNull(uri, "uri is null");
        }

        @JsonProperty("id")
        public String getId()
        {
            return id;
        }

        @JsonProperty("uri")
        public String getUri()
        {
            return uri;
        }
    }
}
