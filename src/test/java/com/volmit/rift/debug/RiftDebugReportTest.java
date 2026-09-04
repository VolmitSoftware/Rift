package com.volmit.rift.debug;

import com.volmit.rift.config.RiftConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftDebugReportTest {
    @Test
    void createsADetailedSanitizedReport() {
        RiftDebugSnapshot snapshot = new RiftDebugSnapshot(
                Instant.parse("2026-08-28T12:00:00Z"),
                "2.0.0-1.20.1-26.2",
                "Paper\nInjected",
                "1.21.11",
                "1.21.11-R0.1-SNAPSHOT",
                "1.21.11",
                true,
                2,
                100,
                10,
                10,
                "SURVIVAL",
                false,
                true,
                true,
                10,
                0,
                12,
                3,
                Map.of("NORMAL", 2, "NETHER", 1),
                "20.00, 20.00, 20.00",
                "4.200 ms",
                "Bukkit/Paper/Spigot",
                true,
                new RiftDebugSnapshot.HotReloadState(true, 3, 10, 9, 1, 1_777_000_000_000L, 1_777_000_000_000L, "test failure"),
                true,
                true,
                "en_US",
                Path.of("languages", "en_US.toml"),
                List.of("en_US"),
                "main",
                "none",
                "none",
                1,
                1,
                "player",
                new RiftConfig().normalize(),
                List.of(new RiftDebugSnapshot.PluginState(
                        "Rift", "2.0.0-1.20.1-26.2", true, "com.volmit.rift.Rift", List.of("VolmitSoftware"),
                        "POSTWORLD", "1.20", List.of("VolmLib"), List.of("PlaceholderAPI")
                )),
                List.of(new RiftDebugSnapshot.WorldState(
                        "world", true, true, true, true, false, "NORMAL", "NORMAL", "void\nInjected",
                        1234L, "world", "world", "standalone world folder", false
                )),
                List.of(),
                Path.of("."),
                Path.of("."),
                null
        );

        String report = RiftDebugReport.create(snapshot);

        assertThat(report)
                .contains("Version: 2.0.0-1.20.1-26.2")
                .contains("Format: 3")
                .contains("Implementation: Paper Injected")
                .contains("Minecraft version: 1.21.11")
                .contains("Pending scheduler tasks: 12")
                .contains("generator=void Injected")
                .contains("storageLayout=standalone world folder")
                .contains("level.dat=false")
                .contains("main=com.volmit.rift.Rift")
                .contains("Hot reload failures: 1")
                .contains("Active language file:", "en_US.toml")
                .contains("Remote language catalog revision: main")
                .contains("Latest remote language download failure: none")
                .contains("== Rift managed files ==")
                .contains("Classes currently loaded:")
                .contains("Deadlocked:")
                .contains("debugUploadEnabled: true")
                .doesNotContain("Privacy:")
                .doesNotContain("user.dir")
                .doesNotContain("sun.java.command")
                .doesNotContain("password")
                .doesNotContain("token=");
    }
}
