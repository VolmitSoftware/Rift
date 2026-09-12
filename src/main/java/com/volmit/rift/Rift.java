package com.volmit.rift;

import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.diagnostics.BukkitDebugDump;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.command.RiftCommandService;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.debug.RiftDebugContributor;
import com.volmit.rift.feedback.RiftFeedbackService;
import com.volmit.rift.gui.RiftConfigMenu;
import com.volmit.rift.hotload.RiftHotloadService;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.metrics.RiftMetricsService;
import com.volmit.rift.storage.RiftPaths;
import com.volmit.rift.storage.TrashStore;
import com.volmit.rift.storage.WorldDirectoryResolver;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfileStore;
import com.volmit.rift.world.PlatformCapabilities;
import com.volmit.rift.world.WorldInventory;
import com.volmit.rift.world.WorldLifecycleService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class Rift extends JavaPlugin implements ReloadAware {
    private static Rift instance;

    private final AtomicBoolean stopped = new AtomicBoolean();
    private RiftPaths paths;
    private RiftConfigManager config;
    private RiftLocalization language;
    private WorldNamePolicy names;
    private WorldDirectoryResolver directories;
    private WorldProfileStore profiles;
    private TrashStore trash;
    private PlatformCapabilities capabilities;
    private WorldInventory worldInventory;
    private WorldLifecycleService lifecycle;
    private RiftFeedbackService feedback;
    private BukkitDebugDump debugDump;
    private BukkitLanguageSwitcher languageSwitcher;
    private RiftMetricsService metrics;
    private RiftHotloadService hotload;
    private RiftConfigMenu configMenu;
    private RiftCommandService commands;

    public static Rift get() {
        return Objects.requireNonNull(instance, "Rift is not enabled");
    }

    @Override
    public void onEnable() {
        long startedNanos = System.nanoTime();
        instance = this;
        stopped.set(false);
        try {
            paths = new RiftPaths(getDataFolder(), getServer().getWorldContainer());
            config = new RiftConfigManager(this, paths.configFile());
            if (!config.loadInitial()) {
                throw new IllegalStateException("Rift could not establish a valid config.toml");
            }
            language = new RiftLocalization(this, config::get, paths.languagesDirectory());
            if (!language.loadInitial()) {
                throw new IllegalStateException("Rift could not establish a valid selected language");
            }
            language.initializeSelections(() -> config.get().getLanguage(), this::selectDefaultLanguage);
            metrics = new RiftMetricsService(this);
            metrics.install(config.get().isBstatsEnabled());
            names = new WorldNamePolicy(paths.worldContainer());
            if (getServer().getWorlds().isEmpty()) {
                throw new IllegalStateException("Bukkit exposed no primary world at POSTWORLD startup");
            }
            directories = new WorldDirectoryResolver(
                    paths.worldContainer(),
                    getServer().getWorlds().get(0).getWorldFolder().toPath(),
                    names
            );
            profiles = new WorldProfileStore(this, paths.profilesDirectory(), names);
            if (!profiles.loadAll()) {
                throw new IllegalStateException("Rift could not establish valid world profile storage");
            }
            trash = new TrashStore(this, paths.trashManifestDirectory());
            trash.loadAll();
            capabilities = new PlatformCapabilities(getServer());
            worldInventory = new WorldInventory(this, directories, profiles);
            worldInventory.refreshDiskSnapshot();
            feedback = new RiftFeedbackService(this, config, language);
            lifecycle = new WorldLifecycleService(
                    this,
                    config,
                    language,
                    paths,
                    names,
                    directories,
                    profiles,
                    trash,
                    capabilities,
                    worldInventory,
                    feedback
            );
            hotload = new RiftHotloadService(this, config, language, profiles, trash, worldInventory);
            configMenu = new RiftConfigMenu(this, config, language, hotload);
            getServer().getPluginManager().registerEvents(configMenu, this);
            languageSwitcher = BukkitLanguageSwitcher.register(
                    this,
                    language.selections(),
                    new BukkitLanguageSwitcher.Options(
                            "rift",
                            "rift.config",
                            RiftCommandService.theme(),
                            language.directorResolver(),
                            language.editorOptions(),
                            (sender, change) -> language.text(sender, RiftMessages.GUI_SETTING_SAVED,
                                    MessageArgs.builder()
                                            .untrusted("setting", change.key())
                                            .untrusted("after", change.after())
                                            .untrusted("before", change.before())
                                            .build())
                    )
            );
            debugDump = BukkitDebugDump.create(this, new BukkitDebugDump.Options(
                    () -> config.get().isDebugUploadEnabled(),
                    new RiftDebugContributor(this),
                    new BukkitDebugDump.Presentation(
                            "/rift debug dump",
                            "/rift debug",
                            RiftCommandService.theme(),
                            language::text
                    )
            ));
            commands = new RiftCommandService(this);
            commands.register();
            lifecycle.loadManagedAtStartup();
            hotload.start();
            long startupMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
            if (config.get().isSplashScreen()) {
                RiftSplashScreen.print(this);
            }
            getLogger().info(readyMessage(startupMillis, schedulerName(), profiles.all().size()));
        } catch (Throwable exception) {
            getLogger().log(Level.SEVERE, "Rift failed to enable", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stop();
    }

    @Override
    public void onPreUnload(PreUnloadReason reason) {
        stop();
    }

    public RiftPaths paths() {
        return paths;
    }

    public RiftConfigManager config() {
        return config;
    }

    public RiftLocalization language() {
        return language;
    }

    public WorldProfileStore profiles() {
        return profiles;
    }

    public TrashStore trash() {
        return trash;
    }

    public PlatformCapabilities capabilities() {
        return capabilities;
    }

    public WorldInventory worldInventory() {
        return worldInventory;
    }

    public WorldLifecycleService lifecycle() {
        return lifecycle;
    }

    public BukkitDebugDump debugDump() {
        return debugDump;
    }

    public BukkitLanguageSwitcher languageSwitcher() {
        return languageSwitcher;
    }

    public RiftConfigMenu configMenu() {
        return configMenu;
    }

    public RiftHotloadService hotload() {
        return hotload;
    }

    public RiftMetricsService metrics() {
        return metrics;
    }

    public void configurationInstalled(RiftConfig installed) {
        if (metrics != null) {
            metrics.install(installed.isBstatsEnabled());
        }
    }

    static String readyMessage(long startupMillis, String schedulerName, int managedWorlds) {
        String worldLabel = managedWorlds == 1 ? "world" : "worlds";
        return "Rift ready in " + startupMillis + " ms with " + schedulerName + " scheduling and "
                + managedWorlds + " managed " + worldLabel + ".";
    }

    private void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (configMenu != null) {
            configMenu.shutdown();
        }
        if (debugDump != null) {
            debugDump.close();
        }
        if (languageSwitcher != null) {
            languageSwitcher.close();
        }
        if (hotload != null) {
            hotload.close();
        }
        if (language != null) {
            language.close();
        }
        if (metrics != null) {
            metrics.close();
        }
        if (lifecycle != null) {
            lifecycle.close();
        }
        if (feedback != null) {
            feedback.close();
        }
        FoliaScheduler.cancelTasks(this);
        if (instance == this) {
            instance = null;
        }
    }

    private synchronized void selectDefaultLanguage(String locale, LocalizationSnapshot prepared) throws IOException {
        boolean updated = config.update(candidate -> {
            candidate.setLanguage(locale);
            return candidate;
        });
        if (!updated) {
            throw new IOException("Unable to persist the Rift server language");
        }
        language.install(new RiftLocalization.PreparedLanguage(
                locale,
                language.file(locale),
                prepared,
                true
        ));
        if (hotload != null) {
            hotload.noteConfigWrite();
        }
    }

    private String schedulerName() {
        return capabilities.isFolia() ? "Folia region" : "Bukkit main-thread";
    }
}
