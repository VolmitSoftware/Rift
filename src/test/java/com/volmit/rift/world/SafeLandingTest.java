package com.volmit.rift.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class SafeLandingTest {
    @Test
    void keepsADestinationThatIsAlreadySafe() {
        Column column = new Column().fill(0, 63, Material.STONE);
        Location requested = new Location(column.world, 0.5D, 64.0D, 0.5D, 90.0F, 10.0F);

        assertThat(SafeLanding.find(requested)).isEqualTo(requested);
    }

    @Test
    void climbsOutOfSolidGround() {
        Column column = new Column().fill(0, 70, Material.STONE);

        Location landing = SafeLanding.find(new Location(column.world, 0.5D, 64.0D, 0.5D, 90.0F, 10.0F));

        assertThat(landing.getY()).isEqualTo(71.0D);
        assertThat(landing.getYaw()).isEqualTo(90.0F);
    }

    @Test
    void dropsToTheFloorBelowAMidAirSpawn() {
        Column column = new Column().fill(0, 40, Material.STONE);

        assertThat(SafeLanding.find(new Location(column.world, 0.5D, 100.0D, 0.5D)).getY()).isEqualTo(41.0D);
    }

    @Test
    void neverLandsOnLavaOrInsideWater() {
        Column column = new Column().fill(0, 59, Material.STONE).fill(60, 60, Material.LAVA)
                .fill(61, 62, Material.WATER).fill(63, 66, Material.STONE);

        assertThat(SafeLanding.find(new Location(column.world, 0.5D, 61.0D, 0.5D)).getY()).isEqualTo(67.0D);
    }

    @Test
    void leavesAnEmptyColumnAlone() {
        Column column = new Column();
        Location requested = new Location(column.world, 0.5D, 64.0D, 0.5D);

        assertThat(SafeLanding.find(requested)).isEqualTo(requested);
    }

    private static final class Column {
        private final World world = mock(World.class);
        private final Map<Integer, Material> blocks = new HashMap<>();

        private Column() {
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.getHighestBlockYAt(anyInt(), anyInt())).thenAnswer(invocation -> blocks.keySet().stream()
                    .mapToInt(Integer::intValue).max().orElse(-64));
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> block(invocation.getArgument(1)));
        }

        private Column fill(int from, int to, Material material) {
            for (int y = from; y <= to; y++) {
                blocks.put(y, material);
            }
            return this;
        }

        private Block block(int y) {
            Material material = blocks.getOrDefault(y, Material.AIR);
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(material);
            when(block.isLiquid()).thenReturn(material == Material.WATER || material == Material.LAVA);
            boolean solid = material == Material.STONE;
            when(block.isSolid()).thenReturn(solid);
            when(block.isPassable()).thenReturn(!solid);
            return block;
        }
    }
}
