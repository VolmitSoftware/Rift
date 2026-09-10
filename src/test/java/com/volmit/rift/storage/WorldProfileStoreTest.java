package com.volmit.rift.storage;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class WorldProfileStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void retiresProfileIntoInactiveArchive() throws Exception {
        Path worldContainer = temporaryDirectory.resolve("server");
        Path profileDirectory = temporaryDirectory.resolve("plugins/Rift/worlds");
        WorldProfileStore store = store(worldContainer, profileDirectory);
        assertThat(store.loadAll()).isTrue();
        store.save(profile("testing100"));

        Optional<File> retired = store.retire("testing100");

        assertThat(retired).isPresent();
        assertThat(retired.orElseThrow()).isFile();
        assertThat(retired.orElseThrow().toPath().getParent())
                .isEqualTo(profileDirectory.resolve("retired"));
        assertThat(retired.orElseThrow().getName()).endsWith("-testing100.toml");
        assertThat(profileDirectory.resolve("testing100.toml")).doesNotExist();
        assertThat(store.find("testing100")).isEmpty();
    }

    @Test
    void retiresMemoryStateWhenProfileFileWasAlreadyRemoved() throws Exception {
        Path worldContainer = temporaryDirectory.resolve("server");
        Path profileDirectory = temporaryDirectory.resolve("plugins/Rift/worlds");
        WorldProfileStore store = store(worldContainer, profileDirectory);
        assertThat(store.loadAll()).isTrue();
        store.save(profile("testing100"));
        Path profileFile = profileDirectory.resolve("testing100.toml").toAbsolutePath().normalize();
        assertThat(profileFile.startsWith(temporaryDirectory.toAbsolutePath().normalize())).isTrue();
        Files.delete(profileFile);

        assertThat(store.retire("testing100")).isEmpty();
        assertThat(store.find("testing100")).isEmpty();
    }

    @Test
    void failedRetirementPreservesActiveProfileAndFile() throws Exception {
        Path worldContainer = temporaryDirectory.resolve("server");
        Path profileDirectory = temporaryDirectory.resolve("plugins/Rift/worlds");
        WorldProfileStore store = store(worldContainer, profileDirectory);
        assertThat(store.loadAll()).isTrue();
        store.save(profile("testing100"));
        Files.createFile(profileDirectory.resolve("retired"));

        assertThatThrownBy(() -> store.retire("testing100")).isInstanceOf(IOException.class);
        assertThat(profileDirectory.resolve("testing100.toml")).isRegularFile();
        assertThat(store.find("testing100")).isPresent();
    }

    @Test
    void rejectsNonDirectoryProfileStorage() throws Exception {
        Path worldContainer = temporaryDirectory.resolve("server");
        Path profileDirectory = temporaryDirectory.resolve("profile-file");
        Files.createFile(profileDirectory);
        WorldProfileStore store = store(worldContainer, profileDirectory);

        assertThat(store.loadAll()).isFalse();
    }

    @Test
    void failedReloadPreservesPreviouslyLoadedProfiles() throws Exception {
        Path worldContainer = temporaryDirectory.resolve("server");
        Path profileDirectory = temporaryDirectory.resolve("plugins/Rift/worlds");
        WorldProfileStore store = store(worldContainer, profileDirectory);
        assertThat(store.loadAll()).isTrue();
        store.save(profile("testing100"));
        Files.writeString(profileDirectory.resolve("broken.toml"), "not valid toml = [");

        assertThat(store.loadAll()).isFalse();
        assertThat(store.find("testing100")).isPresent();
    }

    @Test
    void storesPaperLowercaseDirectoryForMixedCaseWorldName() throws Exception {
        Path worldContainer = temporaryDirectory.resolve("server");
        Path profileDirectory = temporaryDirectory.resolve("plugins/Rift/worlds");
        WorldProfileStore store = store(worldContainer, profileDirectory);
        assertThat(store.loadAll()).isTrue();
        WorldProfile profile = profile("Testing");
        profile.setDirectory("world/dimensions/minecraft/testing");

        store.save(profile);

        assertThat(store.find("Testing")).isPresent();
        assertThat(store.find("Testing").orElseThrow().getDirectory())
                .isEqualTo("world/dimensions/minecraft/testing");

        WorldProfileStore reloaded = store(worldContainer, profileDirectory);
        assertThat(reloaded.loadAll()).isTrue();
        assertThat(reloaded.find("Testing")).isPresent();
        assertThat(reloaded.find("Testing").orElseThrow().getDirectory())
                .isEqualTo("world/dimensions/minecraft/testing");
    }

    private WorldProfileStore store(Path worldContainer, Path profileDirectory) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("WorldProfileStoreTest"));
        return new WorldProfileStore(
                plugin,
                profileDirectory.toFile(),
                new WorldNamePolicy(worldContainer)
        );
    }

    private static WorldProfile profile(String name) {
        WorldProfile profile = new WorldProfile();
        profile.setName(name);
        return profile;
    }
}
