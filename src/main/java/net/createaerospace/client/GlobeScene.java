package net.createaerospace.client;

import java.util.ArrayList;
import java.util.List;
import net.createaerospace.client.globe.CubeEarth;
import net.createaerospace.client.globe.CubeEarth.Sink;
import net.createaerospace.client.globe.CubeEarth.View;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.relay.RelayStorage;

/**
 * The cube Earth with the satellites over it: what the map screen and the Control Computer app both draw. A satellite is a dot, its
 * orbit a line, and both take the colour of the orbit's tier, so a low orbit hugging the Earth and the geostationary ring far out read
 * apart at a glance.
 */
public final class GlobeScene {
    public static final int YELLOW = 0xFFFFE060, HOME = 0xFF50FF70;
    /** By altitude tier: low (green), high low (teal), medium (blue), geostationary (violet). */
    public static final int[] TIER_COLORS = {0xFF55FF77, 0xFF3FD8D0, 0xFF5A9BFF, 0xFFC58CFF};
    private static final int REENTRY = 0xFFFF7020, DEBRIS = 0xFF808890, OUTLINE = 0xFF04070D, GEO_BELT = 0xFF34305A;
    /** Line widths and dot sizes, half of each, in tiles. */
    private static final float LINE = .07f, SEL_LINE = .13f, DOT = .42f, SEL_DOT = .6f, EDGE = .16f;

    /** A satellite name and where it goes on the screen. */
    public record Label(String name, float x, float y, boolean selected) {}

    private GlobeScene() {}

    /**
     * {@code home} is the console or the computer: a green dot on the surface. Every satellite has a dot, a thin line down to the
     * point under it and its orbit as a closed ring at its tier's height. The geostationary belt is a dashed ring over the equator.
     * The selected satellite is larger and yellow, with a yellow ring and a yellow mark on the ground.
     */
    public static void draw(Sink sink, View v, List<Satellite> all, Satellite sel, double tick, double homeLatDeg, double homeLonDeg,
                            float x0, float y0, float x1, float y1, float starHalf, float starDepth) {
        for (int i = 0; i < 90; i++) {
            int h = i * 2654435761L % 1000 > 500 ? 1 : 0;
            float x = x0 + (float) ((i * 0.6180339887 * 7919 % 1) * (x1 - x0)), y = y0 + (float) ((i * 0.7548776662 * 104729 % 1) * (y1 - y0));
            CubeEarth.star(sink, x, y, starHalf * (1 + h), starDepth, h == 1 ? 0xFFFFFFFF : 0xFF8FA0B8);
        }
        Globes.get().drawEarth(sink, v);
        geostationaryBelt(sink, v);
        marker(sink, v, CubeEarth.position(Math.toRadians(homeLatDeg), Math.toRadians(homeLonDeg), CubeEarth.HALF + .3), .3f, HOME, false);

        for (Satellite s : all) {
            if (s == sel) continue;
            int c = CubeEarth.mul(color(s, tick), .5f);
            path(sink, v, s, tick, 96, LINE, c);
            CubeEarth.line(sink, v, position(s, tick), ground(s, tick), LINE * .7f, CubeEarth.mul(c, .8f));
        }
        if (sel != null) {
            path(sink, v, sel, tick, 144, SEL_LINE, sel.reentering(tick) ? REENTRY : YELLOW);
            CubeEarth.line(sink, v, position(sel, tick), ground(sel, tick), LINE, YELLOW);
            double[] sp = OrbitMath.subpoint(sel, tick);
            marker(sink, v, CubeEarth.position(sp[0], sp[1], CubeEarth.HALF + .3), .26f, YELLOW, false);
        }
        for (Satellite s : all) {
            boolean selected = s == sel;
            marker(sink, v, position(s, tick), selected ? SEL_DOT : DOT, selected ? YELLOW : color(s, tick),
                    RelayStorage.isRelay(s) && !s.abandoned() && !s.reentering(tick));
        }
    }

    /** The colour of a satellite: its tier's, orange while it comes down, grey when abandoned. */
    public static int color(Satellite s, double tick) {
        if (s.reentering(tick)) return REENTRY;
        if (s.abandoned()) return DEBRIS;
        return TIER_COLORS[Math.max(0, Math.min(TIER_COLORS.length - 1, s.tier()))];
    }

    /** A dot (a diamond for a communications satellite) with a dark edge, so it shows against land and sea alike. */
    private static void marker(Sink sink, View v, double[] p, float half, int argb, boolean relay) {
        if (relay) {
            CubeEarth.diamond(sink, v, p, half + EDGE, .2f, OUTLINE);
            CubeEarth.diamond(sink, v, p, half, .5f, argb);
        } else {
            CubeEarth.dot(sink, v, p, half + EDGE, .2f, OUTLINE);
            CubeEarth.dot(sink, v, p, half, .5f, argb);
        }
    }

    /** The equator at the geostationary height, in dashes: where a communications satellite hangs still over one place. */
    private static void geostationaryBelt(Sink sink, View v) {
        double r = CubeEarth.orbitRadius(OrbitMath.ALT_KM.length - 1);
        int steps = 96;
        for (int i = 0; i < steps; i += 2) {
            CubeEarth.line(sink, v, CubeEarth.position(0, 2 * Math.PI * i / steps, r), CubeEarth.position(0, 2 * Math.PI * (i + 1) / steps, r), LINE, GEO_BELT);
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
        return CubeEarth.position(sp[0], sp[1], radius(s, tick));
    }

    private static double[] ground(Satellite s, double tick) {
        double[] sp = OrbitMath.subpoint(s, tick);
        return CubeEarth.position(sp[0], sp[1], CubeEarth.HALF);
    }

    /** The drawn height of the orbit's tier; on the way down, between the tiers by the real altitude, and to the surface. */
    private static double radius(Satellite s, double tick) {
        if (!s.reentering(tick)) return CubeEarth.orbitRadius(s.tier());
        double alt = OrbitMath.altKm(s, tick);
        if (alt <= OrbitMath.ALT_KM[0]) return CubeEarth.HALF + (CubeEarth.orbitRadius(0) - CubeEarth.HALF) * alt / OrbitMath.ALT_KM[0];
        for (int i = 1; i < OrbitMath.ALT_KM.length; i++) {
            if (alt <= OrbitMath.ALT_KM[i]) {
                double f = (alt - OrbitMath.ALT_KM[i - 1]) / (OrbitMath.ALT_KM[i] - OrbitMath.ALT_KM[i - 1]);
                return CubeEarth.orbitRadius(i - 1) + (CubeEarth.orbitRadius(i) - CubeEarth.orbitRadius(i - 1)) * f;
            }
        }
        return CubeEarth.orbitRadius(OrbitMath.ALT_KM.length - 1);
    }

    /** The orbit as a closed ring; on the way down, the rest of the path to where it burns out. */
    private static void path(Sink sink, View v, Satellite s, double tick, int steps, float half, int argb) {
        double[] last = position(s, tick);
        if (s.reentering(tick)) {
            double left = s.reentryEnd() - tick;
            for (int i = 1; i <= steps; i++) {
                double[] p = position(s, tick + i * left / steps);
                CubeEarth.line(sink, v, last, p, half, argb);
                last = p;
            }
            return;
        }
        double r = radius(s, tick);
        for (int i = 1; i <= steps; i++) {
            double[] sp = OrbitMath.orbitPoint(s, tick, 2 * Math.PI * i / steps);
            double[] p = CubeEarth.position(sp[0], sp[1], r);
            CubeEarth.line(sink, v, last, p, half, argb);
            last = p;
        }
    }
}
