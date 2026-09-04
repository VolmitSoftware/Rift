package com.volmit.rift.storage;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public final class RiftPaths {
    private final File dataFolder;
    private final Path worldContainer;

    public RiftPaths(File dataFolder, File worldContainer) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.worldContainer = Objects.requireNonNull(worldContainer, "worldContainer").toPath().toAbsolutePath().normalize();
    }

    public File configFile() {
        return new File(dataFolder, "config.toml");
    }

    public File languagesDirectory() {
        return new File(dataFolder, "languages");
    }

    public File profilesDirectory() {
        return new File(dataFolder, "worlds");
    }

    public File trashManifestDirectory() {
        return new File(dataFolder, "trash");
    }

    public File debugDirectory() {
        return new File(dataFolder, "debug");
    }

    public Path worldContainer() {
        return worldContainer;
    }

    public Path quarantineDirectory() {
        return worldContainer.resolve(".rift-trash");
    }
}
