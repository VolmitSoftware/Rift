package com.volmit.rift.world;

import java.nio.file.Path;

public record WorldCheck(
        String name,
        String key,
        Path storagePath,
        String storageLayout,
        boolean loaded,
        boolean managed,
        boolean presentOnDisk,
        boolean autoLoad,
        boolean protectedWorld,
        boolean primaryWorld,
        boolean operationActive,
        int players,
        String environment,
        String generator,
        boolean generatorAvailable,
        boolean writable,
        boolean ready
) {
}
