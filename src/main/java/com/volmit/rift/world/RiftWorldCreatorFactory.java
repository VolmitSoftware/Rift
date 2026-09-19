package com.volmit.rift.world;

import com.volmit.rift.storage.WorldDirectoryResolver;
import org.bukkit.WorldCreator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class RiftWorldCreatorFactory {
    private final WorldDirectoryResolver directories;

    public RiftWorldCreatorFactory(WorldDirectoryResolver directories) {
        this.directories = Objects.requireNonNull(directories, "directories");
    }

    public WorldCreator forNewWorld(String logicalName) {
        return WorldCreator.ofKey(RiftWorldIdentity.key(logicalName));
    }

    public WorldCreator forStoredWorld(String logicalName, Path directory) throws IOException {
        return WorldCreator.ofKey(directories.worldKey(directory, logicalName));
    }
}
