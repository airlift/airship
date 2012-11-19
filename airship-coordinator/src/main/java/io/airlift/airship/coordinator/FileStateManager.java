package io.airlift.airship.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.PatternFilenameFilter;
import io.airlift.airship.shared.ExpectedSlotStatus;
import io.airlift.airship.shared.FileUtils;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;

public class FileStateManager implements StateManager
{
    private static final Logger log = Logger.get(FileStateManager.class);
    private final File dataDir;
    private final JsonCodec<ExpectedSlotStatus> codec;

    @Inject
    public FileStateManager(FileStateManagerConfig fileStateManagerConfig, JsonCodec<ExpectedSlotStatus> codec)
    {
        this(new File(checkNotNull(fileStateManagerConfig, "fileStateManagerConfig is null").getExpectedStateDir()), codec);
    }

    public FileStateManager(File dataDir, JsonCodec<ExpectedSlotStatus> codec)
    {
        Preconditions.checkNotNull(dataDir, "dataDir is null");
        Preconditions.checkNotNull(codec, "codec is null");
        this.dataDir = dataDir;
        this.codec = codec;

        dataDir.mkdirs();
        Preconditions.checkArgument(dataDir.isDirectory(), "dataDir is not a directory");
    }

    @Override
    public Collection<ExpectedSlotStatus> getAllExpectedStates()
    {
        List<ExpectedSlotStatus> slots = newArrayList();
        for (File file : FileUtils.listFiles(dataDir, new PatternFilenameFilter("[^\\.].*\\.json"))) {
            try {
                String json = Files.toString(file, Charsets.UTF_8);
                ExpectedSlotStatus expectedSlotStatus = codec.fromJson(json);
                slots.add(expectedSlotStatus);
            }
            catch (Exception e) {
                // skip corrupted entries... these will be marked as unexpected
                // and someone will resolve the conflict (and overwrite the corrupted record)
            }
        }
        return slots;
    }

    @Override
    public void deleteExpectedState(UUID slotId)
    {
         new File(dataDir, slotId.toString() + ".json").delete();
    }

    @Override
    public void setExpectedState(ExpectedSlotStatus slotStatus)
    {
        Preconditions.checkNotNull(slotStatus, "slotStatus is null");
        try {
            Files.write(codec.toJson(slotStatus), new File(dataDir, slotStatus.getId().toString() + ".json"), Charsets.UTF_8);
        }
        catch (Exception e) {
            log.error(e, "Error writing expected slot status");
        }
    }
}
