package com.volmit.rift.world;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Moves a destination up or down its column so a player arrives standing on a floor instead of inside blocks. */
final class SafeLanding {
    private static final Set<Material> HAZARDS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE);

    private SafeLanding() {
    }

    /** Resolves the landing on the region that owns it, loading its chunk first. Falls back to the request. */
    static CompletableFuture<Location> resolve(Plugin plugin, Location requested) {
        World world = requested.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(requested.clone());
        }
        CompletableFuture<Location> result = new CompletableFuture<>();
        world.getChunkAtAsync(requested).whenComplete((chunk, failure) -> {
            if (failure != null || !FoliaScheduler.runRegion(plugin, requested, () -> result.complete(findOrRequested(requested)))) {
                result.complete(requested.clone());
            }
        });
        return result;
    }

    /** Must run on the thread that owns the requested location. */
    static Location find(Location requested) {
        World world = requested.getWorld();
        if (world == null) {
            return requested.clone();
        }
        int lowest = world.getMinHeight() + 1;
        int highest = world.getMaxHeight() - 2;
        if (highest < lowest) {
            return requested.clone();
        }
        int x = requested.getBlockX();
        int z = requested.getBlockZ();
        int start = Math.clamp(requested.getBlockY(), lowest, highest);
        if (start == requested.getBlockY() && standable(world, x, start, z)) {
            return requested.clone();
        }
        for (int y = start + 1; y <= highest; y++) {
            if (standable(world, x, y, z)) {
                return at(requested, y);
            }
        }
        int surface = world.getHighestBlockYAt(x, z) + 1;
        if (surface >= lowest && surface <= highest && standable(world, x, surface, z)) {
            return at(requested, surface);
        }
        for (int y = start - 1; y >= lowest; y--) {
            if (standable(world, x, y, z)) {
                return at(requested, y);
            }
        }
        return requested.clone();
    }

    private static Location findOrRequested(Location requested) {
        try {
            return find(requested);
        } catch (RuntimeException exception) {
            return requested.clone();
        }
    }

    private static boolean standable(World world, int x, int y, int z) {
        Block floor = world.getBlockAt(x, y - 1, z);
        return floor.isSolid() && !HAZARDS.contains(floor.getType())
                && clear(world.getBlockAt(x, y, z)) && clear(world.getBlockAt(x, y + 1, z));
    }

    private static boolean clear(Block block) {
        return block.isPassable() && !block.isLiquid() && !HAZARDS.contains(block.getType());
    }

    private static Location at(Location requested, int y) {
        Location landing = requested.clone();
        landing.setY(y);
        return landing;
    }
}
