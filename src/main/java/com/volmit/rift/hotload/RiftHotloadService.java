package com.volmit.rift.hotload;

import art.arcane.volmlib.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.storage.WorldProfileStore;
import com.volmit.rift.storage.TrashStore;
import com.volmit.rift.world.WorldInventory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class RiftHotloadService implements AutoCloseable {
    private final Rift plugin;
    private final RiftConfigManager config;
    private final RiftLocalization language;
    private final WorldProfileStore profiles;
    private final TrashStore trash;
    private final WorldInventory inventory;
    private final ConfigHotloadEngine engine;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong pollCount = new AtomicLong();
    private final AtomicLong successfulPollCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong lastPollEpochMillis = new AtomicLong();
    private final AtomicLong lastSuccessfulPollEpochMillis = new AtomicLong();
    private volatile String lastFailure = "";

    public RiftHotloadService(
            Rift plugin,
            RiftConfigManager config,
            RiftLocalization language,
            WorldProfileStore profiles,
            TrashStore trash,
            WorldInventory inventory
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.language = Objects.requireNonNull(language, "language");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.trash = Objects.requireNonNull(trash, "trash");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.engine = new ConfigHotloadEngine(
                this::isManagedFile,
                this::knownFiles,
                RiftHotloadService::read,
                ConfigFileSupport::normalize
        );
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        configure();
        language.setSelfWriteListener(engine::noteSelfWrite);
        generation.incrementAndGet();
        scheduleNext();
    }

    public void noteConfigWrite() {
        try {
            engine.noteSelfWrite(config.file(), config.readRaw());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to update Rift hot-reload state after a config write", exception);
        }
        configChanged();
    }

    public void configChanged() {
        if (!running.get()) {
            return;
        }
        generation.incrementAndGet();
        configure();
        scheduleNext();
    }

    public boolean isRunning() {
        return running.get();
    }

    public Status status() {
        return new Status(
                running.get(),
                generation.get(),
                pollCount.get(),
                successfulPollCount.get(),
                failureCount.get(),
                lastPollEpochMillis.get(),
                lastSuccessfulPollEpochMillis.get(),
                lastFailure
        );
    }

    @Override
    public void close() {
        running.set(false);
        generation.incrementAndGet();
        language.setSelfWriteListener(null);
        engine.clear();
    }

    private void scheduleNext() {
        if (!running.get() || !plugin.isEnabled()) {
            return;
        }
        RiftConfig current = config.get();
        long delayTicks = Math.max(1L, (current.getHotReloadPollMillis() + 49L) / 50L);
        long expectedGeneration = generation.get();
        if (!FoliaScheduler.runAsync(plugin, () -> poll(expectedGeneration), delayTicks)) {
            plugin.getLogger().warning("Rift hot-reload scheduler rejected the next poll");
            failureCount.incrementAndGet();
            lastFailure = "Scheduler rejected the next poll";
            running.set(false);
        }
    }

    private void poll(long expectedGeneration) {
        if (!running.get() || generation.get() != expectedGeneration) {
            return;
        }
        pollCount.incrementAndGet();
        lastPollEpochMillis.set(System.currentTimeMillis());
        boolean reconfigure = false;
        try {
            inventory.refreshIfChanged();
            language.refreshAvailableLocales();
            Set<ConfigHotloadEngine.StableContentSnapshot> snapshots = engine.pollTouchedSnapshots();
            for (ConfigHotloadEngine.StableContentSnapshot snapshot : snapshots) {
                boolean configFile = sameFile(snapshot.file(), config.file());
                boolean applied = engine.processSnapshotChange(snapshot, ignored -> apply(snapshot), delta -> {
                    if (config.get().isVerbose()) {
                        plugin.getLogger().info("Hot-reloaded " + delta.file().getName());
                    }
                });
                reconfigure |= applied && configFile;
            }
            successfulPollCount.incrementAndGet();
            lastSuccessfulPollEpochMillis.set(System.currentTimeMillis());
        } catch (RuntimeException exception) {
            failureCount.incrementAndGet();
            lastFailure = exception.getClass().getSimpleName() + ": " + Objects.toString(exception.getMessage(), "no message");
            plugin.getLogger().log(Level.SEVERE, "Rift hot-reload poll failed", exception);
        }
        if (reconfigure) {
            configure();
        }
        if (generation.get() == expectedGeneration) {
            scheduleNext();
        }
    }

    private boolean apply(ConfigHotloadEngine.StableContentSnapshot snapshot) {
        File file = snapshot.file();
        if (sameFile(file, config.file())) {
            if (snapshot.normalizedContent() == null) {
                return false;
            }
            try {
                RiftConfig candidate = config.prepareSnapshot(snapshot.normalizedContent());
                RiftLocalization.PreparedLanguage preparedLanguage = language.prepare(candidate.getLanguage());
                if (!preparedLanguage.selectionReady()) {
                    requestConfiguredLanguage(candidate.getLanguage());
                    return false;
                }
                config.install(candidate);
                language.install(preparedLanguage);
                return true;
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Rejected Rift config because its settings or selected language are invalid; the last valid state remains active",
                        exception);
                return false;
            }
        }
        if (sameFile(file, language.file())) {
            return language.reloadSnapshot(snapshot.normalizedContent());
        }
        if (profiles.isProfileFile(file)) {
            return profiles.reloadSnapshot(file, snapshot.normalizedContent());
        }
        if (trash.isTrashFile(file)) {
            return trash.reloadSnapshot(file, snapshot.normalizedContent());
        }
        return false;
    }

    private void configure() {
        RiftConfig current = config.get();
        engine.configure(
                current.getHotReloadPollMillis(),
                current.getHotReloadCooldownMillis(),
                List.of(config.file(), language.file()),
                List.of(profiles.directory(), trash.directory())
        );
    }

    private Collection<File> knownFiles() {
        Set<File> files = new LinkedHashSet<>();
        files.add(config.file());
        files.add(language.file());
        files.addAll(profiles.files());
        files.addAll(trash.files());
        return files;
    }

    private boolean isManagedFile(File file) {
        return sameFile(file, config.file())
                || sameFile(file, language.file())
                || profiles.isProfileFile(file)
                || trash.isTrashFile(file);
    }

    private static boolean sameFile(File left, File right) {
        if (left == null || right == null) {
            return false;
        }
        return left.toPath().toAbsolutePath().normalize().equals(right.toPath().toAbsolutePath().normalize());
    }

    private static String read(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    private void requestConfiguredLanguage(String locale) {
        RemoteLanguageCatalog.RequestState state = language.requestRemote(
                locale,
                result -> configuredLanguageDownloadCompleted(locale, result)
        );
        if (state == RemoteLanguageCatalog.RequestState.SCHEDULED
                || state == RemoteLanguageCatalog.RequestState.IN_FLIGHT) {
            return;
        }
        if (state == RemoteLanguageCatalog.RequestState.CURRENT) {
            activateConfiguredLanguage(locale);
            return;
        }
        plugin.getLogger().warning("Unable to hot-reload Rift language " + locale + ": "
                + languageRequestFailure(state));
    }

    private void configuredLanguageDownloadCompleted(
            String locale,
            RemoteLanguageCatalog.DownloadResult result
    ) {
        if (!result.successful()) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to download Rift language " + locale
                            + "; the previous configuration and language remain active",
                    result.failure());
            return;
        }
        activateConfiguredLanguage(locale);
    }

    private void activateConfiguredLanguage(String locale) {
        try {
            String raw = config.readRaw();
            RiftConfig candidate = config.prepareSnapshot(raw);
            if (!candidate.getLanguage().equalsIgnoreCase(locale)) {
                return;
            }
            RiftLocalization.PreparedLanguage preparedLanguage = language.prepare(locale);
            if (!preparedLanguage.selectionReady()) {
                throw new IOException("Downloaded language file is not available");
            }
            if (!FoliaScheduler.runGlobal(plugin, () -> {
                config.install(candidate);
                language.install(preparedLanguage);
                configChanged();
                plugin.getLogger().info("Activated Rift language " + locale + " after download.");
            })) {
                plugin.getLogger().warning("Unable to schedule Rift language activation for " + locale);
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Downloaded Rift language " + locale + " but could not activate its pending configuration",
                    exception);
        }
    }

    private String languageRequestFailure(RemoteLanguageCatalog.RequestState state) {
        return switch (state) {
            case COOLDOWN -> "the download is cooling down after a recent failure";
            case UNSUPPORTED -> "the locale is not in the Rift language catalog";
            case CLOSED -> "the language downloader is unavailable";
            default -> "the language download could not be started";
        };
    }

    public record Status(
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
}
