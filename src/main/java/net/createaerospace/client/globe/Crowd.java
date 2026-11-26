package net.createaerospace.client.globe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.createaerospace.orbit.Satellite;

/**
 * Keeping the map fast and readable with many satellites (a hundred or more): satellites in the same orbit share one circle, past
 * {@link #CIRCLES} different orbits the rest show only a short arc, past {@link #TETHERS} satellites only the selected one has its line
 * to the ground, past {@link #CROWDED} the dots get smaller, and names that would cover each other are left out. No Minecraft client
 * types, so the game tests can check it.
 */
public final class Crowd {
    /** Different orbits drawn as whole circles; with more, every satellite has a short arc behind it instead. */
    public static final int CIRCLES = 40;
    /** Satellites that each get a line down to the ground; with more, only the selected one does. */
    public static final int TETHERS = 24;
    /** Satellites before the dots get smaller. */
    public static final int CROWDED = 40;

    private Crowd() {}

    /**
     * What makes the circle of an orbit on the map: its tier, inclination and (for an inclined orbit) the longitude of its ascending
     * node to a degree. The angle of the satellite along it does not change the circle. Debris is grey, so it has circles of its own.
     */
    public record Orbit(int tier, int incl, int nodeDeg, boolean debris) {}

    public static Orbit orbit(Satellite s) {
        int node = s.incl() == 0 ? 0 : Math.floorMod((int) Math.round(Math.toDegrees(s.raan())), 360);
        return new Orbit(s.tier(), s.incl(), node, s.abandoned());
    }

    /** One satellite for each different orbit, in the order of the list, leaving out {@code skip} and those coming down. */
    public static List<Satellite> circles(List<Satellite> all, Satellite skip, double tick) {
        Map<Orbit, Satellite> first = new LinkedHashMap<>();
        for (Satellite s : all) if (s != skip && !s.reentering(tick)) first.putIfAbsent(orbit(s), s);
        return new ArrayList<>(first.values());
    }

    /**
     * The items whose boxes ({l, t, r, b}) do not cover an earlier one, taking them in the order given (put the most wanted first),
     * at most {@code max} of them.
     */
    public static <T> List<T> declutter(List<T> wanted, Function<T, float[]> box, int max) {
        List<T> kept = new ArrayList<>();
        List<float[]> boxes = new ArrayList<>();
        for (T item : wanted) {
            if (kept.size() >= max) break;
            float[] b = box.apply(item);
            boolean free = true;
            for (float[] o : boxes) {
                if (b[0] < o[2] && o[0] < b[2] && b[1] < o[3] && o[1] < b[3]) {
                    free = false;
                    break;
                }
            }
            if (!free) continue;
            kept.add(item);
            boxes.add(b);
        }
        return kept;
    }
}
