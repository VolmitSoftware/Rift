package com.volmit.rift.world;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldOperationLocks {
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public boolean acquire(String worldName) {
        return active.add(key(worldName));
    }

    public void release(String worldName) {
        active.remove(key(worldName));
    }

    public boolean isActive(String worldName) {
        return active.contains(key(worldName));
    }

    private static String key(String worldName) {
        return worldName == null ? "" : worldName.toLowerCase(Locale.ROOT);
    }
}
