package net.createaerospace.orbit;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One satellite in a circular orbit. {@code raan} is the longitude of the ascending node in the inertial frame, which the Earth
 * turns under; {@code phase0} is the angle along the orbit at {@code epoch}.
 * <p>
 * An {@code abandoned} satellite is debris: nobody but an operator controls it. From {@code reentryStart} ({@link #NO_REENTRY} for
 * never; it may lie in the future for an abandoned satellite whose orbit decays) it leaves its orbit and comes down towards the block
 * ({@code targetX}, {@code targetZ}), burning up as a fireball over it (see {@code OrbitMath.subpoint}).
 */
public record Satellite(UUID id, String name, UUID owner, String ownerName, int tier, int incl, double raan, double phase0, long epoch, int mode,
                        String payload, boolean abandoned, long reentryStart, int targetX, int targetZ) {
    public static final long NO_REENTRY = Long.MIN_VALUE;
    public static final int STANDBY = 0, BEACON = 1, FLASH = 2;
    public static final int MODES = 3;
    public static final int GEOSTATIONARY_TIER = 3;
    /** Anvil names go to 50; the packet limit below is higher, so a long name can never break the player list. */
    public static final int MAX_NAME = 50;

    /** A plain CAE satellite, without a payload of another mod. */
    public Satellite(UUID id, String name, UUID owner, String ownerName, int tier, int incl, double raan, double phase0, long epoch, int mode) {
        this(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, mode, "");
    }

    public Satellite(UUID id, String name, UUID owner, String ownerName, int tier, int incl, double raan, double phase0, long epoch, int mode,
                     String payload) {
        this(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, mode, payload, false, NO_REENTRY, 0, 0);
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

    /**
     * A new orbit from the place the satellite is at. A new altitude keeps the plane and the angle along it. A new inclination turns
     * the plane about the point under the satellite, so it stays over the same latitude and longitude and keeps heading north or
     * south as it did; when it is further from the equator than the new inclination allows, it keeps its longitude and moves to the
     * nearest latitude the new orbit reaches (the equator for 0 degrees).
     */
    public Satellite retuned(int newTier, int newIncl, long now) {
        double u = phase(now), r = raan;
        if (newIncl != incl) {
            double i0 = Math.toRadians(OrbitMath.INCLINATION_DEG[incl]), i1 = Math.toRadians(OrbitMath.INCLINATION_DEG[newIncl]);
            // where it is in the frame the Earth turns in
            double x = Math.cos(r) * Math.cos(u) - Math.sin(r) * Math.cos(i0) * Math.sin(u);
            double y = Math.sin(r) * Math.cos(u) + Math.cos(r) * Math.cos(i0) * Math.sin(u);
            double lat = Math.asin(Math.max(-1, Math.min(1, Math.sin(i0) * Math.sin(u)))), lon = Math.atan2(y, x);
            if (OrbitMath.INCLINATION_DEG[newIncl] == 0) {
                u = 0;
                r = lon;
            } else {
                double a = Math.asin(Math.max(-1, Math.min(1, Math.sin(lat) / Math.sin(i1))));
                boolean northbound = OrbitMath.INCLINATION_DEG[incl] == 0 || Math.cos(u) >= 0;
                u = northbound ? a : Math.PI - a;
                r = lon - Math.atan2(Math.cos(i1) * Math.sin(u), Math.cos(u));
            }
        }
        return new Satellite(id, name, owner, ownerName, newTier, newIncl, r, u, now, mode, payload, abandoned, reentryStart, targetX, targetZ);
    }

    public Satellite withMode(int newMode) {
        return new Satellite(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, newMode, payload, abandoned, reentryStart, targetX, targetZ);
    }

    /** Debris from now on, its lamp off. {@code decayAt} is when its orbit gives out ({@link #NO_REENTRY} for a stable one). */
    public Satellite abandon(long decayAt, int targetX, int targetZ) {
        return new Satellite(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, STANDBY, payload, true, decayAt, targetX, targetZ);
    }

    /** Leaves its orbit at {@code now} and burns up over the block (x, z). */
    public Satellite reenter(long now, int targetX, int targetZ) {
        return new Satellite(id, name, owner, ownerName, tier, incl, raan, phase0, epoch, mode, payload, abandoned, now, targetX, targetZ);
    }

    public boolean reentering(double tick) {
        return reentryStart != NO_REENTRY && tick >= reentryStart;
    }

    /** A reentry is set, now or later. */
    public boolean reentryPlanned() {
        return reentryStart != NO_REENTRY;
    }

    /** The tick it has burnt up; only meaningful with a reentry planned. */
    public long reentryEnd() {
        return reentryStart + OrbitMath.REENTRY_DESCENT_TICKS + OrbitMath.REENTRY_FIRE_TICKS;
    }

    /** Glowing in the sky between these ticks. */
    public boolean burning(double tick) {
        return reentryPlanned() && tick >= reentryStart + OrbitMath.REENTRY_DESCENT_TICKS && tick < reentryEnd();
    }

    /** Owner or operator, and only an operator once it is abandoned. */
    public boolean controlledBy(UUID player, boolean op) {
        return op || !abandoned && owner.equals(player);
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
        if (abandoned) t.putBoolean("abandoned", true);
        if (reentryPlanned()) {
            t.putLong("reentry", reentryStart);
            t.putInt("targetX", targetX);
            t.putInt("targetZ", targetZ);
        }
        return t;
    }

    public static Satellite load(CompoundTag t) {
        return new Satellite(t.getUUID("id"), t.getString("name"), t.getUUID("owner"), t.getString("ownerName"),
                Math.max(0, Math.min(OrbitMath.ALT_KM.length - 1, t.getInt("tier"))),
                Math.max(0, Math.min(OrbitMath.INCLINATION_DEG.length - 1, t.getInt("incl"))),
                t.getDouble("raan"), t.getDouble("phase0"), t.getLong("epoch"), Math.floorMod(t.getInt("mode"), MODES), t.getString("payload"),
                t.getBoolean("abandoned"), t.contains("reentry") ? t.getLong("reentry") : NO_REENTRY, t.getInt("targetX"), t.getInt("targetZ"));
    }

    public void write(FriendlyByteBuf b) {
        b.writeUUID(id).writeUtf(name, 64).writeUUID(owner).writeUtf(ownerName, 64).writeVarInt(tier).writeVarInt(incl)
                .writeDouble(raan).writeDouble(phase0).writeLong(epoch).writeVarInt(mode).writeUtf(payload, 128)
                .writeBoolean(abandoned).writeLong(reentryStart).writeVarInt(targetX).writeVarInt(targetZ);
    }

    public static Satellite read(FriendlyByteBuf b) {
        return new Satellite(b.readUUID(), b.readUtf(64), b.readUUID(), b.readUtf(64), b.readVarInt(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readLong(), b.readVarInt(), b.readUtf(128), b.readBoolean(), b.readLong(), b.readVarInt(), b.readVarInt());
    }
}
