package com.volmit.rift.world;

import com.volmit.rift.storage.WorldDirectoryResolver;
import org.bukkit.WorldCreator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class RiftWorldCreatorFactoryTest {
    @Test
    void createsNewWorldsInTheRiftNamespace() throws Exception {
        WorldDirectoryResolver directories = mock(WorldDirectoryResolver.class);
        RiftWorldCreatorFactory factory = new RiftWorldCreatorFactory(directories);

        WorldCreator creator = factory.forNewWorld("Testing");

        assertThat(creator.key()).isEqualTo(RiftWorldIdentity.key("Testing"));
    }

    @Test
    void restoresAWorldWithItsStoredPaperKey() throws Exception {
        WorldDirectoryResolver directories = mock(WorldDirectoryResolver.class);
        Path directory = Path.of("world/dimensions/rift/testing").toAbsolutePath().normalize();
        when(directories.worldKey(directory, "Testing")).thenReturn(RiftWorldIdentity.key("Testing"));
        RiftWorldCreatorFactory factory = new RiftWorldCreatorFactory(directories);

        assertThat(factory.forStoredWorld("Testing", directory).key())
                .isEqualTo(RiftWorldIdentity.key("Testing"));
    }
}
