package com.volmit.rift.config;

import art.arcane.volmlib.util.config.ConfigIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftConfigManagerTest {
    @Test
    void updateNotificationsDefaultOnAndNotifyAfterCommittedOptOut() {
        List<RiftConfig> activations = new ArrayList<>();
        RiftConfigManager manager = new RiftConfigManager(new RiftConfigManager.Options(
                temporaryDirectory.resolve("updates.toml").toFile(), ConfigIo.SILENT,
                Logger.getLogger("updates"), activations::add));
        assertThat(manager.loadInitial()).isTrue();
        assertThat(manager.get().isUpdateNotifications()).isTrue();
        assertThat(manager.update(config -> {
            config.setUpdateNotifications(false);
            return config;
        })).isTrue();
        assertThat(manager.get().isUpdateNotifications()).isFalse();
        assertThat(activations.getLast().isUpdateNotifications()).isFalse();
        assertThat(manager.reload()).isTrue();
        assertThat(manager.get().isUpdateNotifications()).isFalse();
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void notifiesTheListenerAfterEveryCommittedMetricsChange() {
        List<RiftConfig> activations = new ArrayList<>();
        RiftConfigManager.Options options = new RiftConfigManager.Options(
                temporaryDirectory.resolve("config.toml").toFile(),
                ConfigIo.SILENT,
                Logger.getLogger("RiftConfigManagerTest"),
                activations::add
        );
        RiftConfigManager manager = new RiftConfigManager(options);

        assertThat(manager.loadInitial()).isTrue();
        assertThat(activations).hasSize(1);
        assertThat(activations.get(0).isBstatsEnabled()).isTrue();

        assertThat(manager.update(config -> {
            config.setBstatsEnabled(false);
            return config;
        })).isTrue();
        assertThat(activations).hasSize(2);
        assertThat(activations.get(1).isBstatsEnabled()).isFalse();
    }
}
