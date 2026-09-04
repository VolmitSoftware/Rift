package com.volmit.rift.world;

public record WorldSnapshot(
        String name,
        boolean loaded,
        boolean managed,
        boolean presentOnDisk,
        boolean autoLoad,
        boolean protectedWorld,
        String environment,
        String generator,
        long seed
) {
}
