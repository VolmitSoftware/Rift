package com.volmit.rift.placeholder;

import com.volmit.rift.world.WorldSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RiftPlaceholderValues {
    private static final List<String> WORLD_FIELDS = List.of(
            "name", "loaded", "managed", "protected", "autoload", "generator", "environment", "busy");

    private final String version;
    private final Map<UUID, String> playerWorlds = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot = new Snapshot(Map.of(), 0, 0, 0, 0);

    public RiftPlaceholderValues(String version) {
        this.version = version;
    }

    public void install(List<WorldSnapshot> worlds, Set<String> busyWorlds, int operations) {
        Map<String, WorldValues> values = new LinkedHashMap<>();
        int loaded = 0;
        int managed = 0;
        int missing = 0;
        for (WorldSnapshot world : worlds) {
            String key = world.name().toLowerCase(Locale.ROOT);
            values.put(key, new WorldValues(world, busyWorlds.contains(key)));
            loaded += world.loaded() ? 1 : 0;
            managed += world.managed() ? 1 : 0;
            missing += world.managed() && !world.loaded() && !world.presentOnDisk() ? 1 : 0;
        }
        snapshot = new Snapshot(Map.copyOf(values), loaded, managed, missing, operations);
    }

    public void playerWorld(UUID player, String world) {
        playerWorlds.put(player, world.toLowerCase(Locale.ROOT));
    }

    public void removePlayer(UUID player) {
        playerWorlds.remove(player);
    }

    public void clear() {
        playerWorlds.clear();
        snapshot = new Snapshot(Map.of(), 0, 0, 0, 0);
    }

    public String resolve(UUID player, String parameter) {
        if (parameter == null) {
            return null;
        }
        String requested = parameter.toLowerCase(Locale.ROOT);
        Snapshot current = snapshot;
        return switch (requested) {
            case "version" -> version;
            case "worlds_known" -> Integer.toString(current.worlds().size());
            case "worlds_loaded" -> Integer.toString(current.loaded());
            case "worlds_managed" -> Integer.toString(current.managed());
            case "worlds_missing" -> Integer.toString(current.missing());
            case "operations_active" -> Integer.toString(current.operations());
            default -> resolveWorld(current, player, requested);
        };
    }

    private String resolveWorld(Snapshot current, UUID player, String requested) {
        for (String field : WORLD_FIELDS) {
            String prefix = "world_" + field;
            String worldName;
            if (requested.equals(prefix)) {
                worldName = player == null ? null : playerWorlds.get(player);
            } else if (requested.startsWith(prefix + "_")) {
                worldName = requested.substring(prefix.length() + 1);
                if (worldName.isEmpty()) {
                    return null;
                }
            } else {
                continue;
            }
            WorldValues world = worldName == null ? null : current.worlds().get(worldName);
            return world == null ? "" : world.value(field);
        }
        return null;
    }

    private record Snapshot(Map<String, WorldValues> worlds, int loaded, int managed, int missing, int operations) {
    }

    private record WorldValues(WorldSnapshot world, boolean busy) {
        private String value(String field) {
            return switch (field) {
                case "name" -> world.name();
                case "loaded" -> Boolean.toString(world.loaded());
                case "managed" -> Boolean.toString(world.managed());
                case "protected" -> Boolean.toString(world.protectedWorld());
                case "autoload" -> Boolean.toString(world.autoLoad());
                case "generator" -> world.managed() && world.generator().isBlank() ? "vanilla" : world.generator();
                case "environment" -> world.environment();
                case "busy" -> Boolean.toString(busy);
                default -> null;
            };
        }
    }
}
