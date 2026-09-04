package com.volmit.rift.world;

import art.arcane.volmlib.util.config.ConfigJson;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.feedback.RiftFeedbackService;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.RiftPaths;
import com.volmit.rift.storage.TrashEntry;
import com.volmit.rift.storage.TrashStore;
import com.volmit.rift.storage.WorldDirectoryResolver;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class WorldLifecycleService {
    private static final DateTimeFormatter TRASH_ID_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final Rift plugin;
    private final RiftConfigManager config;
    private final RiftLocalization language;
    private final RiftPaths paths;
    private final WorldNamePolicy names;
    private final WorldDirectoryResolver directories;
    private final WorldProfileStore profiles;
    private final TrashStore trash;
    private final PlatformCapabilities capabilities;
    private final WorldInventory inventory;
    private final RiftFeedbackService feedback;
    private final WorldOperationLocks locks = new WorldOperationLocks();
    private final DeleteConfirmationService confirmations = new DeleteConfirmationService();
    private final TeleportService teleports;

    public WorldLifecycleService(
            Rift plugin,
            RiftConfigManager config,
            RiftLocalization language,
            RiftPaths paths,
            WorldNamePolicy names,
            WorldDirectoryResolver directories,
            WorldProfileStore profiles,
            TrashStore trash,
            PlatformCapabilities capabilities,
            WorldInventory inventory,
            RiftFeedbackService feedback
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.language = Objects.requireNonNull(language, "language");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.names = Objects.requireNonNull(names, "names");
        this.directories = Objects.requireNonNull(directories, "directories");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.trash = Objects.requireNonNull(trash, "trash");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.teleports = new TeleportService(plugin);
    }

    public void loadManagedAtStartup() {
        ManagedWorldStartupReconciler reconciler = new ManagedWorldStartupReconciler(
                new ManagedWorldStartupReconciler.Options(
                        plugin.getLogger(),
                        directories,
                        profiles,
                        inventory,
                        name -> Bukkit.getWorld(name) != null
                )
        );
        List<WorldProfile> availableProfiles = reconciler.reconcile();
        if (!config.get().isAutoLoadManagedWorlds()) {
            return;
        }
        if (!capabilities.supportsDynamicWorldLifecycle()) {
            plugin.getLogger().warning("Managed-world startup loading is disabled on Folia because dynamic world loading is unsupported");
            return;
        }
        for (WorldProfile profile : availableProfiles) {
            if (!profile.isAutoLoad() || Bukkit.getWorld(profile.getName()) != null) {
                continue;
            }
            try {
                World world = createWorld(profile);
                if (world == null) {
                    throw new IOException("Bukkit returned no world");
                }
                verbose("Loaded managed world " + profile.getName() + " during startup");
            } catch (Throwable exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to load managed world " + profile.getName() + " during startup", exception);
            }
        }
    }

    public void create(
            CommandSender sender,
            String name,
            World.Environment environment,
            String generator,
            String seedText,
            WorldType type
    ) {
        queueDynamic(sender, name, "create", () -> {
            String validName = names.requireValid(name);
            if (Bukkit.getWorld(validName) != null || directories.exists(validName) || profiles.find(validName).isPresent()) {
                throw new IOException("a world, directory, or profile with that name already exists");
            }
            requireGenerator(generator);
            OptionalLong seed = parseSeed(seedText);
            WorldCreator creator = new WorldCreator(validName)
                    .environment(Objects.requireNonNull(environment, "environment"))
                    .type(Objects.requireNonNull(type, "type"));
            applyGenerator(creator, generator);
            if (seed.isPresent()) {
                creator.seed(seed.getAsLong());
            }
            World world = creator.createWorld();
            if (world == null) {
                throw new IOException("Bukkit returned no world");
            }
            try {
                profiles.save(profileFromWorld(world, generator, type, true));
            } catch (IOException exception) {
                Bukkit.unloadWorld(world, true);
                throw exception;
            }
            inventory.markPresent(validName);
            language.send(sender, RiftMessages.CREATED, worldArg(validName));
            feedback.world(sender, "Created", validName);
        });
    }

    public void importWorld(CommandSender sender, String name, String generator, boolean autoLoad) {
        queueDynamic(sender, name, "import", () -> {
            String validName = names.requireValid(name);
            if (profiles.find(validName).isPresent()) {
                throw new IOException("world is already managed");
            }
            requireGenerator(generator);
            World world = Bukkit.getWorld(validName);
            boolean loadedHere = false;
            if (world == null) {
                directories.require(validName);
                WorldCreator creator = new WorldCreator(validName);
                applyGenerator(creator, generator);
                world = creator.createWorld();
                loadedHere = true;
            }
            if (world == null) {
                throw new IOException("Bukkit returned no world");
            }
            try {
                profiles.save(profileFromWorld(world, generator, WorldType.NORMAL, autoLoad));
            } catch (IOException exception) {
                if (loadedHere) {
                    Bukkit.unloadWorld(world, true);
                }
                throw exception;
            }
            inventory.markPresent(validName);
            language.send(sender, RiftMessages.IMPORTED, worldArg(validName));
            feedback.world(sender, "Imported", validName);
        });
    }

    public void load(CommandSender sender, String name, String generator) {
        queueDynamic(sender, name, "load", () -> {
            String validName = names.requireValid(name);
            if (Bukkit.getWorld(validName) != null) {
                throw new IOException("world is already loaded");
            }
            WorldProfile profile = profiles.find(validName).orElse(null);
            World world;
            if (profile == null) {
                directories.require(validName);
                requireGenerator(generator);
                WorldCreator creator = new WorldCreator(validName);
                applyGenerator(creator, generator);
                world = creator.createWorld();
            } else {
                world = createWorld(profile);
            }
            if (world == null) {
                throw new IOException("Bukkit returned no world");
            }
            inventory.markPresent(validName);
            language.send(sender, RiftMessages.LOADED, worldArg(validName));
            feedback.world(sender, "Loaded", validName);
        });
    }

    public void unload(CommandSender sender, String name, boolean save) {
        queueDynamic(sender, name, "unload", () -> {
            World world = requireLoaded(name);
            unloadWorld(world, save);
            language.send(sender, RiftMessages.UNLOADED, worldArg(world.getName()));
            feedback.world(sender, "Unloaded", world.getName());
        });
    }

    public void delete(CommandSender sender, String name) {
        String validName;
        try {
            validName = names.requireValid(name);
        } catch (IllegalArgumentException exception) {
            fail(sender, "delete", Objects.requireNonNullElse(name, ""), exception);
            return;
        }
        RiftConfig current = config.get();
        if (!current.isAllowWorldDeletion()) {
            fail(sender, "delete", validName, new IOException("world quarantine is disabled in config.toml"));
            return;
        }
        if (!confirmations.confirm(sender.getName(), validName, current.getDeleteConfirmationSeconds())) {
            language.send(sender, RiftMessages.DELETE_CONFIRM, MessageArgs.builder()
                    .untrusted("world", validName)
                    .untrusted("seconds", current.getDeleteConfirmationSeconds())
                    .build());
            return;
        }
        queueDynamic(sender, validName, "delete", () -> quarantine(sender, validName));
    }

    public void restore(CommandSender sender, String id) {
        TrashEntry entry = trash.find(id).orElse(null);
        if (entry == null) {
            fail(sender, "restore", Objects.requireNonNullElse(id, ""), new IOException("quarantine entry does not exist or is invalid"));
            return;
        }
        queueDynamic(sender, entry.getWorldName(), "restore", () -> restore(sender, entry));
    }

    public void setAutoLoad(CommandSender sender, String name, boolean enabled) {
        updateProfile(sender, name, "auto-load", profile -> profile.setAutoLoad(enabled), String.valueOf(enabled));
    }

    public void setProtected(CommandSender sender, String name, boolean enabled) {
        if (!enabled && isPrimaryFamily(name)) {
            fail(sender, "protect", name, new IOException("primary world protection cannot be disabled"));
            return;
        }
        updateProfile(sender, name, "protected", profile -> profile.setProtectedWorld(enabled), String.valueOf(enabled));
    }

    public void teleport(CommandSender sender, Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            fail(sender, "teleport", worldName, new IOException("world is not loaded"));
            return;
        }
        teleports.teleport(player, world.getSpawnLocation(), success -> {
            if (success) {
                language.send(sender, RiftMessages.TELEPORTED, MessageArgs.builder()
                        .untrusted("player", player.getName())
                        .untrusted("world", world.getName())
                        .build());
                feedback.teleport(player, world.getName());
            } else {
                fail(sender, "teleport", worldName, new IOException("server rejected the teleport"));
            }
        });
    }

    public Set<String> configuredGenerators() {
        Set<String> generators = new LinkedHashSet<>();
        generators.add("vanilla");
        generators.add("void");
        for (WorldProfile profile : profiles.all()) {
            if (!profile.getGenerator().isBlank()) {
                generators.add(profile.getGenerator());
            }
        }
        return Collections.unmodifiableSet(generators);
    }

    public boolean isBusy(String worldName) {
        return locks.isActive(worldName);
    }

    public void close() {
        confirmations.clear();
    }

    private void quarantine(CommandSender sender, String name) throws Exception {
        WorldProfile profile = profiles.find(name).orElseThrow(() -> new IOException("only managed worlds can be quarantined"));
        requireMutable(profile, "delete");
        World loaded = Bukkit.getWorld(profile.getName());
        Path source = loaded == null
                ? directories.require(profile)
                : loaded.getWorldFolder().toPath().toAbsolutePath().normalize();
        WorldProfile storedProfile = copyProfile(profile);
        storedProfile.setDirectory(directories.relative(source, profile.getName()));
        if (loaded != null) {
            unloadWorld(loaded, true);
        }
        Files.createDirectories(paths.quarantineDirectory());
        String id = TRASH_ID_TIME.format(Instant.now()) + "-" + profile.getName();
        Path destination = paths.quarantineDirectory().resolve(id);
        if (Files.exists(destination)) {
            throw new IOException("quarantine id already exists: " + id);
        }
        TrashEntry entry = TrashEntry.from(id, storedProfile);
        moveDirectory(source, destination);
        boolean manifestSaved = false;
        try {
            trash.save(entry);
            manifestSaved = true;
            profiles.delete(profile.getName());
        } catch (Throwable failure) {
            boolean rolledBack = rollbackMove(destination, source, "quarantine " + profile.getName());
            if (rolledBack) {
                try {
                    profiles.save(storedProfile);
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                if (manifestSaved) {
                    try {
                        trash.delete(id);
                    } catch (Throwable cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw failure;
        }
        language.send(sender, RiftMessages.QUARANTINED, MessageArgs.builder()
                .untrusted("world", profile.getName())
                .untrusted("id", id)
                .build());
        inventory.markAbsent(profile.getName());
        feedback.world(sender, "Quarantined", profile.getName());
    }

    private void restore(CommandSender sender, TrashEntry entry) throws Exception {
        String name = names.requireValid(entry.getWorldName());
        Path source = paths.quarantineDirectory().resolve(entry.getId()).toAbsolutePath().normalize();
        if (!paths.quarantineDirectory().toAbsolutePath().normalize().equals(source.getParent())) {
            throw new IOException("quarantine path escapes its directory");
        }
        if (!isQuarantinedWorldDirectory(source)) {
            throw new IOException("quarantined world directory is missing or invalid");
        }
        WorldProfile restoredProfile = entry.toProfile();
        Path destination = directories.restoreTarget(restoredProfile);
        if (Files.exists(destination) || directories.exists(name)
                || profiles.find(name).isPresent() || Bukkit.getWorld(name) != null) {
            throw new IOException("restore target already exists");
        }
        Files.createDirectories(destination.getParent());
        moveDirectory(source, destination);
        boolean profileSaved = false;
        try {
            profiles.save(restoredProfile);
            profileSaved = true;
            trash.delete(entry.getId());
        } catch (Throwable failure) {
            boolean rolledBack = rollbackMove(destination, source, "restore " + name);
            if (rolledBack && profileSaved) {
                try {
                    profiles.delete(name);
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        language.send(sender, RiftMessages.RESTORED, MessageArgs.builder()
                .untrusted("world", name)
                .untrusted("id", entry.getId())
                .build());
        inventory.markPresent(name);
        feedback.world(sender, "Restored", name);
    }

    private void updateProfile(
            CommandSender sender,
            String name,
            String setting,
            Consumer<WorldProfile> mutation,
            String value
    ) {
        queueIo(sender, name, setting, () -> {
            WorldProfile existing = profiles.find(name).orElseThrow(() -> new IOException("world is not managed"));
            WorldProfile candidate = ConfigJson.fromJson(ConfigJson.toJson(existing, false), WorldProfile.class);
            mutation.accept(candidate);
            profiles.save(candidate);
            language.send(sender, RiftMessages.PROFILE_UPDATED, MessageArgs.builder()
                    .untrusted("setting", setting)
                    .untrusted("world", candidate.getName())
                    .untrusted("value", value)
                    .build());
        });
    }

    private World createWorld(WorldProfile profile) throws IOException {
        directories.require(profile);
        requireGenerator(profile.getGenerator());
        WorldCreator creator = new WorldCreator(profile.getName())
                .environment(profile.environment())
                .type(profile.type())
                .seed(profile.getSeed());
        applyGenerator(creator, profile.getGenerator());
        World world = creator.createWorld();
        if (world != null) {
            persistDirectory(profile, world);
        }
        return world;
    }

    private WorldProfile profileFromWorld(
            World world,
            String generator,
            WorldType type,
            boolean autoLoad
    ) throws IOException {
        WorldProfile profile = WorldProfile.fromWorld(world, generator, type, autoLoad);
        profile.setDirectory(directories.relative(world.getWorldFolder().toPath(), world.getName()));
        return profile;
    }

    private void persistDirectory(WorldProfile profile, World world) throws IOException {
        String directory = directories.relative(world.getWorldFolder().toPath(), world.getName());
        if (directory.equals(profile.getDirectory())) {
            return;
        }
        WorldProfile candidate = copyProfile(profile);
        candidate.setDirectory(directory);
        profiles.save(candidate);
    }

    private static WorldProfile copyProfile(WorldProfile profile) {
        return ConfigJson.fromJson(ConfigJson.toJson(profile, false), WorldProfile.class);
    }

    private static boolean isQuarantinedWorldDirectory(Path directory) {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return Files.isRegularFile(directory.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)
                || Files.isDirectory(directory.resolve("region"), LinkOption.NOFOLLOW_LINKS)
                || Files.isRegularFile(directory.resolve("paper-world.yml"), LinkOption.NOFOLLOW_LINKS);
    }

    private void unloadWorld(World world, boolean save) throws IOException {
        WorldProfile profile = profiles.find(world.getName()).orElse(null);
        if (profile != null) {
            requireMutable(profile, "unload");
        }
        if (isPrimaryFamily(world.getName())) {
            throw new IOException("primary world family cannot be unloaded");
        }
        World evacuation = evacuationWorld();
        if (evacuation.equals(world)) {
            throw new IOException("configured evacuation world cannot be unloaded");
        }
        Location destination = evacuation.getSpawnLocation();
        for (Player player : List.copyOf(world.getPlayers())) {
            if (!player.teleport(destination)) {
                throw new IOException("could not evacuate player " + player.getName());
            }
        }
        if (save) {
            world.save();
        }
        if (!Bukkit.unloadWorld(world, save)) {
            throw new IOException("Bukkit rejected the world unload");
        }
    }

    private World evacuationWorld() throws IOException {
        RiftConfig current = config.get();
        if (!current.getEvacuationWorld().isBlank()) {
            World configured = Bukkit.getWorld(current.getEvacuationWorld());
            if (configured == null) {
                throw new IOException("configured evacuation world is not loaded: " + current.getEvacuationWorld());
            }
            return configured;
        }
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            throw new IOException("server has no loaded evacuation world");
        }
        return worlds.get(0);
    }

    private void requireMutable(WorldProfile profile, String operation) throws IOException {
        if (profile.isProtectedWorld()) {
            throw new IOException("world profile is protected from " + operation);
        }
        if (isPrimaryFamily(profile.getName())) {
            throw new IOException("primary world family is protected from " + operation);
        }
    }

    private boolean isPrimaryFamily(String name) {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty() || name == null) {
            return false;
        }
        String primary = worlds.get(0).getName();
        return name.equalsIgnoreCase(primary)
                || name.equalsIgnoreCase(primary + "_nether")
                || name.equalsIgnoreCase(primary + "_the_end");
    }

    private World requireLoaded(String name) throws IOException {
        String validName = names.requireValid(name);
        World world = Bukkit.getWorld(validName);
        if (world == null) {
            throw new IOException("world is not loaded");
        }
        return world;
    }

    private void requireGenerator(String generator) throws IOException {
        if (isVanilla(generator) || isVoid(generator)) {
            return;
        }
        String value = generator.trim();
        String owner = value.contains(":") ? value.substring(0, value.indexOf(':')) : value;
        Plugin generatorPlugin = Bukkit.getPluginManager().getPlugin(owner);
        if (generatorPlugin == null || !generatorPlugin.isEnabled()) {
            throw new IOException("generator plugin is not enabled: " + owner);
        }
    }

    private static boolean isVanilla(String generator) {
        return generator == null || generator.isBlank()
                || generator.equalsIgnoreCase("vanilla")
                || generator.equalsIgnoreCase("normal");
    }

    private static boolean isVoid(String generator) {
        return generator != null && generator.trim().equalsIgnoreCase("void");
    }

    private static void applyGenerator(WorldCreator creator, String generator) {
        if (isVanilla(generator)) {
            return;
        }
        if (isVoid(generator)) {
            creator.generator(VoidChunkGenerator.instance());
            return;
        }
        creator.generator(generator.trim());
    }

    private static OptionalLong parseSeed(String value) throws IOException {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("random")) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException exception) {
            throw new IOException("seed must be 'random' or a signed 64-bit integer", exception);
        }
    }

    private void queueDynamic(CommandSender sender, String worldName, String operation, ThrowingOperation task) {
        if (!capabilities.supportsDynamicWorldLifecycle()) {
            language.send(sender, RiftMessages.FOLIA_LIMIT, MessageArgs.builder().untrusted("operation", operation).build());
            return;
        }
        queue(sender, worldName, operation, task, false);
    }

    private void queueIo(CommandSender sender, String worldName, String operation, ThrowingOperation task) {
        queue(sender, worldName, operation, task, true);
    }

    private void queue(CommandSender sender, String worldName, String operation, ThrowingOperation task, boolean async) {
        String identity = worldName == null ? "" : worldName;
        if (!locks.acquire(identity)) {
            fail(sender, operation, identity, new IOException("another Rift operation is already active for this world"));
            return;
        }
        language.send(sender, RiftMessages.OPERATION_QUEUED, MessageArgs.builder()
                .untrusted("operation", operation)
                .untrusted("world", identity)
                .build());
        Runnable runnable = () -> {
            try {
                task.run();
                if (!FoliaScheduler.runAsync(plugin, inventory::refreshDiskSnapshot)) {
                    plugin.getLogger().warning("Unable to schedule the world inventory refresh after " + operation);
                }
            } catch (Throwable exception) {
                fail(sender, operation, identity, exception);
            } finally {
                locks.release(identity);
            }
        };
        boolean scheduled = async
                ? FoliaScheduler.runAsync(plugin, runnable)
                : FoliaScheduler.runGlobal(plugin, runnable);
        if (!scheduled) {
            locks.release(identity);
            fail(sender, operation, identity, new IOException("scheduler rejected the operation"));
        }
    }

    private void fail(CommandSender sender, String operation, String world, Throwable exception) {
        String reason = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        plugin.getLogger().log(Level.SEVERE, "Rift " + operation + " failed for " + world, exception);
        language.send(sender, RiftMessages.OPERATION_FAILED, MessageArgs.builder()
                .untrusted("operation", operation)
                .untrusted("world", world)
                .untrusted("reason", reason)
                .build());
        feedback.failure(sender, operation, world);
    }

    private void verbose(String message) {
        if (config.get().isVerbose()) {
            plugin.getLogger().info(message);
        }
    }

    private boolean rollbackMove(Path source, Path target, String context) {
        try {
            if (Files.exists(source) && !Files.exists(target)) {
                moveDirectory(source, target);
            }
            return Files.exists(target) && !Files.exists(source);
        } catch (IOException rollbackFailure) {
            plugin.getLogger().log(Level.SEVERE, "Failed to roll back Rift " + context, rollbackFailure);
            return false;
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static MessageArgs worldArg(String world) {
        return MessageArgs.builder().untrusted("world", world).build();
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
