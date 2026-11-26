package net.createaerospace.client;

import java.util.ArrayList;
import java.util.List;
import net.createaerospace.client.globe.VoxelGlobe;
import net.createaerospace.client.globe.VoxelGlobe.Sink;
import net.createaerospace.client.globe.VoxelGlobe.View;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.relay.RelayStorage;

/** The Earth with the satellites over it: what the map screen and the Control Computer app both draw. */
public final class GlobeScene {
    public static final int YELLOW = 0xFFFFE060, HOME = 0xFF50FF70;
    private static final int OTHER = 0xFFE0E0E0, OTHER_PANEL = 0xFF4A7090, SEL_PANEL = 0xFFC9962C;

    /** A satellite name and where it goes on the screen. */
    public record Label(String name, float x, float y, boolean selected) {}

    private GlobeScene() {}

    /**
     * {@code home} is the console or the computer: a green cube on the surface. The orbit of the selected satellite is a yellow line of
     * dots, one orbit ahead and a little behind; the others a dim one. The Earth turns under them, so an orbit is not a closed curve.
     */
    public static void draw(Sink sink, View v, List<Satellite> all, Satellite sel, double tick, double homeLatDeg, double homeLonDeg,
                            float x0, float y0, float x1, float y1, float starHalf, float starDepth) {
        for (int i = 0; i < 90; i++) {
            int h = i * 2654435761L % 1000 > 500 ? 1 : 0;
            float x = x0 + (float) ((i * 0.6180339887 * 7919 % 1) * (x1 - x0)), y = y0 + (float) ((i * 0.7548776662 * 104729 % 1) * (y1 - y0));
            VoxelGlobe.star(sink, x, y, starHalf * (1 + h), starDepth, h == 1 ? 0xFFFFFFFF : 0xFF8FA0B8);
        }
        Globes.get().drawEarth(sink, v);
        VoxelGlobe.dot(sink, v, VoxelGlobe.position(Math.toRadians(homeLatDeg), Math.toRadians(homeLonDeg), VoxelGlobe.N + 2.4), .9f, HOME);
        for (Satellite s : all) if (s != sel && !s.reentering(tick)) track(sink, v, s, tick, 120, 0, .22f, s.abandoned() ? 0xFF606870 : 0xFFB8C8E0, 0);
        if (sel != null) track(sink, v, sel, tick, 360, .12, .3f, YELLOW, 0xFF4A7090);
        for (Satellite s : all) {
            boolean selected = s == sel;
            // a satellite coming down glows orange, debris is grey
            int body = s.reentering(tick) ? 0xFFFF7020 : selected ? YELLOW : s.abandoned() ? 0xFF808890 : OTHER;
            if (RelayStorage.isRelay(s) && !s.abandoned() && !s.reentering(tick))
                VoxelGlobe.communicationsSatellite(sink, v, position(s, tick), selected ? 1.25f : .9f);
            else VoxelGlobe.satellite(sink, v, position(s, tick), selected ? 1.35f : 1f, body,
                    selected ? SEL_PANEL : s.abandoned() ? 0xFF3A4048 : OTHER_PANEL);
        }
    }

    /** The names of the satellites that are not behind the Earth. */
    public static List<Label> labels(View v, List<Satellite> all, Satellite sel, double tick) {
        List<Label> out = new ArrayList<>();
        for (Satellite s : all) {
            double[] p = position(s, tick);
            if (v.hidden(p)) continue;
            float[] at = v.at(p);
            out.add(new Label(s.name(), at[0], at[1], s == sel));
        }
        return out;
    }

    private static double[] position(Satellite s, double tick) {
        double[] sp = OrbitMath.subpoint(s, tick);
        return VoxelGlobe.position(sp[0], sp[1], radius(s, tick));
    }

    /** The drawn height of the orbit's tier; on the way down, between the tiers by the real altitude, and to the surface. */
    private static double radius(Satellite s, double tick) {
        if (!s.reentering(tick)) return VoxelGlobe.orbitRadius(s.tier());
        double alt = OrbitMath.altKm(s, tick);
        if (alt <= OrbitMath.ALT_KM[0]) return VoxelGlobe.N + (VoxelGlobe.orbitRadius(0) - VoxelGlobe.N) * alt / OrbitMath.ALT_KM[0];
        for (int i = 1; i < OrbitMath.ALT_KM.length; i++) {
            if (alt <= OrbitMath.ALT_KM[i]) {
                double f = (alt - OrbitMath.ALT_KM[i - 1]) / (OrbitMath.ALT_KM[i] - OrbitMath.ALT_KM[i - 1]);
                return VoxelGlobe.orbitRadius(i - 1) + (VoxelGlobe.orbitRadius(i) - VoxelGlobe.orbitRadius(i - 1)) * f;
            }
        }
        return VoxelGlobe.orbitRadius(OrbitMath.ALT_KM.length - 1);
    }

    /** One orbit ahead; on the way down, the rest of the path to where it burns out. */
    private static void track(Sink sink, View v, Satellite s, double tick, int steps, double before, float half, int ahead, int behind) {
        if (s.reentering(tick)) {
            double left = s.reentryEnd() - tick;
            for (int i = 0; i <= steps; i++) VoxelGlobe.dot(sink, v, position(s, tick + i * left / steps), half, 0xFFFF7020);
            return;
        }
        for (int i = -(int) Math.round(before * steps); i <= steps; i++) {
            VoxelGlobe.dot(sink, v, position(s, tick + i * s.periodTicks() / (double) steps), half, i < 0 ? behind : ahead);
        }
    }
}
