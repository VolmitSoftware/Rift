package com.volmit.rift.world;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.WorldCreator;

import java.util.Objects;

public final class PlatformCapabilities {
    private final boolean folia;
    private final boolean namespacedWorldStorage;

    public PlatformCapabilities(Server server) {
        Server activeServer = Objects.requireNonNull(server, "server");
        this.folia = FoliaScheduler.isFoliaThreading(activeServer);
        this.namespacedWorldStorage = detectsNamespacedWorldStorage(activeServer);
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean supportsDynamicWorldLifecycle() {
        return !folia;
    }

    public boolean supportsNamespacedWorldStorage() {
        return namespacedWorldStorage;
    }

    private static boolean detectsNamespacedWorldStorage(Server server) {
        try {
            server.getClass().getMethod("getLevelDirectory");
            WorldCreator.class.getMethod("ofKey", NamespacedKey.class);
            return true;
        } catch (NoSuchMethodException | SecurityException exception) {
            return false;
        }
    }
}
