package com.volmit.rift.world;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.FeatureFlag;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public final class WorldPolicyService implements Listener {
    private final Rift plugin;
    private final RiftLocalization language;
    private final WorldNamePolicy names;
    private final WorldProfileStore profiles;
    private final TeleportService teleports;
    private final Map<String, Boolean> appliedBorders = new ConcurrentHashMap<>();

    public WorldPolicyService(
            Rift plugin,
            RiftLocalization language,
            WorldNamePolicy names,
            WorldProfileStore profiles
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.language = Objects.requireNonNull(language, "language");
        this.names = Objects.requireNonNull(names, "names");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.teleports = new TeleportService(plugin);
    }

    public void applyAll() {
        if (!FoliaScheduler.runGlobal(plugin, this::applyLoadedProfiles)) {
            plugin.getLogger().warning("Unable to schedule managed world policy application");
        }
    }

    public boolean validateProfile(WorldProfile profile) {
        Map<String, String> canonical = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : profile.getGameRules().entrySet()) {
            GameRule<?> rule = resolveGameRule(entry.getKey());
            if (rule == null) {
                throw new IllegalArgumentException("Unknown game rule: " + entry.getKey());
            }
            if (rule.equals(GameRules.PVP)) {
                throw new IllegalArgumentException("Use the dedicated world PvP policy instead of the PVP game rule");
            }
            canonical.put(Registry.GAME_RULE.getKeyOrThrow(rule).toString(), validateGameRuleValue(rule, entry.getValue()));
        }
        profile.setGameRules(canonical);
        return true;
    }

    public boolean apply(World world) {
        if (world == null) {
            return true;
        }
        String logicalName = RiftWorldIdentity.logicalName(world, profiles);
        Optional<WorldProfile> profile = profiles.find(logicalName);
        return profile.isEmpty() || apply(world, profile.get());
    }

    public void show(CommandSender sender, String name) {
        WorldProfile profile = profiles.find(name).orElse(null);
        if (profile == null) {
            language.send(sender, RiftMessages.UNKNOWN_WORLD, MessageArgs.builder().untrusted("world", name).build());
            return;
        }
        detail(sender, RiftMessages.LABEL_POLICY_DIFFICULTY, profile.getDifficulty());
        detail(sender, RiftMessages.LABEL_POLICY_PVP, profile.getPvp());
        detail(sender, RiftMessages.LABEL_POLICY_GAME_RULES,
                profile.getGameRules().isEmpty() ? label(sender, RiftMessages.LABEL_NONE) : profile.getGameRules());
        detail(sender, RiftMessages.LABEL_POLICY_CUSTOM_SPAWN, profile.isCustomSpawn());
        detail(sender, RiftMessages.LABEL_POLICY_MANAGED_BORDER, profile.isManagedBorder());
        detail(sender, RiftMessages.LABEL_POLICY_ACCESS_PERMISSION,
                profile.getAccessPermission().isBlank() ? label(sender, RiftMessages.LABEL_NONE) : profile.getAccessPermission());
        detail(sender, RiftMessages.LABEL_POLICY_RESPAWN_WORLD,
                profile.getRespawnWorld().isBlank() ? label(sender, RiftMessages.LABEL_SERVER_DEFAULT) : profile.getRespawnWorld());
        detail(sender, RiftMessages.LABEL_POLICY_TAGS,
                profile.getTags().isEmpty() ? label(sender, RiftMessages.LABEL_NONE) : String.join(", ", profile.getTags()));
    }

    public CompletableFuture<Boolean> setDifficulty(CommandSender sender, String name, String value) {
        String normalized = normalizeChoice(value, "difficulty", "INHERIT", "PEACEFUL", "EASY", "NORMAL", "HARD");
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_DIFFICULTY),
                profile -> profile.setDifficulty(normalized), WorldProfile::getDifficulty, false);
    }

    public CompletableFuture<Boolean> setPvp(CommandSender sender, String name, String value) {
        String normalized = normalizeChoice(value, "PvP policy", "INHERIT", "ALLOW", "DENY");
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_PVP),
                profile -> profile.setPvp(normalized), WorldProfile::getPvp, false);
    }

    public CompletableFuture<Boolean> setGameRule(CommandSender sender, String name, String ruleName, String value) {
        String normalizedRule = Objects.requireNonNullElse(ruleName, "").trim();
        GameRule<?> rule = resolveGameRule(normalizedRule);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown game rule: " + ruleName);
        }
        if (rule.equals(GameRules.PVP)) {
            throw new IllegalArgumentException("Use /rift policy pvp for the PvP game rule");
        }
        String key = Registry.GAME_RULE.getKeyOrThrow(rule).toString();
        if (value.equalsIgnoreCase("inherit")) {
            return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_GAME_RULES) + " " + key, profile -> {
                Map<String, String> rules = profile.getGameRules();
                rules.remove(key);
                profile.setGameRules(rules);
            }, saved -> "INHERIT", false);
        }
        String normalizedValue = validateGameRuleValue(rule, value);
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_GAME_RULES) + " " + key, profile -> {
            Map<String, String> rules = profile.getGameRules();
            rules.put(key, normalizedValue);
            profile.setGameRules(rules);
        }, saved -> saved.getGameRules().get(key), false);
    }

    public CompletableFuture<Boolean> adjustGameRule(CommandSender sender, String name, GameRule<?> rule, long amount) {
        if (rule.equals(GameRules.PVP)) {
            throw new IllegalArgumentException("Use the dedicated PvP policy");
        }
        String key = Registry.GAME_RULE.getKeyOrThrow(rule).toString();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!FoliaScheduler.runGlobal(plugin, () -> {
            try {
                World world = RiftWorldIdentity.findLoaded(name);
                if (!supportsGameRule(rule, world == null ? Set.of(FeatureFlag.VANILLA) : world.getFeatureFlags())) {
                    throw new IllegalArgumentException("Game rule is not enabled by this world's feature flags: " + key);
                }
                String baseline = String.valueOf(world == null ? rule.getDefaultValue() : world.getGameRuleValue(rule));
                update(sender, name, key, profile -> {
                    Map<String, String> rules = profile.getGameRules();
                    rules.put(key, adjustedRuleValue(rule, rules.getOrDefault(key, baseline), amount));
                    profile.setGameRules(rules);
                }, saved -> saved.getGameRules().get(key), false).thenAccept(result::complete);
            } catch (RuntimeException exception) {
                fail(sender, key, name, exception);
                result.complete(false);
            }
        })) {
            fail(sender, key, name, new IOException("scheduler rejected game rule adjustment"));
            result.complete(false);
        }
        return result;
    }

    public CompletableFuture<GameRuleSnapshot> gameRuleValues(String name) {
        CompletableFuture<GameRuleSnapshot> result = new CompletableFuture<>();
        if (!FoliaScheduler.runGlobal(plugin, () -> {
            try {
                World world = RiftWorldIdentity.findLoaded(name);
                Set<FeatureFlag> features = world == null ? Set.of(FeatureFlag.VANILLA) : world.getFeatureFlags();
                result.complete(new GameRuleSnapshot(world != null, collectGameRuleValues(Registry.GAME_RULE, features,
                        rule -> String.valueOf(world == null ? rule.getDefaultValue() : world.getGameRuleValue(rule)))));
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        })) {
            result.completeExceptionally(new IOException("scheduler rejected game rule inspection"));
        }
        return result;
    }

    static Map<String, String> collectGameRuleValues(Iterable<GameRule<?>> rules, Set<FeatureFlag> features,
                                                    Function<GameRule<?>, String> reader) {
        Map<String, String> values = new LinkedHashMap<>();
        for (GameRule<?> rule : rules) {
            if (supportsGameRule(rule, features)) {
                values.put(rule.getKey().toString(), reader.apply(rule));
            }
        }
        return Map.copyOf(values);
    }

    private static boolean supportsGameRule(GameRule<?> rule, Set<FeatureFlag> features) {
        return features.containsAll(rule.requiredFeatures());
    }

    public CompletableFuture<Location> spawnLocation(String name) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        if (!FoliaScheduler.runGlobal(plugin, () -> {
            try {
                World world = RiftWorldIdentity.findLoaded(name);
                result.complete(world == null ? null : world.getSpawnLocation());
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        })) {
            result.completeExceptionally(new IOException("scheduler rejected spawn inspection"));
        }
        return result;
    }

    public CompletableFuture<Boolean> adjustBorder(CommandSender sender, String name, WorldBorderSetting setting, double steps, boolean reset) {
        return update(sender, name, setting.name(), profile -> setting.adjust(profile, steps, reset),
                saved -> Double.toString(setting.read(saved)), false);
    }

    public CompletableFuture<Boolean> toggleBorder(CommandSender sender, String name) {
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_MANAGED_BORDER),
                profile -> profile.setManagedBorder(!profile.isManagedBorder()), saved -> Boolean.toString(saved.isManagedBorder()), true);
    }

    static String adjustedRuleValue(GameRule<?> rule, String current, long amount) {
        if (rule.getType().equals(Boolean.class)) {
            return Boolean.toString(!Boolean.parseBoolean(current));
        }
        long base = Integer.parseInt(current);
        long bounded = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, base + Math.max(-Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, amount))));
        return Long.toString(bounded);
    }

    public CompletableFuture<Boolean> setSpawn(CommandSender sender, String name, Player player) {
        String validName = names.requireValid(name);
        World world = RiftWorldIdentity.findLoaded(validName);
        if (world == null || !world.equals(player.getWorld())) {
            throw new IllegalArgumentException("Stand in the loaded target world to set its spawn");
        }
        Location location = player.getLocation();
        requireSafeSpawn(location);
        return saveSpawn(sender, validName, location);
    }

    public CompletableFuture<Boolean> setSpawnCoordinates(Player player, String name, double x, double y, double z, float yaw) {
        String validName = names.requireValid(name);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                || Math.abs(x) > 29_999_984 || Math.abs(z) > 29_999_984) {
            throw new IllegalArgumentException("Spawn coordinates must be finite and within world limits");
        }
        World world = RiftWorldIdentity.findLoaded(validName);
        if (world == null) {
            throw new IllegalArgumentException("Load the target world before setting its spawn");
        }
        if (y < world.getMinHeight() + 1.0D || y >= world.getMaxHeight() - 1.0D) {
            throw new IllegalArgumentException("Spawn must be inside the world's build height");
        }
        Location location = new Location(world, x, y, z, yaw, 0);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!FoliaScheduler.runRegion(plugin, location, () -> {
            try {
                if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    throw new IllegalArgumentException("Visit the spawn area before entering its coordinates");
                }
                requireSafeSpawn(location);
                saveSpawn(player, validName, location).thenAccept(result::complete);
            } catch (RuntimeException exception) {
                fail(player, "spawn", validName, exception);
                result.complete(false);
            }
        })) {
            fail(player, "spawn", validName, new IOException("scheduler rejected spawn validation"));
            result.complete(false);
        }
        return result;
    }

    private CompletableFuture<Boolean> saveSpawn(CommandSender sender, String validName, Location location) {
        return update(sender, validName, label(sender, RiftMessages.LABEL_POLICY_CUSTOM_SPAWN), profile -> {
            profile.setCustomSpawn(true);
            profile.setSpawnX(location.getX());
            profile.setSpawnY(location.getY());
            profile.setSpawnZ(location.getZ());
            profile.setSpawnYaw(location.getYaw());
        }, saved -> formatLocation(location), false);
    }

    public CompletableFuture<Boolean> clearSpawn(CommandSender sender, String name) {
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_CUSTOM_SPAWN),
                profile -> profile.setCustomSpawn(false), saved -> "INHERIT", false);
    }

    public CompletableFuture<Boolean> setBorder(CommandSender sender, String name, double size) {
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_MANAGED_BORDER), profile -> {
            profile.setManagedBorder(true);
            profile.setBorderSize(size);
        }, saved -> Double.toString(saved.getBorderSize()), false);
    }

    public CompletableFuture<Boolean> setBorderCenter(CommandSender sender, String name, double centerX, double centerZ) {
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_MANAGED_BORDER), profile -> {
            profile.setManagedBorder(true);
            profile.setBorderCenterX(centerX);
            profile.setBorderCenterZ(centerZ);
        }, saved -> saved.getBorderCenterX() + ", " + saved.getBorderCenterZ(), false);
    }

    public CompletableFuture<Boolean> setBorderWarning(CommandSender sender, String name, int warningDistance, int warningTime) {
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_MANAGED_BORDER), profile -> {
            profile.setManagedBorder(true);
            profile.setBorderWarningDistance(warningDistance);
            profile.setBorderWarningTime(warningTime);
        }, saved -> saved.getBorderWarningDistance() + " blocks / " + saved.getBorderWarningTime() + " seconds", false);
    }

    public CompletableFuture<Boolean> setBorderDamage(CommandSender sender, String name, double damageAmount, double damageBuffer) {
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_MANAGED_BORDER), profile -> {
            profile.setManagedBorder(true);
            profile.setBorderDamageAmount(damageAmount);
            profile.setBorderDamageBuffer(damageBuffer);
        }, saved -> saved.getBorderDamageAmount() + " damage / " + saved.getBorderDamageBuffer() + " buffer", false);
    }

    public CompletableFuture<Boolean> clearBorder(CommandSender sender, String name) {
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_MANAGED_BORDER),
                profile -> profile.setManagedBorder(false), saved -> "INHERIT", true);
    }

    public CompletableFuture<Boolean> setAccess(CommandSender sender, String name, String permission, String deniedMessage) {
        String value = permission.equalsIgnoreCase("clear") || permission.equalsIgnoreCase("none")
                ? ""
                : permission;
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_ACCESS_PERMISSION), profile -> {
            profile.setAccessPermission(value);
            profile.setAccessDeniedMessage(value.isBlank() || "default".equalsIgnoreCase(deniedMessage)
                    ? "" : Objects.requireNonNullElse(deniedMessage, ""));
        },
                saved -> saved.getAccessPermission().isBlank() ? label(sender, RiftMessages.LABEL_NONE) : saved.getAccessPermission(), false);
    }

    public CompletableFuture<Boolean> setRespawn(CommandSender sender, String name, String destination) {
        String value = destination.equalsIgnoreCase("clear") || destination.equalsIgnoreCase("default")
                ? ""
                : names.requireValid(destination);
        if (!value.isBlank() && profiles.find(value).isEmpty()) {
            throw new IllegalArgumentException("Respawn destination is not managed: " + value);
        }
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_RESPAWN_WORLD),
                profile -> profile.setRespawnWorld(value),
                saved -> saved.getRespawnWorld().isBlank() ? label(sender, RiftMessages.LABEL_SERVER_DEFAULT) : saved.getRespawnWorld(), false);
    }

    public CompletableFuture<Boolean> setTag(CommandSender sender, String name, String tag, boolean enabled) {
        String normalized = Objects.requireNonNullElse(tag, "").trim().toLowerCase(Locale.ROOT);
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_TAGS) + " " + normalized, profile -> {
            List<String> tags = new ArrayList<>(profile.getTags());
            tags.remove(normalized);
            if (enabled) {
                tags.add(normalized);
            }
            profile.setTags(tags);
        }, saved -> String.valueOf(enabled), false);
    }

    public void forget(String worldName) {
        if (worldName == null) {
            return;
        }
        String key = worldName.toLowerCase(Locale.ROOT);
        appliedBorders.remove(key);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location destination = event.getTo();
        if (destination == null || destination.getWorld() == null || hasAccess(event.getPlayer(), destination.getWorld())) {
            return;
        }
        event.setCancelled(true);
        deny(event.getPlayer(), destination.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<WorldProfile> source = profiles.find(RiftWorldIdentity.logicalName(player.getWorld(), profiles));
        Location selected = event.getRespawnLocation();
        if (source.isPresent() && !source.get().getRespawnWorld().isBlank()) {
            World configured = RiftWorldIdentity.findLoaded(source.get().getRespawnWorld());
            if (configured != null && hasAccess(player, configured)) {
                selected = configured.getSpawnLocation();
            }
        }
        World selectedWorld = selected.getWorld();
        if (selectedWorld != null && hasAccess(player, selectedWorld)) {
            event.setRespawnLocation(selected);
            return;
        }
        World fallback = accessibleFallback(player, selectedWorld);
        if (fallback != null) {
            event.setRespawnLocation(fallback.getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        FoliaScheduler.runEntity(plugin, player, () -> evacuateRestrictedJoin(player), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hasAccess(player, player.getWorld())) {
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> evacuateRestrictedJoin(player), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!hasAccess(player, player.getWorld())) {
            FoliaScheduler.runEntity(plugin, player, () -> evacuateRestrictedJoin(player));
        }
    }

    private void applyLoadedProfiles() {
        appliedBorders.keySet().removeIf(key -> profiles.find(key).isEmpty());
        for (WorldProfile profile : profiles.all()) {
            World world = RiftWorldIdentity.findLoaded(profile.getName());
            if (world != null) {
                apply(world, profile);
            }
        }
    }

    private boolean apply(World world, WorldProfile profile) {
        try {
            if (!profile.getDifficulty().equals("INHERIT")) {
                world.setDifficulty(Difficulty.valueOf(profile.getDifficulty()));
            }
            if (!profile.getPvp().equals("INHERIT")) {
                world.setGameRule(GameRules.PVP, profile.getPvp().equals("ALLOW"));
            }
            for (Map.Entry<String, String> entry : profile.getGameRules().entrySet()) {
                GameRule<?> rule = resolveGameRule(entry.getKey());
                if (rule == null || !applyGameRule(world, rule, entry.getValue())) {
                    throw new IllegalArgumentException("World rejected game rule " + entry.getKey() + '=' + entry.getValue());
                }
            }
            if (profile.isCustomSpawn()) {
                Location spawn = new Location(world, profile.getSpawnX(), profile.getSpawnY(), profile.getSpawnZ(),
                        profile.getSpawnYaw(), 0.0F);
                if (!world.setSpawnLocation(spawn)) {
                    throw new IllegalArgumentException("World rejected the configured spawn location");
                }
            }
            applyBorder(world, profile);
            evacuateDeniedPlayers(world);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to apply managed policies for world " + profile.getName(), exception);
            return false;
        }
    }

    private void evacuateDeniedPlayers(World world) {
        for (Player player : List.copyOf(world.getPlayers())) {
            FoliaScheduler.runEntity(plugin, player, () -> evacuateRestrictedJoin(player));
        }
    }

    private void applyBorder(World world, WorldProfile profile) {
        String key = profile.key();
        WorldBorder border = world.getWorldBorder();
        if (!profile.isManagedBorder()) {
            if (appliedBorders.remove(key) != null) {
                border.reset();
            }
            return;
        }
        border.setCenter(profile.getBorderCenterX(), profile.getBorderCenterZ());
        border.setSize(profile.getBorderSize());
        border.setWarningDistance(profile.getBorderWarningDistance());
        border.setWarningTimeTicks(Math.multiplyExact(profile.getBorderWarningTime(), 20));
        border.setDamageAmount(profile.getBorderDamageAmount());
        border.setDamageBuffer(profile.getBorderDamageBuffer());
        appliedBorders.put(key, Boolean.TRUE);
    }

    public CompletableFuture<Boolean> setAccessPermission(CommandSender sender, String name, String permission) {
        String value = permission.equalsIgnoreCase("clear") ? "" : permission;
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_ACCESS_PERMISSION),
                profile -> profile.setAccessPermission(value), WorldProfile::getAccessPermission, false);
    }

    public CompletableFuture<Boolean> setAccessDenial(CommandSender sender, String name, String message) {
        String value = message.equalsIgnoreCase("clear") ? "" : message;
        return update(sender, name, label(sender, RiftMessages.GUI_POLICY_DENIAL),
                profile -> profile.setAccessDeniedMessage(value), WorldProfile::getAccessDeniedMessage, false);
    }

    public CompletableFuture<Boolean> setTags(CommandSender sender, String name, List<String> tags) {
        List<String> values = List.copyOf(tags);
        return update(sender, name, label(sender, RiftMessages.LABEL_POLICY_TAGS),
                profile -> profile.setTags(values), saved -> String.join(", ", saved.getTags()), false);
    }

    private CompletableFuture<Boolean> update(
            CommandSender sender,
            String name,
            String setting,
            Consumer<WorldProfile> mutation,
            Function<WorldProfile, String> value,
            boolean resetBorder
    ) {
        String validName = names.requireValid(name);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!FoliaScheduler.runAsync(plugin, () -> {
            try {
                WorldProfile candidate = profiles.update(validName, mutation);
                if (!FoliaScheduler.runGlobal(plugin, () -> {
                    WorldProfile current = profiles.find(candidate.getName()).orElse(null);
                    if (current == null) {
                        result.complete(false);
                        return;
                    }
                    World world = RiftWorldIdentity.findLoaded(candidate.getName());
                    if (world != null) {
                        if (resetBorder) {
                            appliedBorders.put(candidate.key(), Boolean.TRUE);
                        }
                        if (!apply(world, current)) {
                            fail(sender, setting, candidate.getName(), new IOException("policy was saved but Paper rejected runtime activation"));
                            result.complete(false);
                            return;
                        }
                    }
                    deliverUpdated(sender, setting, candidate.getName(), value.apply(candidate));
                    result.complete(true);
                })) {
                    throw new IOException("scheduler rejected policy activation");
                }
            } catch (Throwable exception) {
                fail(sender, setting, validName, exception);
                result.complete(false);
            }
        })) {
            fail(sender, setting, validName, new IOException("scheduler rejected policy persistence"));
            result.complete(false);
        }
        return result;
    }

    private void evacuateRestrictedJoin(Player player) {
        World restricted = player.getWorld();
        if (hasAccess(player, restricted)) {
            return;
        }
        World fallback = accessibleFallback(player, restricted);
        deny(player, restricted);
        if (fallback != null) {
            teleports.teleport(player, fallback.getSpawnLocation(), success -> {
                if (!success) {
                    FoliaScheduler.runEntity(plugin, player, () -> disconnectIfRestricted(player));
                }
            });
        } else {
            disconnectIfRestricted(player);
        }
    }

    private void disconnectIfRestricted(Player player) {
        World world = player.getWorld();
        if (hasAccess(player, world)) {
            return;
        }
        profiles.find(RiftWorldIdentity.logicalName(world, profiles)).ifPresent(profile -> {
            String message = profile.getAccessDeniedMessage().isBlank()
                    ? language.text(player, RiftMessages.POLICY_ACCESS_DENIED, MessageArgs.builder()
                            .untrusted("world", profile.getName())
                            .untrusted("permission", profile.getAccessPermission()).build()).plain()
                    : profile.getAccessDeniedMessage().replace("{world}", profile.getName())
                            .replace("{permission}", profile.getAccessPermission());
            player.kick(Component.text(message));
        });
    }

    private World accessibleFallback(Player player, World excluded) {
        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(excluded) && hasAccess(player, world)) {
                return world;
            }
        }
        return null;
    }

    private boolean hasAccess(Player player, World world) {
        Optional<WorldProfile> profile = profiles.find(RiftWorldIdentity.logicalName(world, profiles));
        if (profile.isEmpty() || profile.get().getAccessPermission().isBlank()) {
            return true;
        }
        return player.hasPermission(profile.get().getAccessPermission())
                || player.hasPermission("rift.access.bypass")
                || player.hasPermission("rift.admin");
    }

    private void deny(Player player, World world) {
        profiles.find(RiftWorldIdentity.logicalName(world, profiles)).ifPresent(profile -> {
            if (profile.getAccessDeniedMessage().isBlank()) {
                language.send(player, RiftMessages.POLICY_ACCESS_DENIED, MessageArgs.builder()
                        .untrusted("world", profile.getName())
                        .untrusted("permission", profile.getAccessPermission())
                        .build());
                return;
            }
            String message = profile.getAccessDeniedMessage()
                    .replace("{world}", profile.getName())
                    .replace("{permission}", profile.getAccessPermission());
            player.sendMessage(Component.text(message));
        });
    }

    private void deliverUpdated(CommandSender sender, String setting, String world, String value) {
        Runnable delivery = () -> language.send(sender, RiftMessages.PROFILE_UPDATED, MessageArgs.builder()
                .untrusted("setting", setting)
                .untrusted("world", world)
                .untrusted("value", value)
                .build());
        if (sender instanceof Player player) {
            FoliaScheduler.runEntity(plugin, player, delivery);
        } else {
            delivery.run();
        }
    }

    private void fail(CommandSender sender, String operation, String world, Throwable exception) {
        plugin.getLogger().log(Level.SEVERE, "Rift " + operation + " policy update failed for " + world, exception);
        Runnable delivery = () -> language.send(sender, RiftMessages.OPERATION_FAILED, MessageArgs.builder()
                .untrusted("operation", operation)
                .untrusted("world", world)
                .untrusted("reason", Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()))
                .build());
        if (sender instanceof Player player) {
            FoliaScheduler.runEntity(plugin, player, delivery);
        } else if (!FoliaScheduler.runGlobal(plugin, delivery)) {
            plugin.getLogger().warning("Unable to deliver the failed policy update to " + sender.getName());
        }
    }

    private void detail(CommandSender sender, TextKey label, Object value) {
        language.send(sender, RiftMessages.DETAIL, MessageArgs.builder()
                .untrusted("label", language.text(sender, label).plain())
                .untrusted("value", String.valueOf(value))
                .build());
    }

    private String label(CommandSender sender, TextKey key) {
        return language.text(sender, key).plain();
    }

    private static String normalizeChoice(String value, String label, String... allowed) {
        String normalized = Objects.requireNonNullElse(value, "").trim().toUpperCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("Unknown " + label + ": " + value);
    }

    private static String validateGameRuleValue(GameRule<?> rule, String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        if (rule.getType().equals(Boolean.class)) {
            if (!normalized.equals("true") && !normalized.equals("false")) {
                throw new IllegalArgumentException("Game rule " + Registry.GAME_RULE.getKeyOrThrow(rule) + " requires true or false");
            }
            return normalized;
        }
        try {
            return String.valueOf(Integer.parseInt(normalized));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Game rule " + Registry.GAME_RULE.getKeyOrThrow(rule) + " requires an integer", exception);
        }
    }

    private static <T> boolean applyGameRule(World world, GameRule<T> rule, String value) {
        Object parsed = rule.getType().equals(Boolean.class)
                ? Boolean.valueOf(value)
                : Integer.valueOf(value);
        return world.setGameRule(rule, rule.getType().cast(parsed));
    }

    private static GameRule<?> resolveGameRule(String value) {
        String requested = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        String compact = requested.replace("minecraft:", "").replace("_", "");
        for (GameRule<?> rule : Registry.GAME_RULE) {
            String key = Registry.GAME_RULE.getKeyOrThrow(rule).toString().toLowerCase(Locale.ROOT);
            if (key.equals(requested)
                    || key.replace("minecraft:", "").equals(requested)
                    || key.replace("minecraft:", "").replace("_", "").equals(compact)) {
                return rule;
            }
        }
        return null;
    }

    private static void requireSafeSpawn(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "spawn world");
        int y = location.getBlockY();
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            throw new IllegalArgumentException("Spawn must be inside the world's build height");
        }
        if (!location.getBlock().isPassable()
                || !location.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()
                || !location.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType().isSolid()) {
            throw new IllegalArgumentException("Spawn requires solid ground and two passable blocks");
        }
    }

    private static String formatLocation(Location location) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", location.getX(), location.getY(), location.getZ());
    }

    public record GameRuleSnapshot(boolean loaded, Map<String, String> values) {
        public GameRuleSnapshot {
            values = Map.copyOf(values);
        }
    }
}
