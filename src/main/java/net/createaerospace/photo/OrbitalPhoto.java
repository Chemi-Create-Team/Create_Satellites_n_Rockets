package net.createaerospace.photo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Orthographic RGB image of the world's current surface. Sampling never requests an unloaded chunk. */
public final class OrbitalPhoto {
    public static final int SIZE = 64;
    public static final int BLOCKS_PER_PIXEL = 2;
    public static final int BYTES = SIZE * SIZE * 3;
    private static final int NO_DATA = 0x172537;

    private OrbitalPhoto() {}

    public static byte[] capture(ServerLevel level, BlockPos center) {
        byte[] pixels = new byte[BYTES];
        var pos = new BlockPos.MutableBlockPos();
        int left = center.getX() - SIZE * BLOCKS_PER_PIXEL / 2;
        int top = center.getZ() - SIZE * BLOCKS_PER_PIXEL / 2;
        for (int row = 0; row < SIZE; row++) {
            int z = top + row * BLOCKS_PER_PIXEL;
            int previous = Integer.MIN_VALUE;
            for (int col = 0; col < SIZE; col++) {
                int x = left + col * BLOCKS_PER_PIXEL;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                int rgb = NO_DATA;
                if (chunk != null) {
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                    pos.set(x, y, z);
                    MapColor color = chunk.getBlockState(pos).getMapColor(level, pos);
                    if (color != MapColor.NONE) {
                        double light = previous == Integer.MIN_VALUE ? 1 : Math.clamp(1 + (y - previous) * .055, .63, 1.18);
                        rgb = shade(color.col, light);
                    }
                    previous = y;
                } else {
                    previous = Integer.MIN_VALUE;
                }
                int pixel = (row * SIZE + col) * 3;
                pixels[pixel] = (byte) (rgb >> 16);
                pixels[pixel + 1] = (byte) (rgb >> 8);
                pixels[pixel + 2] = (byte) rgb;
            }
        }
        return pixels;
    }

    private static int shade(int rgb, double light) {
        return component(rgb >> 16, light) << 16 | component(rgb >> 8, light) << 8 | component(rgb, light);
    }

    private static int component(int value, double light) {
        return Math.min(255, (int) ((value & 255) * light));
    }
}
