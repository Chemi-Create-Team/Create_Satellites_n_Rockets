package net.createaerospace.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.createaerospace.client.globe.CubeEarth;
import net.createaerospace.client.globe.Crowd;
import net.createaerospace.client.globe.Spots;
import net.createaerospace.client.globe.Spots.Spot;
import net.createaerospace.client.globe.CubeEarth.Sink;
import net.createaerospace.client.globe.CubeEarth.View;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.relay.RelayStorage;

/**
 * The cube Earth under its clouds with the satellites over it: what the map screen and the Control Computer app both draw. A satellite
 * is a dot, its orbit a circle, and both take the colour of the orbit's tier, so a low orbit close round the Earth and the
 * geostationary circle far out read apart at a glance.
 */
public final class GlobeScene {
    public static final int YELLOW = 0xFFFFE060, HOME = 0xFF50FF70;
    /** By altitude tier: low (green), high low (teal), medium (blue), geostationary (violet). */
    public static final int[] TIER_COLORS = {0xFF55FF77, 0xFF3FD8D0, 0xFF5A9BFF, 0xFFC58CFF};
    private static final int REENTRY = 0xFFFF7020, DEBRIS = 0xFF808890, OUTLINE = 0xFF04070D, GEO_BELT = 0xFF34305A;
    /** Line widths and dot sizes, half of each, in tiles. */
    private static final float LINE = .05f, SEL_LINE = .09f, DOT = .3f, SEL_DOT = .42f, EDGE = .11f;
    /** Marks on the ground sit over the clouds. */
    private static final float GROUND_LIFT = CubeEarth.CLOUD_LIFT + .1f;

    /** A satellite name and where it goes on the screen. */
    public record Label(String name, float x, float y, boolean selected) {}


    private GlobeScene() {}

    /**
     * {@code home} is the console or the computer: a green dot on the surface. Every satellite has a dot, a thin line down to the
     * point under it and its orbit as a circle of its tier's radius. The geostationary belt is a dashed circle over the equator.
     * The selected satellite is larger and yellow, with a yellow ring and a yellow mark on the ground. With many satellites
     * ({@link Crowd}) satellites in the same orbit share a circle, and past the limits the circles become short arcs, the lines to
     * the ground go and the dots get smaller.
     */
    public static void draw(Sink sink, View v, List<Satellite> all, Satellite sel, double tick, double homeLatDeg, double homeLonDeg,
                            float x0, float y0, float x1, float y1, float starHalf, float starDepth) {
        for (int i = 0; i < 90; i++) {
            int h = i * 2654435761L % 1000 > 500 ? 1 : 0;
            float x = x0 + (float) ((i * 0.6180339887 * 7919 % 1) * (x1 - x0)), y = y0 + (float) ((i * 0.7548776662 * 104729 % 1) * (y1 - y0));
            CubeEarth.star(sink, x, y, starHalf * (1 + h), starDepth, h == 1 ? 0xFFFFFFFF : 0xFF8FA0B8);
        }
        Globes.get().drawEarth(sink, v);
        Globes.get().drawClouds(sink, v, tick);
        geostationaryBelt(sink, v);
        marker(sink, v, CubeEarth.surface(Math.toRadians(homeLatDeg), Math.toRadians(homeLonDeg), GROUND_LIFT), .22f, HOME, false);

        List<Satellite> circles = Crowd.circles(all, sel, tick);
        boolean whole = circles.size() <= Crowd.CIRCLES, tethers = all.size() <= Crowd.TETHERS;
        if (whole) for (Satellite s : circles) path(sink, v, s, tick, 64, LINE, CubeEarth.mul(color(s, tick), .5f));
        for (Satellite s : all) {
            if (s == sel) continue;
            int c = CubeEarth.mul(color(s, tick), .5f);
            if (s.reentering(tick)) path(sink, v, s, tick, 48, LINE, c);
            else if (!whole) arc(sink, v, s, tick, c);
            if (tethers) CubeEarth.line(sink, v, position(s, tick), ground(s, tick), LINE * .7f, CubeEarth.mul(c, .8f));
        }
        if (sel != null) {
            path(sink, v, sel, tick, 144, SEL_LINE, sel.reentering(tick) ? REENTRY : YELLOW);
            CubeEarth.line(sink, v, position(sel, tick), ground(sel, tick), LINE, YELLOW);
            double[] sp = OrbitMath.subpoint(sel, tick);
            marker(sink, v, CubeEarth.surface(sp[0], sp[1], GROUND_LIFT), .19f, YELLOW, false);
        }
        float dot = all.size() > Crowd.CROWDED ? DOT * .72f : DOT;
        for (Satellite s : all) {
            boolean selected = s == sel;
            marker(sink, v, position(s, tick), selected ? SEL_DOT : dot, selected ? YELLOW : color(s, tick),
                    RelayStorage.isRelay(s) && !s.abandoned() && !s.reentering(tick));
        }
    }

    /** A tenth of the orbit behind the satellite, where there are too many orbits to draw each whole. */
    private static void arc(Sink sink, View v, Satellite s, double tick, int argb) {
        double r = radius(s, tick);
        double[] last = position(s, tick);
        for (int i = 1; i <= 8; i++) {
            double[] sp = OrbitMath.orbitPoint(s, tick, -2 * Math.PI * i / 80);
            double[] p = CubeEarth.position(sp[0], sp[1], r);
            CubeEarth.line(sink, v, last, p, LINE, CubeEarth.mul(argb, 1 - i / 10f));
            last = p;
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
            CubeEarth.diamond(sink, v, p, half + EDGE, .15f, OUTLINE);
            CubeEarth.diamond(sink, v, p, half, .35f, argb);
        } else {
            CubeEarth.dot(sink, v, p, half + EDGE, .15f, OUTLINE);
            CubeEarth.dot(sink, v, p, half, .35f, argb);
        }
    }

    /** The equator at the geostationary radius, in dashes: where a communications satellite hangs still over one place. */
    private static void geostationaryBelt(Sink sink, View v) {
        double r = CubeEarth.orbitRadius(OrbitMath.ALT_KM.length - 1);
        int steps = 96;
        for (int i = 0; i < steps; i += 2) {
            CubeEarth.line(sink, v, CubeEarth.position(0, 2 * Math.PI * i / steps, r), CubeEarth.position(0, 2 * Math.PI * (i + 1) / steps, r), LINE, GEO_BELT);
        }
    }

    /**
     * The names to show: of satellites not behind the Earth, the selected one and the one under the pointer ({@code hovered}, may be
     * null) first, then the nearer ones, leaving out any that would cover a name already placed, at most {@code max}. Past
     * {@link Crowd#CROWDED} satellites only the selected and the hovered one are named, so the names do not hide the Earth. A name
     * starts {@code dx} right of its dot but no further right than ends it at {@code right}, is {@code width} wide and
     * {@code height} high, centred on the dot; the label's x is where the name starts.
     */
    public static List<Label> labels(View v, List<Satellite> all, Satellite sel, Satellite hovered, double tick,
                                     ToDoubleFunction<String> width, float dx, float right, float height, int max) {
        boolean crowded = all.size() > Crowd.CROWDED;
        List<Spot> spots = new ArrayList<>();
        for (Spot spot : Spots.of(v, all, tick)) {
            if (spot.hidden() || crowded && spot.satellite() != sel && spot.satellite() != hovered) continue;
            spots.add(spot);
        }
        spots.sort(Comparator.comparingInt((Spot spot) -> spot.satellite() == sel ? 0 : spot.satellite() == hovered ? 1 : 2)
                .thenComparingDouble(spot -> -spot.depth()));
        List<Label> out = new ArrayList<>();
        for (Spot spot : Crowd.declutter(spots, spot -> {
            float w = (float) width.applyAsDouble(spot.satellite().name()), x = Math.min(spot.x() + dx, right - w);
            return new float[] {x, spot.y() - height / 2, x + w, spot.y() + height / 2};
        }, max)) {
            float w = (float) width.applyAsDouble(spot.satellite().name());
            out.add(new Label(spot.satellite().name(), Math.min(spot.x() + dx, right - w), spot.y(), spot.satellite() == sel));
        }
        return out;
    }

    /** The satellite whose dot is nearest a point of the screen, within {@code reach} screen units; those in front first. Or null. */
    public static Satellite pick(View v, List<Satellite> all, double tick, double x, double y, double reach) {
        return Spots.pick(v, all, tick, x, y, reach);
    }

    private static double[] position(Satellite s, double tick) {
        return Spots.position(s, tick);
    }

    private static double[] ground(Satellite s, double tick) {
        return Spots.ground(s, tick);
    }

    private static double radius(Satellite s, double tick) {
        return Spots.radius(s, tick);
    }

    /** The orbit as a circle; on the way down, the rest of the path to where it burns out. */
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
