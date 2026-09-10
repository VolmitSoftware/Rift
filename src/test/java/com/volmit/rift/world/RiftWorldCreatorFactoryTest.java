package com.volmit.rift.world;

import com.volmit.rift.storage.WorldDirectoryResolver;
import org.bukkit.WorldCreator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class RiftWorldCreatorFactoryTest {
    @Test
    void retainsStandaloneCreationOnPlatformsWithoutNamespacedStorage() throws Exception {
        PlatformCapabilities capabilities = mock(PlatformCapabilities.class);
        WorldDirectoryResolver directories = mock(WorldDirectoryResolver.class);
        RiftWorldCreatorFactory factory = new RiftWorldCreatorFactory(capabilities, directories);

        WorldCreator creator = factory.forNewWorld("Testing");

        assertThat(creator.name()).isEqualTo("Testing");
    }

    @Test
    void refusesRiftDimensionOnPlatformsWithoutPersistentNamespacedStorage() throws Exception {
        PlatformCapabilities capabilities = mock(PlatformCapabilities.class);
        WorldDirectoryResolver directories = mock(WorldDirectoryResolver.class);
        Path directory = Path.of("world/dimensions/rift/testing").toAbsolutePath().normalize();
        when(directories.isRiftDimension(directory, "Testing")).thenReturn(true);
        RiftWorldCreatorFactory factory = new RiftWorldCreatorFactory(capabilities, directories);

        assertThatThrownBy(() -> factory.forStoredWorld("Testing", directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Paper 26.1 or newer");
    }
}
