package net.createaerospace.orbit;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One satellite in a circular orbit. {@code raan} is the longitude of the ascending node in the inertial frame, which the Earth
 * turns under; {@code phase0} is the angle along the orbit at {@code epoch}.
 */
public record Satellite(UUID id, String name, UUID owner, String ownerName, int tier, int incl, double raan, double phase0, long epoch, int mode,
                        String payload) {
    public static final int STANDBY = 0, BEACON = 1, FLASH = 2;
    public static final int MODES = 3;
    public static final int GEOSTATIONARY_TIER = 3;
    /** Anvil names go to 50; the packet limit below is higher, so a long name can never break the player list. */
    public static final int MAX_NAME = 50;

    /** A plain CAE satellite, without a payload of another mod. */
    public Satellite(UUID id, String name, UUID owner, String ownerName, int tier, int incl, double raan, double phase0, long epoch, int mode) {
        this(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, mode, "");
    }

    public Satellite {
        payload = payload == null ? "" : payload;
        name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        ownerName = ownerName.length() > MAX_NAME ? ownerName.substring(0, MAX_NAME) : ownerName;
    }

    /** A new satellite in geostationary orbit: it hangs over the equator at the given longitude, as a communications satellite does. */
    public static Satellite deployed(String name, UUID owner, String ownerName, double lonDeg, long now) {
        return deployed(UUID.randomUUID(), name, owner, ownerName, lonDeg, now, "");
    }

    /** The same with a given id and the payload type of another mod ({@code ""} for a plain satellite). */
    public static Satellite deployed(UUID id, String name, UUID owner, String ownerName, double lonDeg, long now, String payload) {
        double raan = Math.toRadians(lonDeg) + 2 * Math.PI * now / OrbitMath.DAY_TICKS;
        return new Satellite(id, name, owner, ownerName, GEOSTATIONARY_TIER, 0, raan, 0, now, BEACON, payload);
    }

    public int periodTicks() {
        return OrbitMath.PERIOD_TICKS[tier];
    }

    public int altKm() {
        return OrbitMath.ALT_KM[tier];
    }

    public double phase(double tick) {
        return phase0 + 2 * Math.PI * (tick - epoch) / periodTicks();
    }

    /** Changing the orbit keeps the angle along it, so a new altitude is continuous and a new plane repositions the satellite. */
    public Satellite retuned(int newTier, int newIncl, long now) {
        double u = phase(now);
        return new Satellite(id, name, owner, ownerName, newTier, newIncl, raan, u, now, mode, payload);
    }

    public Satellite withMode(int newMode) {
        return new Satellite(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, newMode, payload);
    }

    public boolean controlledBy(UUID player, boolean op) {
        return op || owner.equals(player);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putUUID("owner", owner);
        t.putString("ownerName", ownerName);
        t.putInt("tier", tier);
        t.putInt("incl", incl);
        t.putDouble("raan", raan);
        t.putDouble("phase0", phase0);
        t.putLong("epoch", epoch);
        t.putInt("mode", mode);
        if (!payload.isEmpty()) t.putString("payload", payload);
        return t;
    }

    public static Satellite load(CompoundTag t) {
        return new Satellite(t.getUUID("id"), t.getString("name"), t.getUUID("owner"), t.getString("ownerName"),
                Math.max(0, Math.min(OrbitMath.ALT_KM.length - 1, t.getInt("tier"))),
                Math.max(0, Math.min(OrbitMath.INCLINATION_DEG.length - 1, t.getInt("incl"))),
                t.getDouble("raan"), t.getDouble("phase0"), t.getLong("epoch"), Math.floorMod(t.getInt("mode"), MODES), t.getString("payload"));
    }

    public void write(FriendlyByteBuf b) {
        b.writeUUID(id).writeUtf(name, 64).writeUUID(owner).writeUtf(ownerName, 64).writeVarInt(tier).writeVarInt(incl)
                .writeDouble(raan).writeDouble(phase0).writeLong(epoch).writeVarInt(mode).writeUtf(payload, 128);
    }

    public static Satellite read(FriendlyByteBuf b) {
        return new Satellite(b.readUUID(), b.readUtf(64), b.readUUID(), b.readUtf(64), b.readVarInt(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readLong(), b.readVarInt(), b.readUtf(128));
    }
}
