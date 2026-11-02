package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

final class RiftWorldIdentityTest {
    @Test
    void mapsRiftRuntimeIdentityBackToItsLogicalProfileName() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("rift_testing");
        when(world.getKey()).thenReturn(RiftWorldIdentity.key("Testing"));
        WorldProfile profile = new WorldProfile();
        profile.setName("Testing");
        WorldProfileStore profiles = mock(WorldProfileStore.class);
        when(profiles.find("testing")).thenReturn(Optional.of(profile));

        assertThat(RiftWorldIdentity.key("Testing").toString()).isEqualTo("rift:testing");
        assertThat(RiftWorldIdentity.isRiftWorld(world)).isTrue();
        assertThat(RiftWorldIdentity.profile(world, profiles)).contains(profile);
        assertThat(RiftWorldIdentity.logicalName(world, profiles)).isEqualTo("Testing");
    }

    @Test
    void leavesNonRiftBukkitNamesUnchanged() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("Testing");
        when(world.getKey()).thenReturn(NamespacedKey.minecraft("testing"));
        WorldProfileStore profiles = mock(WorldProfileStore.class);
        when(profiles.find("testing")).thenReturn(Optional.empty());

        assertThat(RiftWorldIdentity.isRiftWorld(world)).isFalse();
        assertThat(RiftWorldIdentity.logicalName(world, profiles)).isEqualTo("testing");
    }

    @Test
    void managedNameNeverResolvesToAVanillaWorldWithTheSameShortName() {
        WorldProfile profile = profile("foo", "world/dimensions/rift/foo");
        WorldProfileStore profiles = mock(WorldProfileStore.class);
        when(profiles.find("foo")).thenReturn(Optional.of(profile));
        World vanilla = world(NamespacedKey.minecraft("foo"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(NamespacedKey.minecraft("foo"))).thenReturn(vanilla);

            assertThat(RiftWorldIdentity.findLoaded("foo", profiles)).isNull();
            assertThat(RiftWorldIdentity.findAnyLoaded("foo")).isSameAs(vanilla);
        }
        assertThat(RiftWorldIdentity.profile(vanilla, profiles)).isEmpty();
        assertThat(RiftWorldIdentity.logicalName(vanilla, profiles)).isEqualTo("minecraft:foo");
    }

    @Test
    void recordedDirectoryDecidesWhichNamespaceAProfileOwns() {
        WorldProfile imported = profile("foo", "world/dimensions/minecraft/foo");

        assertThat(RiftWorldIdentity.recordedKey(imported)).contains(NamespacedKey.minecraft("foo"));
        assertThat(RiftWorldIdentity.owns(imported, NamespacedKey.minecraft("foo"))).isTrue();
        assertThat(RiftWorldIdentity.owns(imported, RiftWorldIdentity.key("foo"))).isFalse();
        assertThat(RiftWorldIdentity.recordedKey(profile("bar", ""))).isEmpty();
    }

    @Test
    void vanillaDimensionsAreAlwaysPrimary() {
        World nether = world(NamespacedKey.minecraft("the_nether"));
        World end = world(NamespacedKey.minecraft("the_end"));
        World riftEnd = world(RiftWorldIdentity.key("the_end"));
        World overworld = world(NamespacedKey.minecraft("overworld"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(overworld));

            assertThat(RiftWorldIdentity.isPrimary(nether)).isTrue();
            assertThat(RiftWorldIdentity.isPrimary(end)).isTrue();
            assertThat(RiftWorldIdentity.isPrimary(overworld)).isTrue();
            assertThat(RiftWorldIdentity.isPrimary(riftEnd)).isFalse();
        }
    }

    private static WorldProfile profile(String name, String directory) {
        WorldProfile profile = new WorldProfile();
        profile.setName(name);
        profile.setDirectory(directory);
        return profile;
    }

    private static World world(NamespacedKey key) {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(key);
        return world;
    }
}
