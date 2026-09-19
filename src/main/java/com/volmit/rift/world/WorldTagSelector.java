package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class WorldTagSelector {
    private final Map<String, List<String>> tags = new HashMap<>();

    public WorldTagSelector(Collection<WorldProfile> profiles) {
        for (WorldProfile profile : profiles) {
            tags.put(profile.key(), List.copyOf(profile.getTags()));
        }
    }

    public List<String> tags(String world) {
        return tags.getOrDefault(world.toLowerCase(Locale.ROOT), List.of());
    }

    public List<WorldSnapshot> select(List<WorldSnapshot> worlds, String tag) {
        String filter = normalize(tag);
        List<WorldSnapshot> selected = new ArrayList<>();
        for (WorldSnapshot world : worlds) {
            if (filter.equals("all") || tags(world.name()).contains(filter)) {
                selected.add(world);
            }
        }
        selected.sort(Comparator.comparing(WorldSnapshot::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(selected);
    }

    public Map<String, List<WorldSnapshot>> group(List<WorldSnapshot> worlds, String tag) {
        String filter = normalize(tag);
        Map<String, List<WorldSnapshot>> groups = new TreeMap<>();
        for (WorldSnapshot world : select(worlds, filter)) {
            List<String> names = filter.equals("all") ? tags(world.name()) : List.of(filter);
            if (names.isEmpty()) {
                names = List.of("");
            }
            for (String name : names) {
                groups.computeIfAbsent(name, ignored -> new ArrayList<>()).add(world);
            }
        }
        return groups;
    }

    public static String normalize(String tag) {
        String normalized = tag == null ? "all" : tag.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "all";
        }
        if (!normalized.matches("[a-z0-9_.:-]+")) {
            throw new IllegalArgumentException("Tag must contain only letters, numbers, underscores, dots, colons, or hyphens");
        }
        return normalized;
    }
}
