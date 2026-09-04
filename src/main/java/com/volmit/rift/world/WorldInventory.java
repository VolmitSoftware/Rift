package com.volmit.rift.world;

import com.volmit.rift.storage.WorldDirectoryResolver;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class WorldInventory {
    private final Plugin plugin;
    private final WorldDirectoryResolver directories;
    private final WorldProfileStore profiles;
    private volatile List<String> discoveredWorlds = List.of();
    private volatile Set<String> discoveredKeys = Set.of();
    private volatile Map<String, Path> discoveredDirectories = Map.of();

    public WorldInventory(Plugin plugin, WorldDirectoryResolver directories, WorldProfileStore profiles) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.directories = Objects.requireNonNull(directories, "directories");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    public List<WorldSnapshot> snapshots() {
        Map<String, WorldSnapshot> snapshots = new LinkedHashMap<>();
        for (WorldProfile profile : profiles.all()) {
            World world = Bukkit.getWorld(profile.getName());
            boolean onDisk = world != null || isWorldDirectory(profile.getName());
            snapshots.put(profile.key(), from(profile, world, onDisk));
        }
        for (World world : Bukkit.getWorlds()) {
            String key = world.getName().toLowerCase(Locale.ROOT);
            snapshots.putIfAbsent(key, from(null, world, true));
        }
        for (String discovered : discoveredWorlds) {
            String key = discovered.toLowerCase(Locale.ROOT);
            snapshots.putIfAbsent(key, new WorldSnapshot(
                    discovered, false, false, true, false, false, "unknown", "", 0L
            ));
        }
        List<WorldSnapshot> result = new ArrayList<>(snapshots.values());
        result.sort(Comparator.comparing(WorldSnapshot::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public Optional<WorldSnapshot> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return snapshots().stream().filter(snapshot -> snapshot.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<String> discoverWorldDirectories() {
        return discoveredWorlds;
    }

    public Optional<Path> discoveredDirectory(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(discoveredDirectories.get(name.toLowerCase(Locale.ROOT)));
    }

    public synchronized void markPresent(String name) {
        if (name == null || isWorldDirectory(name)) {
            return;
        }
        List<String> updated = new ArrayList<>(discoveredWorlds);
        updated.add(name);
        updated.sort(String.CASE_INSENSITIVE_ORDER);
        discoveredWorlds = List.copyOf(updated);
        discoveredKeys = updated.stream()
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        try {
            Optional<Path> directory = directories.find(name);
            if (directory.isPresent()) {
                Map<String, Path> updatedDirectories = new LinkedHashMap<>(discoveredDirectories);
                updatedDirectories.put(name.toLowerCase(Locale.ROOT), directory.get());
                discoveredDirectories = Map.copyOf(updatedDirectories);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to resolve new world storage for " + name, exception);
        }
    }

    public synchronized void markAbsent(String name) {
        if (name == null || !isWorldDirectory(name)) {
            return;
        }
        List<String> updated = discoveredWorlds.stream()
                .filter(entry -> !entry.equalsIgnoreCase(name))
                .toList();
        discoveredWorlds = List.copyOf(updated);
        discoveredKeys = updated.stream()
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        Map<String, Path> updatedDirectories = new LinkedHashMap<>(discoveredDirectories);
        updatedDirectories.remove(name.toLowerCase(Locale.ROOT));
        discoveredDirectories = Map.copyOf(updatedDirectories);
    }

    public void refreshIfChanged() {
        refreshDiskSnapshot();
    }

    public synchronized void refreshDiskSnapshot() {
        List<String> discovered;
        try {
            List<WorldDirectoryResolver.DiscoveredWorld> resolved = directories.discover();
            discovered = resolved.stream().map(WorldDirectoryResolver.DiscoveredWorld::name).toList();
            Map<String, Path> locations = new LinkedHashMap<>();
            for (WorldDirectoryResolver.DiscoveredWorld world : resolved) {
                locations.put(world.name().toLowerCase(Locale.ROOT), world.directory());
            }
            discoveredDirectories = Map.copyOf(locations);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to scan Bukkit world storage", exception);
            return;
        }
        List<String> snapshot = List.copyOf(discovered);
        discoveredWorlds = snapshot;
        discoveredKeys = snapshot.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isWorldDirectory(String name) {
        return name != null && discoveredKeys.contains(name.toLowerCase(Locale.ROOT));
    }

    private static WorldSnapshot from(WorldProfile profile, World world, boolean onDisk) {
        if (profile != null) {
            return new WorldSnapshot(
                    profile.getName(),
                    world != null,
                    true,
                    onDisk,
                    profile.isAutoLoad(),
                    profile.isProtectedWorld(),
                    world == null ? profile.getEnvironment() : world.getEnvironment().name(),
                    profile.getGenerator(),
                    world == null ? profile.getSeed() : world.getSeed()
            );
        }
        return new WorldSnapshot(
                world.getName(), true, false, onDisk, false, false,
                world.getEnvironment().name(), "", world.getSeed()
        );
    }
}
