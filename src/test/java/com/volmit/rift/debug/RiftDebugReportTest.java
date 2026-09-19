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
                "2.0.0-26.x",
                "Paper\nInjected",
                "26.2",
                "26.2-R0.1-SNAPSHOT",
                "26.2",
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
                "Paper",
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
                        "Rift", "2.0.0-26.x", true, "com.volmit.rift.Rift", List.of("VolmitSoftware"),
                        "POSTWORLD", "26.1", List.of("VolmLib"), List.of("PlaceholderAPI")
                )),
                List.of(new RiftDebugSnapshot.WorldState(
                        "world", true, true, true, true, false, "NORMAL", "NORMAL", "void\nInjected",
                        1234L, "world/dimensions/minecraft/overworld", "world/dimensions/minecraft/overworld",
                        "Minecraft namespaced dimension", "HARD", "DENY", Map.of("minecraft:keep_inventory", "true"),
                        "1.5,80.0,1.5 yaw=0.0", "size=500.0", "rift.world.world", "No entry", "lobby",
                        List.of("private"), false
                )),
                List.of(),
                Path.of("."),
                Path.of("."),
                null
        );

        String report = RiftDebugReport.create(snapshot);

        assertThat(report)
                .contains("Version: 2.0.0-26.x")
                .contains("Format: 3")
                .contains("Java bytecode target: 25")
                .contains("Implementation: Paper Injected")
                .contains("Minecraft version: 26.2")
                .contains("Pending scheduler tasks: 12")
                .contains("generator=void Injected")
                .contains("storageLayout=Minecraft namespaced dimension")
                .contains("difficultyPolicy=HARD")
                .contains("gameRules={minecraft:keep_inventory=true}")
                .contains("level.dat=false")
                .contains("main=com.volmit.rift.Rift")
                .contains("Hot reload failures: 1")
                .contains("Active language file:", "en_US.toml")
                .contains("Remote language catalog revision: main")
                .contains("Latest remote language download failure: none")
                .contains("== Rift managed files ==")
                .contains("Classes currently loaded:")
                .contains("debugUploadEnabled: true")
                .doesNotContain("== Memory ==")
                .doesNotContain("== Threads ==")
                .doesNotContain("Deadlocked:")
                .doesNotContain("Privacy:")
                .doesNotContain("user.dir")
                .doesNotContain("sun.java.command")
                .doesNotContain("password")
                .doesNotContain("token=");
    }
}
