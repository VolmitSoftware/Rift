package com.volmit.rift.storage;

import art.arcane.volmlib.util.config.ConfigJson;
import art.arcane.volmlib.util.config.TomlCodec;

import java.io.IOException;
import java.time.Instant;

public final class TrashEntry {
    private String id = "";
    private String worldName = "";
    private long deletedAtEpochMillis = 0L;
    private WorldProfile profile = null;

    public static TrashEntry from(String id, WorldProfile profile) {
        TrashEntry entry = new TrashEntry();
        entry.id = id;
        entry.worldName = profile.getName();
        entry.deletedAtEpochMillis = System.currentTimeMillis();
        entry.profile = copy(profile);
        return entry;
    }

    public static TrashEntry parse(String raw) throws IOException {
        TrashEntry entry = TomlCodec.fromToml(raw, TrashEntry.class);
        if (entry == null) {
            throw new IOException("Trash manifest is empty");
        }
        if (entry.profile == null) {
            // Manifests written before the full profile was stored keep its fields at the top level.
            entry.profile = TomlCodec.fromToml(raw, WorldProfile.class);
            if (entry.profile == null) {
                throw new IOException("Trash manifest has no world profile");
            }
        }
        entry.profile.setName(entry.worldName);
        return entry;
    }

    public WorldProfile toProfile() {
        WorldProfile restored = profile == null ? new WorldProfile() : copy(profile);
        restored.setName(worldName);
        return restored;
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getDeletedAtEpochMillis() {
        return deletedAtEpochMillis;
    }

    public Instant deletedAt() {
        return Instant.ofEpochMilli(deletedAtEpochMillis);
    }

    private static WorldProfile copy(WorldProfile profile) {
        return ConfigJson.fromJson(ConfigJson.toJson(profile, false), WorldProfile.class);
    }
}
