package com.volmit.rift.config;

import art.arcane.volmlib.util.config.ConfigDescription;
import art.arcane.volmlib.util.config.ConfigDoc;
import java.util.Locale;
import java.util.regex.Pattern;

@ConfigDescription("Rift runtime configuration")
public final class RiftConfig {
    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{1,31}");
    private static final Pattern SOUND_KEY = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    @ConfigDoc("Locale identifier selecting languages/<locale>.toml; missing repository translations download when selected.")
    private String language = "en_US";
    @ConfigDoc("Milliseconds between filesystem hot-reload polls.")
    private long hotReloadPollMillis = 1_000L;
    @ConfigDoc("Milliseconds used to combine rapid file changes into one reload.")
    private long hotReloadCooldownMillis = 1_500L;
    @ConfigDoc("Load managed profiles marked auto-load during plugin startup.")
    private boolean autoLoadManagedWorlds = true;
    @ConfigDoc("Loaded world used when evacuating players. Empty selects the primary world.")
    private String evacuationWorld = "";
    @ConfigDoc("Allow confirmed moves of managed worlds into Rift quarantine.")
    private boolean allowWorldDeletion = true;
    @ConfigDoc("Seconds allowed between the first and second delete command.")
    private int deleteConfirmationSeconds = 30;
    @ConfigDoc("Print the Rift startup splash to the console.")
    private boolean splashScreen = true;
    @ConfigDoc("Show title and subtitle feedback when an enabled event completes.")
    private boolean titlePopups = true;
    @ConfigDoc("Publish action-bar feedback through VolmLib HUD arbitration.")
    private boolean actionBarPopups = true;
    @ConfigDoc("Play configured feedback sounds.")
    private boolean sounds = true;
    @ConfigDoc("Show feedback for create, import, load, unload, quarantine, and restore operations.")
    private boolean worldLifecycleFeedback = true;
    @ConfigDoc("Show feedback after a successful teleport.")
    private boolean teleportFeedback = true;
    @ConfigDoc("Show feedback when an operation fails.")
    private boolean failureFeedback = true;
    @ConfigDoc("Bukkit sound used for successful world lifecycle operations.")
    private String worldLifecycleSound = "block.beacon.activate";
    @ConfigDoc("Bukkit sound used for successful teleports.")
    private String teleportSound = "entity.enderman.teleport";
    @ConfigDoc("Bukkit sound used for failed operations.")
    private String failureSound = "block.note_block.bass";
    @ConfigDoc("Volume used for Rift feedback sounds.")
    private float soundVolume = 0.8F;
    @ConfigDoc("Pitch used for Rift feedback sounds.")
    private float soundPitch = 1.0F;
    @ConfigDoc("Title fade-in duration in ticks.")
    private int titleFadeInTicks = 10;
    @ConfigDoc("Title stay duration in ticks.")
    private int titleStayTicks = 40;
    @ConfigDoc("Title fade-out duration in ticks.")
    private int titleFadeOutTicks = 10;
    @ConfigDoc("Emit additional lifecycle and hot-reload details.")
    private boolean verbose = false;
    @ConfigDoc("Upload /rift debug reports to the public mclo.gs service after saving them locally.")
    private boolean debugUploadEnabled = true;
    @ConfigDoc("Submit anonymous Rift usage metrics to bStats. The global plugins/bStats/config.yml opt-out also applies.")
    private boolean bstatsEnabled = true;

    public RiftConfig normalize() {
        language = normalizeLanguage(language);
        hotReloadPollMillis = clamp(hotReloadPollMillis, 250L, 10_000L);
        hotReloadCooldownMillis = clamp(hotReloadCooldownMillis, 250L, 30_000L);
        evacuationWorld = evacuationWorld == null ? "" : evacuationWorld.trim();
        deleteConfirmationSeconds = (int) clamp(deleteConfirmationSeconds, 10L, 300L);
        worldLifecycleSound = normalizeSound(worldLifecycleSound, "worldLifecycleSound");
        teleportSound = normalizeSound(teleportSound, "teleportSound");
        failureSound = normalizeSound(failureSound, "failureSound");
        soundVolume = clampFinite(soundVolume, 0.0F, 4.0F, "soundVolume");
        soundPitch = clampFinite(soundPitch, 0.5F, 2.0F, "soundPitch");
        titleFadeInTicks = (int) clamp(titleFadeInTicks, 0L, 200L);
        titleStayTicks = (int) clamp(titleStayTicks, 1L, 1_200L);
        titleFadeOutTicks = (int) clamp(titleFadeOutTicks, 0L, 200L);
        return this;
    }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public long getHotReloadPollMillis() { return hotReloadPollMillis; }
    public void setHotReloadPollMillis(long value) { hotReloadPollMillis = value; }
    public long getHotReloadCooldownMillis() { return hotReloadCooldownMillis; }
    public void setHotReloadCooldownMillis(long value) { hotReloadCooldownMillis = value; }
    public boolean isAutoLoadManagedWorlds() { return autoLoadManagedWorlds; }
    public void setAutoLoadManagedWorlds(boolean value) { autoLoadManagedWorlds = value; }
    public String getEvacuationWorld() { return evacuationWorld; }
    public void setEvacuationWorld(String value) { evacuationWorld = value; }
    public boolean isAllowWorldDeletion() { return allowWorldDeletion; }
    public void setAllowWorldDeletion(boolean value) { allowWorldDeletion = value; }
    public int getDeleteConfirmationSeconds() { return deleteConfirmationSeconds; }
    public void setDeleteConfirmationSeconds(int value) { deleteConfirmationSeconds = value; }
    public boolean isSplashScreen() { return splashScreen; }
    public void setSplashScreen(boolean value) { splashScreen = value; }
    public boolean isTitlePopups() { return titlePopups; }
    public void setTitlePopups(boolean value) { titlePopups = value; }
    public boolean isActionBarPopups() { return actionBarPopups; }
    public void setActionBarPopups(boolean value) { actionBarPopups = value; }
    public boolean isSounds() { return sounds; }
    public void setSounds(boolean value) { sounds = value; }
    public boolean isWorldLifecycleFeedback() { return worldLifecycleFeedback; }
    public void setWorldLifecycleFeedback(boolean value) { worldLifecycleFeedback = value; }
    public boolean isTeleportFeedback() { return teleportFeedback; }
    public void setTeleportFeedback(boolean value) { teleportFeedback = value; }
    public boolean isFailureFeedback() { return failureFeedback; }
    public void setFailureFeedback(boolean value) { failureFeedback = value; }
    public String worldLifecycleSound() { return worldLifecycleSound; }
    public void setWorldLifecycleSound(String value) { worldLifecycleSound = value; }
    public String teleportSound() { return teleportSound; }
    public void setTeleportSound(String value) { teleportSound = value; }
    public String failureSound() { return failureSound; }
    public void setFailureSound(String value) { failureSound = value; }
    public float getSoundVolume() { return soundVolume; }
    public void setSoundVolume(float value) { soundVolume = value; }
    public float getSoundPitch() { return soundPitch; }
    public void setSoundPitch(float value) { soundPitch = value; }
    public int getTitleFadeInTicks() { return titleFadeInTicks; }
    public void setTitleFadeInTicks(int value) { titleFadeInTicks = value; }
    public int getTitleStayTicks() { return titleStayTicks; }
    public void setTitleStayTicks(int value) { titleStayTicks = value; }
    public int getTitleFadeOutTicks() { return titleFadeOutTicks; }
    public void setTitleFadeOutTicks(int value) { titleFadeOutTicks = value; }
    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean value) { verbose = value; }
    public boolean isDebugUploadEnabled() { return debugUploadEnabled; }
    public void setDebugUploadEnabled(boolean value) { debugUploadEnabled = value; }
    public boolean isBstatsEnabled() { return bstatsEnabled; }
    public void setBstatsEnabled(boolean value) { bstatsEnabled = value; }
    public static String normalizeLanguage(String language) {
        String normalized = language == null || language.isBlank() ? "en_US" : language.trim();
        if (!LANGUAGE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("language must be a safe locale identifier such as en_US");
        }
        return normalized;
    }

    private static String normalizeSound(String value, String field) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SOUND_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a valid namespaced sound key");
        }
        return normalized;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clampFinite(float value, float minimum, float maximum, String field) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return clamp(value, minimum, maximum);
    }
}
