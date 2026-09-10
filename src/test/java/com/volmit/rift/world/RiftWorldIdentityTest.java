package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        when(profiles.find("Testing")).thenReturn(Optional.empty());

        assertThat(RiftWorldIdentity.isRiftWorld(world)).isFalse();
        assertThat(RiftWorldIdentity.logicalName(world, profiles)).isEqualTo("Testing");
    }
}
