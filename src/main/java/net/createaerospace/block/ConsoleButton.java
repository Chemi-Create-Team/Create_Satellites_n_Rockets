package net.createaerospace.block;

import java.util.Locale;

/**
 * The controls on the console top, as a fraction of the block seen by the operator: u from their left edge, v from their near edge.
 * Dials step down when pressed on their left half and up on their right half.
 */
public enum ConsoleButton {
    PREVIOUS(.15, .10, .09), NEXT(.35, .10, .09), MAP(.55, .10, .09), MODE(.75, .10, .09),
    ALTITUDE(.22, .29, .13), INCLINATION(.50, .29, .13), DEORBIT(.85, .29, .10),
    /** The small hazard-striped button by the near right corner; like DEORBIT it only works with the cover open. */
    ABANDON(.92, .08, .06);

    public final double u, v, radius;

    ConsoleButton(double u, double v, double radius) {
        this.u = u;
        this.v = v;
        this.radius = radius;
    }

    /** Pressed on its left half it steps down, on its right half up. */
    public boolean dial() {
        return this == ALTITUDE || this == INCLINATION;
    }

    /** Under the cover: the first press opens it, a press with it open does the thing. */
    public boolean guarded() {
        return this == DEORBIT || this == ABANDON;
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ConsoleButton at(double u, double v) {
        for (ConsoleButton b : values()) {
            if (Math.abs(u - b.u) <= b.radius && Math.abs(v - b.v) <= b.radius) return b;
        }
        return null;
    }
}
