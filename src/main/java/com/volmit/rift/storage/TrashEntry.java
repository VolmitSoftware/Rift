package com.volmit.rift.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private String difficulty = "INHERIT";
    private String pvp = "INHERIT";
    private Map<String, String> gameRules = new LinkedHashMap<>();
    private boolean customSpawn = false;
    private double spawnX = 0.5D;
    private double spawnY = 64.0D;
    private double spawnZ = 0.5D;
    private float spawnYaw = 0.0F;
    private boolean managedBorder = false;
    private double borderSize = 59_999_968.0D;
    private double borderCenterX = 0.0D;
    private double borderCenterZ = 0.0D;
    private int borderWarningDistance = 5;
    private int borderWarningTime = 15;
    private double borderDamageAmount = 0.2D;
    private double borderDamageBuffer = 5.0D;
    private String accessPermission = "";
    private String accessDeniedMessage = "";
    private String respawnWorld = "";
    private List<String> tags = new ArrayList<>();

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
        entry.difficulty = profile.getDifficulty();
        entry.pvp = profile.getPvp();
        entry.gameRules = profile.getGameRules();
        entry.customSpawn = profile.isCustomSpawn();
        entry.spawnX = profile.getSpawnX();
        entry.spawnY = profile.getSpawnY();
        entry.spawnZ = profile.getSpawnZ();
        entry.spawnYaw = profile.getSpawnYaw();
        entry.managedBorder = profile.isManagedBorder();
        entry.borderSize = profile.getBorderSize();
        entry.borderCenterX = profile.getBorderCenterX();
        entry.borderCenterZ = profile.getBorderCenterZ();
        entry.borderWarningDistance = profile.getBorderWarningDistance();
        entry.borderWarningTime = profile.getBorderWarningTime();
        entry.borderDamageAmount = profile.getBorderDamageAmount();
        entry.borderDamageBuffer = profile.getBorderDamageBuffer();
        entry.accessPermission = profile.getAccessPermission();
        entry.accessDeniedMessage = profile.getAccessDeniedMessage();
        entry.respawnWorld = profile.getRespawnWorld();
        entry.tags = new ArrayList<>(profile.getTags());
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
        profile.setDifficulty(difficulty);
        profile.setPvp(pvp);
        profile.setGameRules(gameRules);
        profile.setCustomSpawn(customSpawn);
        profile.setSpawnX(spawnX);
        profile.setSpawnY(spawnY);
        profile.setSpawnZ(spawnZ);
        profile.setSpawnYaw(spawnYaw);
        profile.setManagedBorder(managedBorder);
        profile.setBorderSize(borderSize);
        profile.setBorderCenterX(borderCenterX);
        profile.setBorderCenterZ(borderCenterZ);
        profile.setBorderWarningDistance(borderWarningDistance);
        profile.setBorderWarningTime(borderWarningTime);
        profile.setBorderDamageAmount(borderDamageAmount);
        profile.setBorderDamageBuffer(borderDamageBuffer);
        profile.setAccessPermission(accessPermission);
        profile.setAccessDeniedMessage(accessDeniedMessage);
        profile.setRespawnWorld(respawnWorld);
        profile.setTags(tags);
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
