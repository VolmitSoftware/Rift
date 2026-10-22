package com.volmit.rift.storage;

import art.arcane.volmlib.util.config.ConfigDescription;
import art.arcane.volmlib.util.config.ConfigDoc;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@ConfigDescription("A world managed by Rift")
public final class WorldProfile {
    private static final double MAX_BORDER_CENTER = 29_999_984.0D;
    private static final double MAX_BORDER_SIZE = 59_999_968.0D;
    private static final Pattern POLICY_TOKEN = Pattern.compile("[A-Za-z0-9_.:-]+");
    private static final Pattern PERMISSION_TOKEN = Pattern.compile("[A-Za-z0-9_.*:-]+");

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

    @ConfigDoc("Difficulty enforced while this world is loaded. Use INHERIT to leave the server value unchanged.")
    private String difficulty = "INHERIT";

    @ConfigDoc("PvP policy enforced while this world is loaded. Use INHERIT, ALLOW, or DENY.")
    private String pvp = "INHERIT";

    @ConfigDoc("Game mode applied on entry, respawn, or policy changes. INHERIT leaves the player's mode unchanged.")
    private String gameMode = "INHERIT";

    @ConfigDoc("Survival and adventure flight permission: INHERIT, ALLOW, or DENY. Creative and spectator keep native flight.")
    private String flight = "INHERIT";

    @ConfigDoc("Weather held while managed: INHERIT, CLEAR, RAIN, or THUNDER. INHERIT releases control without restoring old weather.")
    private String weather = "INHERIT";

    @ConfigDoc("Natural animal spawning: INHERIT, ALLOW, or DENY. Existing entities and other spawn reasons are unchanged.")
    private String naturalAnimals = "INHERIT";

    @ConfigDoc("Natural monster spawning: INHERIT, ALLOW, or DENY. Existing entities and other spawn reasons are unchanged.")
    private String naturalMonsters = "INHERIT";

    @ConfigDoc("Maximum admitted players. Zero is unlimited; lowering the limit does not remove current occupants.")
    private int playerLimit = 0;

    @ConfigDoc("Cross-world entry: INHERIT, ALLOW, or DENY. ALLOW does not override other restrictions.")
    private String travelEntry = "INHERIT";

    @ConfigDoc("Cross-world departures: INHERIT, ALLOW, or DENY. Does not block respawn or login recovery.")
    private String travelExit = "INHERIT";

    @ConfigDoc("Managed destination for first-ever joins assigned to this world. Empty uses the login destination or server placement.")
    private String firstJoinWorld = "";

    @ConfigDoc("Managed destination for logins assigned to this world. Empty keeps server placement. Only loaded destinations are used.")
    private String loginWorld = "";

    @ConfigDoc("Game-rule names and values enforced while this world is loaded.")
    private Map<String, String> gameRules = new LinkedHashMap<>();

    @ConfigDoc("Use the stored coordinates as this world's spawn point.")
    private boolean customSpawn = false;

    @ConfigDoc("Stored spawn X coordinate.")
    private double spawnX = 0.5D;

    @ConfigDoc("Stored spawn Y coordinate.")
    private double spawnY = 64.0D;

    @ConfigDoc("Stored spawn Z coordinate.")
    private double spawnZ = 0.5D;

    @ConfigDoc("Stored spawn yaw.")
    private float spawnYaw = 0.0F;

    @ConfigDoc("Enforce the stored world-border settings while this world is loaded.")
    private boolean managedBorder = false;

    @ConfigDoc("World-border diameter in blocks.")
    private double borderSize = MAX_BORDER_SIZE;

    @ConfigDoc("World-border center X coordinate.")
    private double borderCenterX = 0.0D;

    @ConfigDoc("World-border center Z coordinate.")
    private double borderCenterZ = 0.0D;

    @ConfigDoc("World-border warning distance in blocks.")
    private int borderWarningDistance = 5;

    @ConfigDoc("World-border warning time in seconds.")
    private int borderWarningTime = 15;

    @ConfigDoc("World-border damage per block outside its buffer.")
    private double borderDamageAmount = 0.2D;

    @ConfigDoc("World-border safe buffer in blocks.")
    private double borderDamageBuffer = 5.0D;

    @ConfigDoc("Permission required to enter this world. Empty allows everyone.")
    private String accessPermission = "";

    @ConfigDoc("Optional message shown when entry is denied. {world} and {permission} are available placeholders.")
    private String accessDeniedMessage = "";

    @ConfigDoc("Managed world used after deaths in this world. Empty keeps the server-selected respawn.")
    private String respawnWorld = "";

    @ConfigDoc("Operator labels used to group and identify this world.")
    private List<String> tags = new ArrayList<>();

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
        difficulty = normalizeEnumPolicy(difficulty, "difficulty", "PEACEFUL", "EASY", "NORMAL", "HARD");
        pvp = normalizeEnumPolicy(pvp, "PvP policy", "ALLOW", "DENY");
        gameMode = normalizeEnumPolicy(gameMode, "game mode", "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR");
        flight = normalizeEnumPolicy(flight, "flight", "ALLOW", "DENY");
        weather = normalizeEnumPolicy(weather, "weather", "CLEAR", "RAIN", "THUNDER");
        naturalAnimals = normalizeEnumPolicy(naturalAnimals, "natural animal spawning", "ALLOW", "DENY");
        naturalMonsters = normalizeEnumPolicy(naturalMonsters, "natural monster spawning", "ALLOW", "DENY");
        requireNonNegative(playerLimit, "player limit");
        travelEntry = normalizeEnumPolicy(travelEntry, "cross-world entry", "ALLOW", "DENY");
        travelExit = normalizeEnumPolicy(travelExit, "cross-world exit", "ALLOW", "DENY");
        firstJoinWorld = firstJoinWorld == null || firstJoinWorld.isBlank() ? "" : names.requireValid(firstJoinWorld);
        loginWorld = loginWorld == null || loginWorld.isBlank() ? "" : names.requireValid(loginWorld);
        gameRules = normalizeMap(gameRules, "game rule");
        requireFinite(spawnX, "spawn X");
        requireFinite(spawnY, "spawn Y");
        requireFinite(spawnZ, "spawn Z");
        requireFinite(spawnYaw, "spawn yaw");
        requireRange(borderSize, 1.0D, MAX_BORDER_SIZE, "border size");
        requireRange(borderCenterX, -MAX_BORDER_CENTER, MAX_BORDER_CENTER, "border center X");
        requireRange(borderCenterZ, -MAX_BORDER_CENTER, MAX_BORDER_CENTER, "border center Z");
        requireNonNegative(borderWarningDistance, "border warning distance");
        requireRange(borderWarningTime, 0.0D, Integer.MAX_VALUE / 20.0D, "border warning time");
        requireNonNegative(borderDamageAmount, "border damage amount");
        requireNonNegative(borderDamageBuffer, "border damage buffer");
        accessPermission = normalizePermission(accessPermission);
        accessDeniedMessage = accessDeniedMessage == null ? "" : accessDeniedMessage.trim();
        respawnWorld = respawnWorld == null || respawnWorld.isBlank() ? "" : names.requireValid(respawnWorld);
        tags = normalizeTags(tags);
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

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getPvp() {
        return pvp;
    }

    public void setPvp(String pvp) {
        this.pvp = pvp;
    }

    public Map<String, String> getGameRules() {
        return new LinkedHashMap<>(gameRules);
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public String getFlight() {
        return flight;
    }

    public void setFlight(String flight) {
        this.flight = flight;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public String getNaturalAnimals() {
        return naturalAnimals;
    }

    public void setNaturalAnimals(String naturalAnimals) {
        this.naturalAnimals = naturalAnimals;
    }

    public String getNaturalMonsters() {
        return naturalMonsters;
    }

    public void setNaturalMonsters(String naturalMonsters) {
        this.naturalMonsters = naturalMonsters;
    }

    public void setGameRules(Map<String, String> gameRules) {
        this.gameRules = new LinkedHashMap<>(gameRules);
    }

    public int getPlayerLimit() {
        return playerLimit;
    }

    public void setPlayerLimit(int playerLimit) {
        this.playerLimit = playerLimit;
    }

    public String getTravelEntry() {
        return travelEntry;
    }

    public void setTravelEntry(String travelEntry) {
        this.travelEntry = travelEntry;
    }

    public String getTravelExit() {
        return travelExit;
    }

    public void setTravelExit(String travelExit) {
        this.travelExit = travelExit;
    }

    public String getFirstJoinWorld() {
        return firstJoinWorld;
    }

    public void setFirstJoinWorld(String firstJoinWorld) {
        this.firstJoinWorld = firstJoinWorld;
    }

    public String getLoginWorld() {
        return loginWorld;
    }

    public void setLoginWorld(String loginWorld) {
        this.loginWorld = loginWorld;
    }

    public boolean isCustomSpawn() {
        return customSpawn;
    }

    public void setCustomSpawn(boolean customSpawn) {
        this.customSpawn = customSpawn;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(double spawnX) {
        this.spawnX = spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public void setSpawnY(double spawnY) {
        this.spawnY = spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public void setSpawnZ(double spawnZ) {
        this.spawnZ = spawnZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public void setSpawnYaw(float spawnYaw) {
        this.spawnYaw = spawnYaw;
    }

    public boolean isManagedBorder() {
        return managedBorder;
    }

    public void setManagedBorder(boolean managedBorder) {
        this.managedBorder = managedBorder;
    }

    public double getBorderSize() {
        return borderSize;
    }

    public void setBorderSize(double borderSize) {
        this.borderSize = borderSize;
    }

    public double getBorderCenterX() {
        return borderCenterX;
    }

    public void setBorderCenterX(double borderCenterX) {
        this.borderCenterX = borderCenterX;
    }

    public double getBorderCenterZ() {
        return borderCenterZ;
    }

    public void setBorderCenterZ(double borderCenterZ) {
        this.borderCenterZ = borderCenterZ;
    }

    public int getBorderWarningDistance() {
        return borderWarningDistance;
    }

    public void setBorderWarningDistance(int borderWarningDistance) {
        this.borderWarningDistance = borderWarningDistance;
    }

    public int getBorderWarningTime() {
        return borderWarningTime;
    }

    public void setBorderWarningTime(int borderWarningTime) {
        this.borderWarningTime = borderWarningTime;
    }

    public double getBorderDamageAmount() {
        return borderDamageAmount;
    }

    public void setBorderDamageAmount(double borderDamageAmount) {
        this.borderDamageAmount = borderDamageAmount;
    }

    public double getBorderDamageBuffer() {
        return borderDamageBuffer;
    }

    public void setBorderDamageBuffer(double borderDamageBuffer) {
        this.borderDamageBuffer = borderDamageBuffer;
    }

    public String getAccessPermission() {
        return accessPermission;
    }

    public void setAccessPermission(String accessPermission) {
        this.accessPermission = accessPermission;
    }

    public String getAccessDeniedMessage() {
        return accessDeniedMessage;
    }

    public void setAccessDeniedMessage(String accessDeniedMessage) {
        this.accessDeniedMessage = accessDeniedMessage;
    }

    public String getRespawnWorld() {
        return respawnWorld;
    }

    public void setRespawnWorld(String respawnWorld) {
        this.respawnWorld = respawnWorld;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = new ArrayList<>(tags);
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

    private static String normalizeEnumPolicy(String value, String label, String... allowed) {
        String normalized = value == null || value.isBlank() ? "INHERIT" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("INHERIT")) {
            return normalized;
        }
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("Unknown world " + label + ": " + value);
    }

    private static Map<String, String> normalizeMap(Map<String, String> values, String label) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (values == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isBlank() || !POLICY_TOKEN.matcher(key).matches()) {
                throw new IllegalArgumentException("Invalid world " + label + " name: " + entry.getKey());
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.isBlank()) {
                throw new IllegalArgumentException("World " + label + " value cannot be empty: " + key);
            }
            normalized.put(key, value);
        }
        return normalized;
    }

    private static List<String> normalizeTags(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            String tag = normalizeToken(value, "tag", false);
            if (!normalized.contains(tag)) {
                normalized.add(tag);
            }
        }
        return normalized;
    }

    private static String normalizeToken(String value, String label, boolean allowEmpty) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() && allowEmpty) {
            return "";
        }
        if (normalized.isBlank() || !POLICY_TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid world " + label + ": " + value);
        }
        return normalized;
    }

    private static String normalizePermission(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        if (!PERMISSION_TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid world access permission: " + value);
        }
        return normalized;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("World " + label + " must be finite");
        }
    }

    private static void requireRange(double value, double minimum, double maximum, String label) {
        requireFinite(value, label);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("World " + label + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void requireNonNegative(double value, String label) {
        requireFinite(value, label);
        if (value < 0.0D) {
            throw new IllegalArgumentException("World " + label + " cannot be negative");
        }
    }

}
