package net.createaerospace.orbit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A warning on the consoles, the map screen and the Control Computer app, for {@link #SHOWN_TICKS} after {@code time}. A launch
 * failure names the rocket type ({@code subject}, an id the client turns into a name), whose it was and how many payloads it lost;
 * a reentry names the satellite and where it comes down.
 */
public record Alert(int kind, String subject, String owner, int count, int x, int z, long time) {
    public static final int LAUNCH_FAILURE = 0, REENTRY = 1;
    public static final int SHOWN_TICKS = 6000;
    static final int KEPT = 8;

    public boolean shown(double tick) {
        return tick >= time && tick - time < SHOWN_TICKS;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("kind", kind);
        t.putString("subject", subject);
        t.putString("owner", owner);
        t.putInt("count", count);
        t.putInt("x", x);
        t.putInt("z", z);
        t.putLong("time", time);
        return t;
    }

    public static Alert load(CompoundTag t) {
        return new Alert(t.getInt("kind"), t.getString("subject"), t.getString("owner"), t.getInt("count"), t.getInt("x"), t.getInt("z"), t.getLong("time"));
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(kind).writeUtf(subject, 128).writeUtf(owner, 64).writeVarInt(count).writeVarInt(x).writeVarInt(z).writeLong(time);
    }

    public static Alert read(FriendlyByteBuf b) {
        return new Alert(b.readVarInt(), b.readUtf(128), b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong());
    }
}
