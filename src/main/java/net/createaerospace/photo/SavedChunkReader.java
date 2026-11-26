package net.createaerospace.photo;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

/**
 * Reads saved chunks for satellite photos straight from the region files, on one low-priority thread of its own. The server's chunk
 * IO worker, which also loads and saves the chunks players walk into, never sees these reads, so a photo of 65,536 chunks cannot
 * hold up anybody's chunk loading. The files are only read. A chunk with changes the server has not written yet shows as it was
 * last saved; a chunk kept in an external {@code .mcc} file (over 1 MiB) is left out.
 */
public final class SavedChunkReader {
    private static final int SECTOR = 4096;
    /** Far more than any normal chunk; a damaged file cannot make the reader build an endless tag. */
    private static final long MAX_NBT_BYTES = 16L << 20;

    /** Every photo shares this one thread, so all of them together use one core at most. */
    static final ExecutorService THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "CAE satellite photo reader");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private SavedChunkReader() {}

    /** The saved chunk, or null when the region has none (or it cannot be read). */
    public static CompoundTag read(Path regions, int chunkX, int chunkZ) throws IOException {
        Path file = regions.resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer location = ByteBuffer.allocate(4);
            readFully(channel, location, 4L * ((chunkX & 31) + (chunkZ & 31) * 32));
            if (location.hasRemaining()) return null;
            int entry = location.flip().getInt(), sector = entry >>> 8, sectors = entry & 255;
            if (sector < 2 || sectors == 0) return null;
            ByteBuffer data = ByteBuffer.allocate(sectors * SECTOR);
            readFully(channel, data, (long) sector * SECTOR);
            data.flip();
            if (data.remaining() < 5) return null;
            int length = data.getInt();
            byte version = data.get();
            // the length counts the version byte; the external flag (128) means the chunk lives in a .mcc file
            if ((version & 128) != 0 || length <= 1 || length - 1 > data.remaining()) return null;
            RegionFileVersion compression = RegionFileVersion.fromId(version);
            if (compression == null) return null;
            try (var in = new DataInputStream(compression.wrap(new ByteArrayInputStream(data.array(), data.position(), length - 1)))) {
                return NbtIo.read(in, NbtAccounter.create(MAX_NBT_BYTES));
            }
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) return;
            position += read;
        }
    }
}
