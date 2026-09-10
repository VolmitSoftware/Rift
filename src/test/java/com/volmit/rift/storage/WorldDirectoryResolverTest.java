package com.volmit.rift.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class WorldDirectoryResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesLegacyAndModernDimensionLayouts() throws Exception {
        Path primary = temporaryDirectory.resolve("world");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        Path legacy = temporaryDirectory.resolve("legacy");
        Files.createDirectories(legacy);
        Files.createFile(legacy.resolve("level.dat"));
        Path dimension = primary.resolve("dimensions/minecraft/testing");
        Files.createDirectories(dimension.resolve("region"));

        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );

        assertThat(resolver.require("legacy")).isEqualTo(legacy.toAbsolutePath().normalize());
        assertThat(resolver.require("testing")).isEqualTo(dimension.toAbsolutePath().normalize());
        WorldProfile profile = new WorldProfile();
        profile.setName("testing");
        assertThat(resolver.require(profile)).isEqualTo(dimension.toAbsolutePath().normalize());
        profile.setDirectory("world/dimensions/minecraft/testing");
        assertThat(resolver.require(profile)).isEqualTo(dimension.toAbsolutePath().normalize());
        assertThat(resolver.relative(dimension, "testing")).isEqualTo("world/dimensions/minecraft/testing");
        assertThat(resolver.discover()).extracting(WorldDirectoryResolver.DiscoveredWorld::name)
                .contains("world", "legacy", "testing");
    }

    @Test
    void storedDirectoryIsConfinedAndUsedForRestore() throws Exception {
        Path primary = temporaryDirectory.resolve("world");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );
        WorldProfile profile = new WorldProfile();
        profile.setName("testing");
        profile.setDirectory("world/dimensions/custom/testing");

        assertThat(resolver.restoreTarget(profile))
                .isEqualTo(primary.resolve("dimensions/custom/testing").toAbsolutePath().normalize());

        profile.setDirectory("../testing");
        assertThatThrownBy(() -> resolver.restoreTarget(profile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("relative");
    }

    @Test
    void resolvesDimensionsWhenPaperReportsANestedPrimaryWorldFolder() throws Exception {
        Path dimensionRoot = temporaryDirectory.resolve("world/dimensions");
        Path primary = dimensionRoot.resolve("minecraft/overworld");
        Files.createDirectories(primary.resolve("region"));
        Files.createFile(primary.resolve("paper-world.yml"));
        Path testing = dimensionRoot.resolve("minecraft/testing");
        Files.createDirectories(testing.resolve("region"));
        Files.createFile(testing.resolve("paper-world.yml"));

        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );

        assertThat(resolver.require("testing")).isEqualTo(testing.toAbsolutePath().normalize());
        assertThat(resolver.discover()).extracting(WorldDirectoryResolver.DiscoveredWorld::name)
                .containsExactly("testing");
        assertThat(resolver.find("overworld")).isEmpty();
    }

    @Test
    void resolvesPaperLowercaseDimensionForMixedCaseBukkitWorldName() throws Exception {
        Path primary = temporaryDirectory.resolve("world");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        Path testing = primary.resolve("dimensions/minecraft/testing");
        Files.createDirectories(testing.resolve("region"));
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );
        WorldProfile profile = new WorldProfile();
        profile.setName("Testing");
        profile.setDirectory("world/dimensions/minecraft/testing");

        assertThat(resolver.find("Testing")).contains(testing.toAbsolutePath().normalize());
        assertThat(resolver.require(profile)).isEqualTo(testing.toAbsolutePath().normalize());
        assertThat(resolver.relative(testing, "Testing")).isEqualTo("world/dimensions/minecraft/testing");
        assertThat(resolver.isDefinitelyMissing(profile)).isFalse();
        assertThatThrownBy(() -> resolver.relative(testing, "AnotherWorld"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not match");

        Path lowercaseLegacy = temporaryDirectory.resolve("testing");
        Files.createDirectories(lowercaseLegacy);
        Files.createFile(lowercaseLegacy.resolve("level.dat"));
        assertThatThrownBy(() -> resolver.relative(lowercaseLegacy, "Testing"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void resolvesAndDiscoversRiftNamespacedDimensionForMixedCaseLogicalName() throws Exception {
        Path primary = temporaryDirectory.resolve("world");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        Path testing = primary.resolve("dimensions/rift/testing");
        Files.createDirectories(testing.resolve("region"));
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );
        WorldProfile profile = new WorldProfile();
        profile.setName("Testing");
        profile.setDirectory("world/dimensions/rift/testing");

        assertThat(resolver.find("Testing")).contains(testing.toAbsolutePath().normalize());
        assertThat(resolver.require(profile)).isEqualTo(testing.toAbsolutePath().normalize());
        assertThat(resolver.relative(testing, "Testing")).isEqualTo("world/dimensions/rift/testing");
        assertThat(resolver.isRiftDimension(testing, "Testing")).isTrue();
        assertThat(resolver.isDefinitelyMissing(profile)).isFalse();
        assertThat(resolver.discover()).extracting(WorldDirectoryResolver.DiscoveredWorld::name)
                .containsExactly("testing", "world");
    }

    @Test
    void ignoresOtherPluginNamespacesUnlessAProfileStoresTheExactPath() throws Exception {
        Path primary = temporaryDirectory.resolve("world");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        Path custom = primary.resolve("dimensions/anotherplugin/testing");
        Files.createDirectories(custom.resolve("region"));
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );

        assertThat(resolver.find("testing")).isEmpty();
        assertThat(resolver.discover()).extracting(WorldDirectoryResolver.DiscoveredWorld::name)
                .doesNotContain("testing");

        WorldProfile profile = new WorldProfile();
        profile.setName("testing");
        profile.setDirectory("world/dimensions/anotherplugin/testing");
        assertThat(resolver.require(profile)).isEqualTo(custom.toAbsolutePath().normalize());
    }

    @Test
    void distinguishesDeletedStorageFromAnExistingInvalidDirectory() throws Exception {
        Path primary = temporaryDirectory.resolve("world");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        Files.createDirectories(temporaryDirectory.resolve("incomplete"));
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );
        WorldProfile deleted = new WorldProfile();
        deleted.setName("deleted");
        WorldProfile incomplete = new WorldProfile();
        incomplete.setName("incomplete");

        assertThat(resolver.find(deleted)).isEmpty();
        assertThat(resolver.isDefinitelyMissing(deleted)).isTrue();
        assertThat(resolver.find(incomplete)).isEmpty();
        assertThat(resolver.isDefinitelyMissing(incomplete)).isFalse();
    }

    @Test
    void retainsExplicitProfileWhenAlternateSameNameStorageExists() throws Exception {
        Path primary = temporaryDirectory.resolve("world");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        Path alternate = temporaryDirectory.resolve("testing");
        Files.createDirectories(alternate);
        Files.createFile(alternate.resolve("level.dat"));
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(
                temporaryDirectory,
                primary,
                new WorldNamePolicy(temporaryDirectory)
        );
        WorldProfile profile = new WorldProfile();
        profile.setName("testing");
        profile.setDirectory("world/dimensions/custom/testing");

        assertThat(resolver.find(profile)).isEmpty();
        assertThatThrownBy(() -> resolver.isDefinitelyMissing(profile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("another same-name storage entry exists");
    }
}
