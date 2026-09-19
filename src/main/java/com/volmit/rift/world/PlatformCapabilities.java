package com.volmit.rift.world;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Server;

import java.util.Objects;

public final class PlatformCapabilities {
    private final boolean folia;

    public PlatformCapabilities(Server server) {
        Server activeServer = Objects.requireNonNull(server, "server");
        this.folia = FoliaScheduler.isFoliaThreading(activeServer);
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean supportsDynamicWorldLifecycle() {
        return !folia;
    }

}
