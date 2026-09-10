package com.volmit.rift.debug;

import art.arcane.volmlib.util.diagnostics.DebugDumpContributor;
import com.volmit.rift.Rift;
import com.volmit.rift.hotload.RiftHotloadService;
import com.volmit.rift.storage.TrashEntry;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.world.RiftWorldIdentity;
import com.volmit.rift.world.WorldSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

public final class RiftDebugContributor implements DebugDumpContributor {
    private final Rift plugin;

    public RiftDebugContributor(Rift plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Report capture() {
        RiftDebugSnapshot snapshot = captureSnapshot();
        return () -> RiftDebugReport.create(snapshot);
    }

    private RiftDebugSnapshot captureSnapshot() {
        List<RiftDebugSnapshot.PluginState> plugins = new ArrayList<>();
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            PluginDescriptionFile description = installed.getDescription();
            plugins.add(new RiftDebugSnapshot.PluginState(
                    installed.getName(),
                    description.getVersion(),
                    installed.isEnabled(),
                    description.getMain(),
                    List.copyOf(description.getAuthors()),
                    description.getLoad().name(),
                    Objects.toString(description.getAPIVersion(), "unspecified"),
                    List.copyOf(description.getDepend()),
                    List.copyOf(description.getSoftDepend())
            ));
        }
        plugins.sort(Comparator.comparing(RiftDebugSnapshot.PluginState::name, String.CASE_INSENSITIVE_ORDER));

        List<RiftDebugSnapshot.WorldState> worlds = new ArrayList<>();
        for (WorldSnapshot world : plugin.worldInventory().snapshots()) {
            Optional<WorldProfile> profile = plugin.profiles().find(world.name());
            World loadedWorld = RiftWorldIdentity.findLoaded(world.name());
            Path detectedDirectory = loadedWorld == null
                    ? plugin.worldInventory().discoveredDirectory(world.name()).orElse(null)
                    : loadedWorld.getWorldFolder().toPath().toAbsolutePath().normalize();
            if (detectedDirectory == null && profile.isPresent() && !profile.get().getDirectory().isBlank()) {
                detectedDirectory = plugin.paths().worldContainer().resolve(profile.get().getDirectory()).normalize();
            }
            String detectedRelative = relativeWorldDirectory(detectedDirectory, plugin.paths().worldContainer());
            worlds.add(new RiftDebugSnapshot.WorldState(
                    world.name(), world.loaded(), world.managed(), world.presentOnDisk(), world.autoLoad(),
                    world.protectedWorld(),
                    world.environment(),
                    profile.map(WorldProfile::getWorldType)
                            .orElseGet(() -> loadedWorld == null ? "unknown" : loadedWorld.getWorldType().name()),
                    world.generator(),
                    world.seed(),
                    profile.map(WorldProfile::getDirectory).orElse(""),
                    detectedRelative,
                    storageLayout(detectedRelative),
                    plugin.lifecycle().isBusy(world.name())
            ));
        }

        List<RiftDebugSnapshot.TrashState> quarantine = new ArrayList<>();
        for (TrashEntry entry : plugin.trash().all()) {
            quarantine.add(new RiftDebugSnapshot.TrashState(entry.getId(), entry.getWorldName(), entry.deletedAt()));
        }

        Map<String, Integer> worldsByEnvironment = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            worldsByEnvironment.merge(world.getEnvironment().name(), 1, Integer::sum);
        }
        RiftHotloadService.Status hotReload = plugin.hotload().status();

        return new RiftDebugSnapshot(
                Instant.now(),
                plugin.getDescription().getVersion(),
                Bukkit.getName(),
                Bukkit.getVersion(),
                Bukkit.getBukkitVersion(),
                minecraftVersion(),
                Bukkit.getOnlineMode(),
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getMaxPlayers(),
                Bukkit.getViewDistance(),
                Bukkit.getSimulationDistance(),
                Bukkit.getDefaultGameMode().name(),
                Bukkit.isHardcore(),
                Bukkit.getAllowFlight(),
                Bukkit.hasWhitelist(),
                Bukkit.getSpawnRadius(),
                Bukkit.getIdleTimeout(),
                plugin.capabilities().isFolia() ? -1 : Bukkit.getScheduler().getPendingTasks().size(),
                Bukkit.getWorlds().size(),
                Map.copyOf(worldsByEnvironment),
                tickRates(),
                averageTickMillis(),
                plugin.capabilities().isFolia() ? "Folia" : "Bukkit/Paper/Spigot",
                plugin.capabilities().supportsDynamicWorldLifecycle(),
                new RiftDebugSnapshot.HotReloadState(
                        hotReload.running(),
                        hotReload.generation(),
                        hotReload.polls(),
                        hotReload.successfulPolls(),
                        hotReload.failures(),
                        hotReload.lastPollEpochMillis(),
                        hotReload.lastSuccessfulPollEpochMillis(),
                        hotReload.lastFailure()
                ),
                plugin.metrics().isRunning(),
                Files.isWritable(plugin.paths().worldContainer()),
                plugin.language().activeLocale(),
                plugin.language().file().toPath().toAbsolutePath().normalize(),
                plugin.language().availableLocales(),
                plugin.language().remoteCatalogReference().orElse("unavailable"),
                plugin.language().remoteCatalogFailure()
                        .map(failure -> failure.getClass().getSimpleName() + ": "
                                + Objects.toString(failure.getMessage(), "no message"))
                        .orElse("none"),
                plugin.language().remoteDownloadFailure().orElse("none"),
                plugin.profiles().all().size(),
                plugin.worldInventory().discoverWorldDirectories().size(),
                "shared debug command",
                plugin.config().get(),
                List.copyOf(plugins),
                List.copyOf(worlds),
                List.copyOf(quarantine),
                plugin.getDataFolder().toPath().toAbsolutePath().normalize(),
                plugin.paths().worldContainer(),
                codeSource()
        );
    }

    private static String relativeWorldDirectory(Path directory, Path worldContainer) {
        if (directory == null) {
            return "";
        }
        Path normalizedContainer = worldContainer.toAbsolutePath().normalize();
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedContainer)) {
            return normalizedDirectory.toString().replace('\\', '/');
        }
        return normalizedContainer.relativize(normalizedDirectory).toString().replace('\\', '/');
    }

    private static String storageLayout(String relativeDirectory) {
        if (relativeDirectory.isBlank()) {
            return "not detected";
        }
        String normalized = relativeDirectory.replace('\\', '/');
        if (!normalized.contains("/")) {
            return "standalone world folder";
        }
        String lowercase = normalized.toLowerCase(Locale.ROOT);
        if (lowercase.contains("/dimensions/rift/")) {
            return "Rift namespaced dimension";
        }
        if (lowercase.contains("/dimensions/minecraft/")) {
            return "Minecraft namespaced dimension";
        }
        if (lowercase.contains("/dimensions/")) {
            return "other namespaced dimension";
        }
        return "server-managed world folder";
    }

    private Path codeSource() {
        try {
            if (plugin.getClass().getProtectionDomain().getCodeSource() == null) {
                return null;
            }
            File source = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            return source.toPath().toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Unable to resolve the Rift code source for diagnostics", exception);
            return null;
        }
    }

    private static String tickRates() {
        try {
            Object value = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            if (!(value instanceof double[] rates) || rates.length < 3) {
                return "unavailable";
            }
            return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", rates[0], rates[1], rates[2]);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "unavailable";
        }
    }

    private static String minecraftVersion() {
        String version = Bukkit.getBukkitVersion();
        int separator = version.indexOf('-');
        return separator < 0 ? version : version.substring(0, separator);
    }

    private static String averageTickMillis() {
        try {
            Object value = Bukkit.getServer().getClass().getMethod("getAverageTickTime").invoke(Bukkit.getServer());
            if (!(value instanceof Number milliseconds)) {
                return "unavailable";
            }
            return String.format(Locale.ROOT, "%.3f ms", milliseconds.doubleValue());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "unavailable";
        }
    }
}
