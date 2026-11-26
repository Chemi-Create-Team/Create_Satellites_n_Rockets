package net.createaerospace.photo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** An orthographic surface raster assembled from current chunks and saved chunk snapshots. */
public final class OrbitalPhoto {
    public static final int RANGE = 4096;
    public static final int SIZE = 256;
    public static final int BYTES = SIZE * SIZE * 3;
    public static final int OVERVIEW_STEP = 4;
    private static final int NO_DATA = 0x172537;
    private static final int MISSING_HEIGHT = Integer.MIN_VALUE;

    private OrbitalPhoto() {}

    public record Tile(int step, int x, int z) {
        public boolean valid() {
            if (step != 1 && step != 2 && step != OVERVIEW_STEP) return false;
            int count = RANGE / span();
            return x >= 0 && z >= 0 && x < count && z < count;
        }

        public int span() { return SIZE * step; }
        public int left(BlockPos center) { return center.getX() - RANGE / 2 + x * span(); }
        public int top(BlockPos center) { return center.getZ() - RANGE / 2 + z * span(); }
    }

    /** Instances are written by one task per chunk. Each chunk owns disjoint pixels. */
    public static final class Raster {
        private final int left, top, step;
        private final int[] colors = new int[SIZE * SIZE];
        private final int[] heights = new int[SIZE * SIZE];

        public Raster(int left, int top, int step) {
            this.left = left;
            this.top = top;
            this.step = step;
            java.util.Arrays.fill(heights, MISSING_HEIGHT);
        }

        public void sample(LevelChunk chunk) {
            sampleChunk(chunk.getPos().x, chunk.getPos().z,
                    (x, z) -> chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), chunk::getBlockState);
        }

        public void sampleSaved(int chunkX, int chunkZ, CompoundTag data, int minHeight, int worldHeight) {
            var packed = data.getCompound("Heightmaps").getLongArray("WORLD_SURFACE");
            int bits = Mth.ceillog2(worldHeight + 1);
            int perLong = 64 / bits;
            if (packed.length != Math.ceilDiv(256, perLong)) return;
            var heightMap = new SimpleBitStorage(bits, 256, packed);
            Map<Integer, CompoundTag> sections = new HashMap<>();
            for (var raw : data.getList("sections", Tag.TAG_COMPOUND)) {
                var section = (CompoundTag) raw;
                if (section.contains("block_states", Tag.TAG_COMPOUND)) {
                    sections.put((int) section.getByte("Y"), section.getCompound("block_states"));
                }
            }
            Map<Integer, PalettedContainer<BlockState>> blocks = new HashMap<>();
            sampleChunk(chunkX, chunkZ, (x, z) -> minHeight + heightMap.get((z & 15) * 16 + (x & 15)) - 1, pos -> {
                int section = pos.getY() >> 4;
                var palette = blocks.computeIfAbsent(section, key -> {
                    var nbt = sections.get(key);
                    return nbt == null ? SavedStates.EMPTY : SavedStates.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElse(SavedStates.EMPTY);
                });
                return palette.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
            });
        }

        private void sampleChunk(int chunkX, int chunkZ, IntBinaryOperator height, java.util.function.Function<BlockPos, BlockState> state) {
            int x0 = chunkX << 4, z0 = chunkZ << 4;
            int firstCol = Math.max(0, Math.ceilDiv(x0 - left, step)), lastCol = Math.min(SIZE, Math.ceilDiv(x0 + 16 - left, step));
            int firstRow = Math.max(0, Math.ceilDiv(z0 - top, step)), lastRow = Math.min(SIZE, Math.ceilDiv(z0 + 16 - top, step));
            var pos = new BlockPos.MutableBlockPos();
            for (int row = firstRow; row < lastRow; row++) {
                int z = top + row * step;
                for (int col = firstCol; col < lastCol; col++) {
                    int x = left + col * step;
                    int y = height.applyAsInt(x, z);
                    pos.set(x, y, z);
                    MapColor color = state.apply(pos).getMapColor(EmptyBlockGetter.INSTANCE, pos);
                    if (color == MapColor.NONE) continue;
                    int offset = row * SIZE + col;
                    heights[offset] = y;
                    colors[offset] = color.col;
                }
            }
        }

        public byte[] pixels() {
            byte[] rgb = new byte[BYTES];
            double relief = 1 / Math.sqrt(step);
            for (int i = 0; i < colors.length; i++) {
                int color = NO_DATA;
                int y = heights[i];
                if (y != MISSING_HEIGHT) {
                    int west = i % SIZE > 0 && heights[i - 1] != MISSING_HEIGHT ? heights[i - 1] : y;
                    int north = i >= SIZE && heights[i - SIZE] != MISSING_HEIGHT ? heights[i - SIZE] : y;
                    double light = Math.clamp(1 + ((y - west) * .025 + (y - north) * .035) * relief, .67, 1.23);
                    color = shade(colors[i], light);
                }
                rgb[3 * i] = (byte) (color >> 16);
                rgb[3 * i + 1] = (byte) (color >> 8);
                rgb[3 * i + 2] = (byte) color;
            }
            return rgb;
        }
    }

    private static int shade(int rgb, double light) {
        return component(rgb >> 16, light) << 16 | component(rgb >> 8, light) << 8 | component(rgb, light);
    }

    private static int component(int value, double light) {
        return Math.min(255, (int) ((value & 255) * light));
    }

    private static final class SavedStates {
        static final PalettedContainer<BlockState> EMPTY = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        static final com.mojang.serialization.Codec<PalettedContainer<BlockState>> CODEC = PalettedContainer.codecRW(
                Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    }
}
