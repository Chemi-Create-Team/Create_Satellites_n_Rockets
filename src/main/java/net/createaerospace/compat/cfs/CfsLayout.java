package net.createaerospace.compat.cfs;

import java.util.ArrayList;
import java.util.List;
import net.createaerospace.client.globe.CubeEarth;
import net.createaerospace.orbit.SatelliteControl;

/**
 * Where everything is on the Control Computer app, in fractions of the canvas (x right, y down). The renderer draws from it and the
 * server finds the clicked button from it, so the two cannot disagree. No CFS types here.
 */
public final class CfsLayout {
    public enum View {
        CONTROL, MAP;

        public static View of(int i) {
            return i == MAP.ordinal() ? MAP : CONTROL;
        }
    }

    public record Rect(float l, float t, float r, float b) {
        public boolean contains(double x, double y) {
            return x >= l && x < r && y >= t && y < b;
        }

        public boolean overlaps(Rect o) {
            return l < o.r && o.l < r && t < o.b && o.t < b;
        }
    }

    public static final String TAB_CONTROL = "tab_control", TAB_MAP = "tab_map";
    public static final Rect TAB_CONTROL_RECT = new Rect(.03f, .03f, .20f, .11f), TAB_MAP_RECT = new Rect(.21f, .03f, .38f, .11f);
    /** The status line over the readings, the readings (three columns), and the map. */
    public static final Rect STATUS = new Rect(.03f, .14f, .97f, .22f), READINGS = new Rect(.03f, .24f, .97f, .62f), MAP = new Rect(.03f, .14f, .97f, .80f);
    /** The line over the buttons of the map view. */
    public static final Rect MAP_SUMMARY = new Rect(.03f, .81f, .97f, .87f);

    private CfsLayout() {}

    /** Turning the Earth on the map view, a step of 45 degrees. */
    public static final String ROTATE_LEFT = "rotate_left", ROTATE_RIGHT = "rotate_right";

    /** The buttons shown in a view: all of the console in the control view, the selection and the turning of the Earth under the map. */
    public static List<String> buttons(View v) {
        return v == View.CONTROL ? SatelliteControl.appButtons() : List.of("previous", "next", ROTATE_LEFT, ROTATE_RIGHT);
    }

    public static Rect button(View v, String id) {
        int i = buttons(v).indexOf(id);
        if (i < 0) return null;
        if (v == View.MAP) return new Rect(.03f + i * .18f, .88f, .20f + i * .18f, .96f);
        float left = .03f + (i % 5) * .19f, top = .66f + (i / 5) * .155f;
        return new Rect(left, top, left + .175f, top + .135f);
    }

    /**
     * The globe on the map view of a canvas {@code w} by {@code h} (in the canvas's units): a square in the middle of {@link #MAP},
     * seen from over the board ({@code homeLat}, {@code homeLon}, degrees; latitude kept within 40 degrees) turned {@code rot} steps
     * of 45 degrees. The renderer draws with it and the server finds the clicked satellite with it.
     */
    public record MapView(CubeEarth.View view, float x0, float y0, float size, float k) {
        public boolean contains(double x, double y) {
            return x >= x0 && x < x0 + size && y >= y0 && y < y0 + size;
        }

        /** How far from a dot a click still picks it, in canvas units: a little over one tile of the Earth. */
        public double reach() {
            return k * 1.2;
        }
    }

    public static MapView mapView(float w, float h, double homeLat, double homeLon, int rot) {
        float aw = (MAP.r() - MAP.l()) * w, ah = (MAP.b() - MAP.t()) * h, size = Math.min(aw, ah) * .96f;
        float x0 = MAP.l() * w + (aw - size) / 2, y0 = MAP.t() * h + (ah - size) / 2, k = size / 2f / CubeEarth.FIT;
        return new MapView(new CubeEarth.View(Math.max(-40, Math.min(40, homeLat)), homeLon + 45 * rot, x0 + size / 2, y0 + size / 2, k),
                x0, y0, size, k);
    }

    /** The tab or button at a point, or null. */
    public static String hit(View v, double x, double y) {
        if (TAB_CONTROL_RECT.contains(x, y)) return TAB_CONTROL;
        if (TAB_MAP_RECT.contains(x, y)) return TAB_MAP;
        for (String id : buttons(v)) if (button(v, id).contains(x, y)) return id;
        return null;
    }

    /** Every clickable rectangle of a view, tabs first. */
    public static List<Rect> all(View v) {
        List<Rect> rects = new ArrayList<>(List.of(TAB_CONTROL_RECT, TAB_MAP_RECT));
        for (String id : buttons(v)) rects.add(button(v, id));
        return rects;
    }
}
