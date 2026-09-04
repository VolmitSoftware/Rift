package com.volmit.rift.world;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

final class VoidChunkGeneratorTest {
    @Test
    void generatesOnlyTheSpawnPlatformInTheOriginChunk() {
        VoidChunkGenerator generator = VoidChunkGenerator.instance();
        WorldInfo worldInfo = mock(WorldInfo.class);
        ChunkGenerator.ChunkData origin = mock(ChunkGenerator.ChunkData.class);
        ChunkGenerator.ChunkData distant = mock(ChunkGenerator.ChunkData.class);

        generator.generateNoise(worldInfo, new Random(1L), 0, 0, origin);
        generator.generateNoise(worldInfo, new Random(1L), 1, 0, distant);

        verify(origin, times(25)).setBlock(anyInt(), eq(63), anyInt(), eq(Material.BEDROCK));
        verify(distant, never()).setBlock(anyInt(), anyInt(), anyInt(), eq(Material.BEDROCK));
        assertThat(generator.getDefaultBiomeProvider(worldInfo).getBiome(worldInfo, 0, 64, 0))
                .isEqualTo(Biome.THE_VOID);
        assertThat(generator.shouldGenerateSurface()).isFalse();
        assertThat(generator.shouldGenerateStructures()).isFalse();
        assertThat(generator.isParallelCapable()).isTrue();
    }
}
