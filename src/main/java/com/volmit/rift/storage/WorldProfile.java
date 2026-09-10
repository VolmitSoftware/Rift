package com.volmit.rift.storage;

import art.arcane.volmlib.util.config.ConfigDescription;
import art.arcane.volmlib.util.config.ConfigDoc;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.nio.file.Path;
import java.util.Locale;

@ConfigDescription("A world managed by Rift")
public final class WorldProfile {
    @ConfigDoc("Rift command name for this world.")
    private String name = "world";

    @ConfigDoc("Environment used when the world was created.")
    private String environment = World.Environment.NORMAL.name();

    @ConfigDoc("Bukkit generator identifier. Empty selects the server's vanilla generator.")
    private String generator = "";

    @ConfigDoc("World type used when the world was created.")
    private String worldType = WorldType.NORMAL.name();

    @ConfigDoc("Seed recorded when the profile was created or imported.")
    private long seed = 0L;

    @ConfigDoc("World storage directory relative to the Bukkit world container.")
    private String directory = "";

    @ConfigDoc("Load this world during Rift startup.")
    private boolean autoLoad = true;

    @ConfigDoc("Prevent Rift from unloading or deleting this world.")
    private boolean protectedWorld = false;

    public static WorldProfile fromWorld(
            String logicalName,
            World world,
            String generator,
            WorldType worldType,
            boolean autoLoad
    ) {
        WorldProfile profile = new WorldProfile();
        profile.name = logicalName;
        profile.environment = world.getEnvironment().name();
        profile.generator = normalizeGenerator(generator);
        profile.worldType = worldType.name();
        profile.seed = world.getSeed();
        profile.autoLoad = autoLoad;
        return profile;
    }

    public WorldProfile normalize(WorldNamePolicy names) {
        name = names.requireValid(name);
        environment = enumName(World.Environment.class, environment, "environment");
        worldType = enumName(WorldType.class, worldType, "world type");
        generator = normalizeGenerator(generator);
        directory = normalizeDirectory(directory, name);
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public World.Environment environment() {
        return World.Environment.valueOf(environment);
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getGenerator() {
        return generator;
    }

    public void setGenerator(String generator) {
        this.generator = generator;
    }

    public WorldType type() {
        return WorldType.valueOf(worldType);
    }

    public String getWorldType() {
        return worldType;
    }

    public void setWorldType(String worldType) {
        this.worldType = worldType;
    }

    public long getSeed() {
        return seed;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public boolean isAutoLoad() {
        return autoLoad;
    }

    public void setAutoLoad(boolean autoLoad) {
        this.autoLoad = autoLoad;
    }

    public boolean isProtectedWorld() {
        return protectedWorld;
    }

    public void setProtectedWorld(boolean protectedWorld) {
        this.protectedWorld = protectedWorld;
    }

    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String normalizeGenerator(String generator) {
        String value = generator == null ? "" : generator.trim();
        return value.equalsIgnoreCase("vanilla") || value.equalsIgnoreCase("normal") ? "" : value;
    }

    private static String normalizeDirectory(String directory, String worldName) {
        String value = directory == null ? "" : directory.trim().replace('\\', '/');
        if (value.isBlank()) {
            return "";
        }
        Path path;
        try {
            path = Path.of(value).normalize();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid world storage directory: " + value, exception);
        }
        if (path.isAbsolute() || path.startsWith("..") || path.getFileName() == null) {
            throw new IllegalArgumentException("World storage directory must be relative and end with " + worldName);
        }
        String storageName = path.getFileName().toString();
        if (!storageName.equals(worldName) && !storageName.equals(worldName.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("World storage directory must be relative and end with " + worldName);
        }
        return path.toString().replace('\\', '/');
    }

    private static <E extends Enum<E>> String enumName(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("World " + label + " cannot be empty");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown world " + label + ": " + value, exception);
        }
    }
}
