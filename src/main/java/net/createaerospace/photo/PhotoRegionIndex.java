package net.createaerospace.photo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Reads only the location table of existing region files. Vanilla chunk IO handles the actual data. */
final class PhotoRegionIndex {
    private final Map<Long, BitSet> regions = new HashMap<>();

    static PhotoRegionIndex read(Path directory, BlockPos center) {
        var index = new PhotoRegionIndex();
        int x0 = center.getX() - OrbitalPhoto.RANGE / 2, z0 = center.getZ() - OrbitalPhoto.RANGE / 2;
        int x1 = x0 + OrbitalPhoto.RANGE - 1, z1 = z0 + OrbitalPhoto.RANGE - 1;
        for (int z = z0 >> 9; z <= z1 >> 9; z++) {
            for (int x = x0 >> 9; x <= x1 >> 9; x++) {
                Path path = directory.resolve("r." + x + "." + z + ".mca");
                try (var file = FileChannel.open(path, StandardOpenOption.READ)) {
                    ByteBuffer addresses = ByteBuffer.allocate(4096);
                    while (addresses.hasRemaining() && file.read(addresses) > 0) {}
                    addresses.flip();
                    var chunks = new BitSet(1024);
                    for (int i = 0; addresses.remaining() >= Integer.BYTES; i++) {
                        if (addresses.getInt() != 0) chunks.set(i);
                    }
                    if (!chunks.isEmpty()) index.regions.put(ChunkPos.asLong(x, z), chunks);
                } catch (IOException e) {
                    // No saved or readable region. Loaded chunks are still eligible.
                }
            }
        }
        return index;
    }

    boolean has(int x, int z) {
        BitSet chunks = regions.get(ChunkPos.asLong(x >> 5, z >> 5));
        return chunks != null && chunks.get((z & 31) * 32 + (x & 31));
    }
}
