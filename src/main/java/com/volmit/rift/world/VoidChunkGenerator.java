package com.volmit.rift.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public final class VoidChunkGenerator extends ChunkGenerator {
    private static final VoidChunkGenerator INSTANCE = new VoidChunkGenerator();
    private static final BiomeProvider VOID_BIOMES = new VoidBiomeProvider();

    private VoidChunkGenerator() {
    }

    public static VoidChunkGenerator instance() {
        return INSTANCE;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        if (chunkX != 0 || chunkZ != 0) {
            return;
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                chunkData.setBlock(x, 63, z, Material.BEDROCK);
            }
        }
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 2.5D, 64.0D, 2.5D);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return VOID_BIOMES;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    private static final class VoidBiomeProvider extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.THE_VOID);
        }
    }
}
