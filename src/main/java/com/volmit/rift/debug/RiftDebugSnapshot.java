package com.volmit.rift.debug;

import com.volmit.rift.config.RiftConfig;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

record RiftDebugSnapshot(
        Instant generatedAt,
        String riftVersion,
        String serverName,
        String serverVersion,
        String bukkitVersion,
        String minecraftVersion,
        boolean onlineMode,
        int onlinePlayers,
        int maximumPlayers,
        int viewDistance,
        int simulationDistance,
        String defaultGameMode,
        boolean hardcore,
        boolean allowFlight,
        boolean whitelist,
        int spawnRadius,
        int idleTimeout,
        int pendingSchedulerTasks,
        int loadedWorlds,
        Map<String, Integer> worldsByEnvironment,
        String tickRates,
        String averageTickMillis,
        String platform,
        boolean dynamicWorldLifecycle,
        HotReloadState hotReload,
        boolean metricsRunning,
        boolean worldContainerWritable,
        String activeLocale,
        Path activeLanguageFile,
        List<String> availableLocales,
        String remoteLanguageCatalogReference,
        String remoteLanguageCatalogFailure,
        String remoteLanguageDownloadFailure,
        int managedProfiles,
        int discoveredWorldStorage,
        String senderType,
        RiftConfig config,
        List<PluginState> plugins,
        List<WorldState> worlds,
        List<TrashState> quarantine,
        Path dataDirectory,
        Path worldContainer,
        Path codeSource
) {
    record HotReloadState(
            boolean running,
            long generation,
            long polls,
            long successfulPolls,
            long failures,
            long lastPollEpochMillis,
            long lastSuccessfulPollEpochMillis,
            String lastFailure
    ) {
    }

    record PluginState(
            String name,
            String version,
            boolean enabled,
            String mainClass,
            List<String> authors,
            String loadOrder,
            String apiVersion,
            List<String> hardDependencies,
            List<String> softDependencies
    ) {
    }

    record WorldState(
            String name,
            boolean loaded,
            boolean managed,
            boolean presentOnDisk,
            boolean autoLoad,
            boolean protectedWorld,
            String environment,
            String worldType,
            String generator,
            long seed,
            String configuredDirectory,
            String detectedDirectory,
            String storageLayout,
            boolean operationActive
    ) {
    }

    record TrashState(String id, String worldName, Instant deletedAt) {
    }
}
