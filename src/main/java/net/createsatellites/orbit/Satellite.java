package net.createsatellites.orbit;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One satellite in a circular orbit. {@code raan} is the longitude of the ascending node in the inertial frame, which the Earth
 * turns under; {@code phase0} is the angle along the orbit at {@code epoch}.
 */
public record Satellite(UUID id, String name, UUID owner, String ownerName, int tier, int incl, double raan, double phase0, long epoch, int mode) {
    public static final int STANDBY = 0, BEACON = 1, FLASH = 2;
    public static final int MODES = 3;
    /** Anvil names go to 50; the packet limit below is higher, so a long name can never break the player list. */
    public static final int MAX_NAME = 50;

    public Satellite {
        name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        ownerName = ownerName.length() > MAX_NAME ? ownerName.substring(0, MAX_NAME) : ownerName;
    }

    /** A new satellite released over the given longitude at the equator, climbing north-east. */
    public static Satellite deployed(String name, UUID owner, String ownerName, double lonDeg, long now) {
        double raan = Math.toRadians(lonDeg) + 2 * Math.PI * now / OrbitMath.DAY_TICKS;
        return new Satellite(UUID.randomUUID(), name, owner, ownerName, 0, 2, raan, 0, now, BEACON);
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
        return new Satellite(id, name, owner, ownerName, newTier, newIncl, raan, u, now, mode);
    }

    public Satellite withMode(int newMode) {
        return new Satellite(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, newMode);
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
        return t;
    }

    public static Satellite load(CompoundTag t) {
        return new Satellite(t.getUUID("id"), t.getString("name"), t.getUUID("owner"), t.getString("ownerName"),
                Math.max(0, Math.min(OrbitMath.ALT_KM.length - 1, t.getInt("tier"))),
                Math.max(0, Math.min(OrbitMath.INCLINATION_DEG.length - 1, t.getInt("incl"))),
                t.getDouble("raan"), t.getDouble("phase0"), t.getLong("epoch"), Math.floorMod(t.getInt("mode"), MODES));
    }

    public void write(FriendlyByteBuf b) {
        b.writeUUID(id).writeUtf(name, 64).writeUUID(owner).writeUtf(ownerName, 64).writeVarInt(tier).writeVarInt(incl)
                .writeDouble(raan).writeDouble(phase0).writeLong(epoch).writeVarInt(mode);
    }

    public static Satellite read(FriendlyByteBuf b) {
        return new Satellite(b.readUUID(), b.readUtf(64), b.readUUID(), b.readUtf(64), b.readVarInt(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readLong(), b.readVarInt());
    }
}
