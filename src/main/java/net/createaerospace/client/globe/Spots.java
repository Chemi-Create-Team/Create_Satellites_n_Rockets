package net.createaerospace.client.globe;

import java.util.ArrayList;
import java.util.List;
import net.createaerospace.client.globe.CubeEarth.View;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;

/**
 * Where each satellite's dot is drawn over the cube Earth, and which dot a point of the screen is on. No Minecraft client types: the
 * map screen and the Control Computer app draw with it, and the server finds the satellite a player clicked in the app with it, from
 * the same view, so the two cannot disagree.
 */
public final class Spots {
    private Spots() {}

    /** A satellite and where its dot is on the screen, and whether the Earth is in front of it. */
    public record Spot(Satellite satellite, float x, float y, float depth, boolean hidden) {}

    public static List<Spot> of(View v, List<Satellite> all, double tick) {
        List<Spot> out = new ArrayList<>(all.size());
        for (Satellite s : all) {
            double[] p = position(s, tick);
            float[] at = v.at(p);
            out.add(new Spot(s, at[0], at[1], at[2], v.hidden(p)));
        }
        return out;
    }

    /** The satellite whose dot is nearest a point of the screen, within {@code reach} screen units; those in front first. Or null. */
    public static Satellite pick(View v, List<Satellite> all, double tick, double x, double y, double reach) {
        Satellite best = null;
        double bestScore = Double.MAX_VALUE;
        for (Spot spot : of(v, all, tick)) {
            double d = Math.hypot(spot.x() - x, spot.y() - y);
            if (d > reach) continue;
            // a dot behind the Earth can be picked only where no dot in front is near
            double score = d + (spot.hidden() ? reach * 2 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = spot.satellite();
            }
        }
        return best;
    }

    /** Where the satellite's dot is, along the Earth-fixed axes, in tiles. */
    public static double[] position(Satellite s, double tick) {
        double[] sp = OrbitMath.subpoint(s, tick);
        return CubeEarth.position(sp[0], sp[1], radius(s, tick));
    }

    /** The point of the cube's face under the satellite. */
    public static double[] ground(Satellite s, double tick) {
        double[] sp = OrbitMath.subpoint(s, tick);
        return CubeEarth.surface(sp[0], sp[1], 0);
    }

    /**
     * The drawn radius of the orbit's tier; on the way down, between the tiers by the real altitude, and under the lowest orbit down
     * to the face of the cube under it.
     */
    public static double radius(Satellite s, double tick) {
        if (!s.reentering(tick)) return CubeEarth.orbitRadius(s.tier());
        double alt = OrbitMath.altKm(s, tick);
        if (alt <= OrbitMath.ALT_KM[0]) {
            double[] sp = OrbitMath.subpoint(s, tick), g = CubeEarth.surface(sp[0], sp[1], 0);
            double floor = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);
            return floor + (CubeEarth.orbitRadius(0) - floor) * alt / OrbitMath.ALT_KM[0];
        }
        for (int i = 1; i < OrbitMath.ALT_KM.length; i++) {
            if (alt <= OrbitMath.ALT_KM[i]) {
                double f = (alt - OrbitMath.ALT_KM[i - 1]) / (OrbitMath.ALT_KM[i] - OrbitMath.ALT_KM[i - 1]);
                return CubeEarth.orbitRadius(i - 1) + (CubeEarth.orbitRadius(i) - CubeEarth.orbitRadius(i - 1)) * f;
            }
        }
        return CubeEarth.orbitRadius(OrbitMath.ALT_KM.length - 1);
    }
}
