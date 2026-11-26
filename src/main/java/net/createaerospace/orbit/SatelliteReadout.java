package net.createaerospace.orbit;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the ground console shows about the selected satellite. The console screen and the Control Computer app (see
 * {@code compat.cfs}) both read it from here, so a new line or value has to be added in this one place.
 */
public record SatelliteReadout(String name, int number, int count, String lat, String lon, int blockX, int blockZ, int altKm,
                               int inclDeg, String periodMin, int mode, boolean above, int elevation, int azimuth, String owner,
                               String payload, int state, long untilTicks) {
    public static final String K = "console.create_aerospace_engineering.";
    /** In orbit and controlled; abandoned (debris); coming down; burning up in the sky. */
    public static final int ACTIVE = 0, ABANDONED = 1, DESCENDING = 2, BURNING = 3;

    /** {@code index} counts from 0; {@code viewer} is the block the satellite is looked at from. */
    public static SatelliteReadout of(Satellite s, int index, int count, double tick, BlockPos viewer) {
        double[] p = OrbitMath.subpoint(s, tick);
        int[] block = OrbitMath.groundBlock(p);
        var look = OrbitMath.lookFromBlock(s, tick, viewer.getX() + .5, viewer.getZ() + .5);
        return new SatelliteReadout(s.name(), index + 1, count, OrbitMath.fmtLat(p[0]), OrbitMath.fmtLon(p[1]), block[0], block[1],
                (int) Math.round(OrbitMath.altKm(s, tick)), OrbitMath.INCLINATION_DEG[s.incl()], String.format(java.util.Locale.ROOT, "%.1f", s.periodTicks() / 1200.0), s.mode(),
                look.elevation() > 0, (int) Math.toDegrees(look.elevation()), (int) Math.floorMod((long) Math.toDegrees(look.azimuth()), 360L),
                s.ownerName(), s.payload(), state(s, tick),
                s.reentering(tick) || s.reentryPlanned() ? (long) (s.reentering(tick) ? s.reentryEnd() - tick : s.reentryStart() - tick) : -1);
    }

    public static int state(Satellite s, double tick) {
        if (s.burning(tick)) return BURNING;
        if (s.reentering(tick)) return DESCENDING;
        return s.abandoned() ? ABANDONED : ACTIVE;
    }

    /** The status line under the title, or null while all is normal. */
    public Component stateLine() {
        MutableComponent line = switch (state) {
            case ABANDONED -> untilTicks < 0 ? Component.translatable(K + "state.abandoned_stable")
                    : Component.translatable(K + "state.abandoned_decay", String.format(java.util.Locale.ROOT, "%.1f", untilTicks / 24000.0));
            case DESCENDING -> Component.translatable(K + "state.reentry", untilTicks / 20);
            case BURNING -> Component.translatable(K + "state.burning", untilTicks / 20);
            default -> null;
        };
        return line == null ? null : line.withColor(stateColor(state) & 0xFFFFFF);
    }

    /** Red for a satellite coming down, grey for debris; null for a normal one. */
    public static Integer stateColor(int state) {
        return switch (state) {
            case ABANDONED -> 0xFF9AA4B0;
            case DESCENDING, BURNING -> 0xFFFF6040;
            default -> null;
        };
    }

    public static final int ALERT_RED = 0xFF4040, ALERT_ORANGE = 0xFFA040;

    /** Blinks once a second, for every screen that shows a warning. */
    public static boolean alertLit(double tick) {
        return (long) tick / 10 % 2 == 0;
    }

    /** The warning to show now, the latest one that is still recent, or null. */
    public static Alert activeAlert(List<Alert> alerts, double tick) {
        for (int i = alerts.size() - 1; i >= 0; i--) if (alerts.get(i).shown(tick)) return alerts.get(i);
        return null;
    }

    /** The warning as one line: what happened, to what, where. */
    public static Component alertLine(Alert a) {
        if (a.kind() == Alert.LANDING_FAILURE) {
            var type = net.createaerospace.api.RocketApi.rocketOrStandard(ResourceLocation.tryParse(a.subject()));
            return Component.translatable(K + "alert.landing_failure", type.type().name(), a.owner(), a.x(), a.z()).withColor(ALERT_RED);
        }
        if (a.kind() == Alert.LAUNCH_FAILURE) {
            var type = net.createaerospace.api.RocketApi.rocketOrStandard(ResourceLocation.tryParse(a.subject()));
            Component name = type == null ? Component.literal(a.subject()) : type.type().name();
            return Component.translatable(K + "alert.launch_failure", name, a.owner(), a.count(), a.x(), a.z()).withColor(ALERT_RED);
        }
        return Component.translatable(K + "alert.reentry", a.subject(), a.x(), a.z()).withColor(ALERT_ORANGE);
    }

    /** Standby amber, beacon green, flash cyan: the lamp on the console and on the map screen, and the mode in the Control Computer app. */
    public static final int[] MODE_COLORS = {0xFFB020, 0x30FF60, 0x40E0FF};

    public static int modeColor(int mode) {
        return MODE_COLORS[mode];
    }

    public MutableComponent modeName() {
        return Component.translatable(K + "mode." + mode);
    }

    /** The name of the payload of another mod, or null for a plain satellite (or one whose mod is gone). */
    public Component payloadName() {
        return payload.isEmpty() ? null : net.createaerospace.api.AerospaceApi.payloadName(ResourceLocation.tryParse(payload)).orElse(null);
    }

    /** The line that tells whether the satellite is above the horizon of the viewer. */
    public MutableComponent visibility() {
        return above ? Component.translatable(K + "above", elevation, azimuth) : Component.translatable(K + "below");
    }

    /** The lines of the console screen. */
    public List<Component> lines() {
        List<Component> lines = new java.util.ArrayList<>(List.of(
                Component.translatable(K + "title", name, number, count),
                Component.translatable(K + "pos", lat, lon),
                Component.translatable(K + "block", blockX, blockZ),
                Component.translatable(K + "orbit", altKm, inclDeg, periodMin),
                Component.translatable(K + "mode", modeName()),
                visibility(),
                Component.translatable(K + "owner", owner)));
        if (payloadName() != null) lines.add(1, Component.translatable(K + "payload", payloadName()));
        if (stateLine() != null) lines.add(1, stateLine());
        return lines;
    }

    public static List<Component> noneLines() {
        return List.of(Component.translatable(K + "none"), Component.translatable(K + "none_hint"));
    }
}
