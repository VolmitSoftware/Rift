package com.volmit.rift.config;

import art.arcane.volmlib.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.config.ConfigIo;
import art.arcane.volmlib.util.config.ConfigJson;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.io.AtomicFileIO;
import com.volmit.rift.Rift;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RiftConfigManager {
    private static final String SOURCE_TAG = "Rift";

    private final File file;
    private final ConfigIo configIo;
    private final Logger logger;
    private final Consumer<RiftConfig> activationListener;
    private final AtomicReference<RiftConfig> active = new AtomicReference<>(new RiftConfig().normalize());

    public RiftConfigManager(Rift plugin, File file) {
        this(options(plugin, file));
    }

    RiftConfigManager(Options options) {
        Options required = Objects.requireNonNull(options, "options");
        this.file = Objects.requireNonNull(required.file(), "file");
        this.configIo = Objects.requireNonNull(required.configIo(), "configIo");
        this.logger = Objects.requireNonNull(required.logger(), "logger");
        this.activationListener = Objects.requireNonNull(required.activationListener(), "activationListener");
    }

    public RiftConfig get() {
        return copy(active.get());
    }

    public File file() {
        return file;
    }

    public synchronized boolean loadInitial() {
        if (!file.exists()) {
            RiftConfig defaults = new RiftConfig().normalize();
            try {
                write(defaults);
                activate(defaults);
                return true;
            } catch (IOException exception) {
                logger.log(Level.SEVERE, "Unable to create Rift config at " + file, exception);
                return false;
            }
        }
        return reload();
    }

    public synchronized boolean reload() {
        try {
            RiftConfig candidate = ConfigFileSupport.load(
                    configIo,
                    file,
                    null,
                    RiftConfig.class,
                    new RiftConfig(),
                    false,
                    SOURCE_TAG,
                    "Created Rift config",
                    RiftConfig::normalize,
                    false
            );
            activate(candidate.normalize());
            return true;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Rejected invalid Rift config; the last valid settings remain active", exception);
            return false;
        }
    }

    public synchronized boolean reloadSnapshot(String raw) {
        try {
            install(prepareSnapshot(raw));
            return true;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Rejected invalid Rift config snapshot; the last valid settings remain active", exception);
            return false;
        }
    }

    public synchronized RiftConfig prepareSnapshot(String raw) throws IOException {
        return ConfigFileSupport.parseSnapshot(
                raw,
                file,
                RiftConfig.class,
                RiftConfig::normalize
        );
    }

    public synchronized void install(RiftConfig prepared) {
        activate(Objects.requireNonNull(prepared, "prepared"));
    }

    public synchronized boolean update(UnaryOperator<RiftConfig> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        RiftConfig candidate = mutation.apply(copy(active.get()));
        if (candidate == null) {
            throw new IllegalArgumentException("Config mutation returned null");
        }
        candidate.normalize();
        try {
            write(candidate);
            activate(candidate);
            return true;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Unable to persist Rift config; settings were not changed", exception);
            return false;
        }
    }

    public String readRaw() throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private void write(RiftConfig config) throws IOException {
        AtomicFileIO.writeString(file.toPath(), TomlCodec.toToml(config, SOURCE_TAG));
    }

    private void activate(RiftConfig config) {
        RiftConfig installed = copy(config);
        active.set(installed);
        activationListener.accept(installed);
    }

    private static RiftConfig copy(RiftConfig config) {
        return ConfigJson.fromJson(ConfigJson.toJson(config, false), RiftConfig.class).normalize();
    }

    private static Options options(Rift plugin, File file) {
        Rift requiredPlugin = Objects.requireNonNull(plugin, "plugin");
        File requiredFile = Objects.requireNonNull(file, "file");
        Logger logger = requiredPlugin.getLogger();
        return new Options(
                requiredFile,
                new RiftConfigIo(logger, requiredFile.getParentFile()),
                logger,
                requiredPlugin::configurationInstalled
        );
    }

    record Options(File file, ConfigIo configIo, Logger logger, Consumer<RiftConfig> activationListener) {
    }

    private static final class RiftConfigIo implements ConfigIo {
        private final Logger logger;
        private final File dataFolder;

        private RiftConfigIo(Logger logger, File dataFolder) {
            this.logger = logger;
            this.dataFolder = dataFolder;
        }

        @Override
        public void info(String message) {
            logger.info(message);
        }

        @Override
        public void warn(String message) {
            logger.warning(message);
        }

        @Override
        public File dataFolder() {
            return dataFolder;
        }
    }
}
