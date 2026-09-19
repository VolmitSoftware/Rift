package com.volmit.rift.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class VoidChunkGeneratorTest {
    @Test
    void disablesVanillaTerrainPassesAndSupportsParallelGeneration() {
        VoidChunkGenerator generator = VoidChunkGenerator.instance();

        assertThat(generator.shouldGenerateSurface()).isFalse();
        assertThat(generator.shouldGenerateStructures()).isFalse();
        assertThat(generator.isParallelCapable()).isTrue();
    }
}
