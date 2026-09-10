package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class RiftWorldIdentity {
    public static final String NAMESPACE = "rift";

    private RiftWorldIdentity() {
    }

    public static NamespacedKey key(String logicalName) {
        String value = NAMESPACE + ":"
                + Objects.requireNonNull(logicalName, "logicalName").toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(value);
        if (key == null) {
            throw new IllegalArgumentException("Invalid Rift world key: " + value);
        }
        return key;
    }

    public static World findLoaded(String logicalName) {
        if (logicalName == null || logicalName.isBlank()) {
            return null;
        }
        NamespacedKey expected = key(logicalName);
        for (World world : Bukkit.getWorlds()) {
            if (expected.equals(worldKey(world))) {
                return world;
            }
        }
        return Bukkit.getWorld(logicalName);
    }

    public static Optional<WorldProfile> profile(World world, WorldProfileStore profiles) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(profiles, "profiles");
        NamespacedKey key = worldKey(world);
        if (isRiftKey(key)) {
            return profiles.find(key.getKey());
        }
        return profiles.find(world.getName());
    }

    public static String logicalName(World world, WorldProfileStore profiles) {
        return profile(world, profiles)
                .map(WorldProfile::getName)
                .orElseGet(() -> {
                    NamespacedKey key = worldKey(world);
                    return isRiftKey(key) ? key.getKey() : world.getName();
                });
    }

    public static boolean isRiftWorld(World world) {
        return world != null && isRiftKey(worldKey(world));
    }

    private static NamespacedKey worldKey(World world) {
        return world.getKey();
    }

    private static boolean isRiftKey(NamespacedKey key) {
        return key != null && key.getNamespace().equals(NAMESPACE);
    }
}
