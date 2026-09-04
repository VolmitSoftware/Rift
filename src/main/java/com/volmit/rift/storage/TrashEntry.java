package com.volmit.rift.storage;

import java.time.Instant;

public final class TrashEntry {
    private String id = "";
    private String worldName = "";
    private long deletedAtEpochMillis = 0L;
    private String environment = "NORMAL";
    private String generator = "";
    private String worldType = "NORMAL";
    private long seed = 0L;
    private String directory = "";
    private boolean autoLoad = true;
    private boolean protectedWorld = false;

    public static TrashEntry from(String id, WorldProfile profile) {
        TrashEntry entry = new TrashEntry();
        entry.id = id;
        entry.worldName = profile.getName();
        entry.deletedAtEpochMillis = System.currentTimeMillis();
        entry.environment = profile.getEnvironment();
        entry.generator = profile.getGenerator();
        entry.worldType = profile.getWorldType();
        entry.seed = profile.getSeed();
        entry.directory = profile.getDirectory();
        entry.autoLoad = profile.isAutoLoad();
        entry.protectedWorld = profile.isProtectedWorld();
        return entry;
    }

    public WorldProfile toProfile() {
        WorldProfile profile = new WorldProfile();
        profile.setName(worldName);
        profile.setEnvironment(environment);
        profile.setGenerator(generator);
        profile.setWorldType(worldType);
        profile.setSeed(seed);
        profile.setDirectory(directory);
        profile.setAutoLoad(autoLoad);
        profile.setProtectedWorld(protectedWorld);
        return profile;
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
}
