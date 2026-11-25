package net.createaerospace.orbit;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Everything the ground console shows about the selected satellite. The console screen and the Control Computer app (see
 * {@code compat.cfs}) both read it from here, so a new line or value has to be added in this one place.
 */
public record SatelliteReadout(String name, int number, int count, String lat, String lon, int blockX, int blockZ, int altKm,
                               int inclDeg, String periodMin, int mode, boolean above, int elevation, int azimuth, String owner) {
    public static final String K = "console.create_aerospace_engineering.";

    /** {@code index} counts from 0; {@code viewer} is the block the satellite is looked at from. */
    public static SatelliteReadout of(Satellite s, int index, int count, double tick, BlockPos viewer) {
        double[] p = OrbitMath.subpoint(s, tick);
        int[] block = OrbitMath.groundBlock(p);
        var look = OrbitMath.lookFromBlock(s, tick, viewer.getX() + .5, viewer.getZ() + .5);
        return new SatelliteReadout(s.name(), index + 1, count, OrbitMath.fmtLat(p[0]), OrbitMath.fmtLon(p[1]), block[0], block[1],
                s.altKm(), OrbitMath.INCLINATION_DEG[s.incl()], String.format("%.1f", s.periodTicks() / 1200.0), s.mode(),
                look.elevation() > 0, (int) Math.toDegrees(look.elevation()), (int) Math.floorMod((long) Math.toDegrees(look.azimuth()), 360L),
                s.ownerName());
    }

    /** Standby amber, beacon green, flash cyan: the lamp on the console and on the map screen, and the mode in the Control Computer app. */
    public static final int[] MODE_COLORS = {0xFFB020, 0x30FF60, 0x40E0FF};

    public static int modeColor(int mode) {
        return MODE_COLORS[mode];
    }

    public MutableComponent modeName() {
        return Component.translatable(K + "mode." + mode);
    }

    /** The line that tells whether the satellite is above the horizon of the viewer. */
    public MutableComponent visibility() {
        return above ? Component.translatable(K + "above", elevation, azimuth) : Component.translatable(K + "below");
    }

    /** The lines of the console screen. */
    public List<Component> lines() {
        return List.of(
                Component.translatable(K + "title", name, number, count),
                Component.translatable(K + "pos", lat, lon),
                Component.translatable(K + "block", blockX, blockZ),
                Component.translatable(K + "orbit", altKm, inclDeg, periodMin),
                Component.translatable(K + "mode", modeName()),
                visibility(),
                Component.translatable(K + "owner", owner));
    }

    public static List<Component> noneLines() {
        return List.of(Component.translatable(K + "none"), Component.translatable(K + "none_hint"));
    }
}
