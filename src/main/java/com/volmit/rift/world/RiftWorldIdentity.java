package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RiftWorldIdentity {
    public static final String NAMESPACE = "rift";
    private static final Set<String> PRIMARY_KEYS = Set.of("overworld", "the_nether", "the_end");

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

    /**
     * The dimension key a managed profile owns, read from its recorded {@code dimensions/<namespace>/<key>}
     * storage folder. Empty when the profile has no recorded folder yet.
     */
    public static Optional<NamespacedKey> recordedKey(WorldProfile profile) {
        String directory = Objects.requireNonNull(profile, "profile").getDirectory();
        if (directory == null || directory.isBlank()) {
            return Optional.empty();
        }
        String[] segments = directory.replace('\\', '/').split("/");
        int length = segments.length;
        if (length < 3 || !segments[length - 3].equals("dimensions")) {
            return Optional.empty();
        }
        return Optional.ofNullable(NamespacedKey.fromString(segments[length - 2] + ":" + segments[length - 1]));
    }

    public static boolean owns(WorldProfile profile, NamespacedKey key) {
        if (key == null) {
            return false;
        }
        Optional<NamespacedKey> recorded = recordedKey(profile);
        if (recorded.isPresent()) {
            return recorded.get().equals(key);
        }
        return key.getKey().equals(profile.getName().toLowerCase(Locale.ROOT))
                && (key.getNamespace().equals(NAMESPACE) || key.getNamespace().equals(NamespacedKey.MINECRAFT));
    }

    public static World findLoaded(WorldProfile profile) {
        Optional<NamespacedKey> recorded = recordedKey(profile);
        return recorded.isPresent() ? Bukkit.getWorld(recorded.get()) : findAnyLoaded(profile.getName());
    }

    /**
     * Resolves a command name to a loaded world. A managed name only ever resolves to the dimension its profile
     * owns; an unmanaged name resolves to {@code rift:<name>} first, then {@code minecraft:<name>}.
     */
    public static World findLoaded(String name, WorldProfileStore profiles) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<WorldProfile> profile = profiles.find(name);
        return profile.isPresent() ? findLoaded(profile.get()) : findAnyLoaded(name);
    }

    /** Any loaded world whose key is this name in the Rift or Minecraft namespace, ignoring profiles. */
    public static World findAnyLoaded(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        World riftWorld = Bukkit.getWorld(key(name));
        if (riftWorld != null) {
            return riftWorld;
        }
        return Bukkit.getWorld(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    }

    public static Optional<WorldProfile> profile(World world, WorldProfileStore profiles) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(profiles, "profiles");
        NamespacedKey key = worldKey(world);
        return profiles.find(key.getKey()).filter(profile -> owns(profile, key));
    }

    public static String logicalName(World world, WorldProfileStore profiles) {
        Optional<WorldProfile> profile = profile(world, profiles);
        if (profile.isPresent()) {
            return profile.get().getName();
        }
        NamespacedKey key = worldKey(world);
        // A managed world in another namespace owns the short name, so this one keeps its full key.
        return profiles.find(key.getKey()).isPresent() ? key.toString() : key.getKey();
    }

    public static boolean isRiftWorld(World world) {
        return world != null && isRiftKey(worldKey(world));
    }

    /** True for the server's own overworld, nether, and end, which Rift never unloads or deletes. */
    public static boolean isPrimary(World world) {
        if (world == null) {
            return false;
        }
        if (isPrimaryKey(worldKey(world))) {
            return true;
        }
        List<World> worlds = Bukkit.getWorlds();
        return !worlds.isEmpty() && worlds.get(0).equals(world);
    }

    public static boolean isPrimaryKey(NamespacedKey key) {
        return key != null && key.getNamespace().equals(NamespacedKey.MINECRAFT) && PRIMARY_KEYS.contains(key.getKey());
    }

    private static NamespacedKey worldKey(World world) {
        return world.getKey();
    }

    private static boolean isRiftKey(NamespacedKey key) {
        return key != null && key.getNamespace().equals(NAMESPACE);
    }
}
