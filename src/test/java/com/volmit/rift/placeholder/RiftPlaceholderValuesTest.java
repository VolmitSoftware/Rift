package com.volmit.rift.placeholder;

import com.volmit.rift.world.WorldSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftPlaceholderValuesTest {
    @Test
    void reportsGlobalCountsWithoutPlayerContext() {
        RiftPlaceholderValues values = values();

        assertThat(values.resolve(null, "version")).isEqualTo("test-version");
        assertThat(values.resolve(null, "worlds_known")).isEqualTo("3");
        assertThat(values.resolve(null, "worlds_loaded")).isEqualTo("1");
        assertThat(values.resolve(null, "worlds_managed")).isEqualTo("2");
        assertThat(values.resolve(null, "worlds_missing")).isEqualTo("1");
        assertThat(values.resolve(null, "operations_active")).isEqualTo("2");
    }

    @Test
    void supportsCaseInsensitiveExplicitNamesWithUnderscores() {
        RiftPlaceholderValues values = values();

        assertThat(values.resolve(null, "world_name_SKY_ISLAND")).isEqualTo("Sky_Island");
        assertThat(values.resolve(null, "world_loaded_sky_island")).isEqualTo("true");
        assertThat(values.resolve(null, "world_managed_sky_island")).isEqualTo("true");
        assertThat(values.resolve(null, "world_protected_sky_island")).isEqualTo("true");
        assertThat(values.resolve(null, "world_autoload_sky_island")).isEqualTo("true");
        assertThat(values.resolve(null, "world_generator_sky_island")).isEqualTo("void");
        assertThat(values.resolve(null, "world_environment_sky_island")).isEqualTo("NORMAL");
        assertThat(values.resolve(null, "world_busy_sky_island")).isEqualTo("true");
        assertThat(values.resolve(null, "world_generator_missing")).isEqualTo("vanilla");
        assertThat(values.resolve(null, "world_generator_discovered")).isEmpty();
    }

    @Test
    void tracksCurrentWorldOnlyWhilePlayerIsPresent() {
        RiftPlaceholderValues values = values();
        UUID player = UUID.randomUUID();
        assertThat(values.resolve(player, "world_name")).isEmpty();
        assertThat(values.resolve(null, "world_name")).isEmpty();

        values.playerWorld(player, "Sky_Island");
        assertThat(values.resolve(player, "world_name")).isEqualTo("Sky_Island");
        values.playerWorld(player, "Missing");
        assertThat(values.resolve(player, "world_loaded")).isEqualTo("false");
        values.removePlayer(player);
        assertThat(values.resolve(player, "world_managed")).isEmpty();
    }

    @Test
    void distinguishesUnknownParametersFromUnavailableWorlds() {
        RiftPlaceholderValues values = values();

        assertThat(values.resolve(null, "world_loaded_unknown")).isEmpty();
        assertThat(values.resolve(null, "world_loaded_")).isNull();
        assertThat(values.resolve(null, "world_unknown")).isNull();
        assertThat(values.resolve(null, "world")).isNull();
        assertThat(values.resolve(null, null)).isNull();
    }

    @Test
    void replacesSnapshotsAndClearsRemovedWorlds() {
        RiftPlaceholderValues values = values();
        UUID player = UUID.randomUUID();
        values.playerWorld(player, "Sky_Island");
        List<WorldSnapshot> source = new ArrayList<>();
        source.add(new WorldSnapshot("Only", true, false, true, false, false, "NORMAL", "", 1));
        values.install(source, Set.of(), 0);
        source.clear();

        assertThat(values.resolve(null, "worlds_known")).isEqualTo("1");
        assertThat(values.resolve(player, "world_name")).isEmpty();
        assertThat(values.resolve(null, "world_loaded_only")).isEqualTo("true");
        values.clear();
        assertThat(values.resolve(null, "worlds_known")).isEqualTo("0");
        assertThat(values.resolve(null, "operations_active")).isEqualTo("0");
    }

    @Test
    void requestsCanReadPublishedDataOffThread() {
        RiftPlaceholderValues values = values();
        UUID player = UUID.randomUUID();
        values.playerWorld(player, "Sky_Island");

        String result = CompletableFuture.supplyAsync(() -> values.resolve(player, "world_generator")).join();

        assertThat(result).isEqualTo("void");
    }

    private static RiftPlaceholderValues values() {
        RiftPlaceholderValues values = new RiftPlaceholderValues("test-version");
        values.install(List.of(
                new WorldSnapshot("Sky_Island", true, true, true, true, true, "NORMAL", "void", 1),
                new WorldSnapshot("Missing", false, true, false, false, false, "NETHER", "", 1),
                new WorldSnapshot("Discovered", false, false, true, false, false, "unknown", "", 1)
        ), Set.of("sky_island"), 2);
        return values;
    }
}
