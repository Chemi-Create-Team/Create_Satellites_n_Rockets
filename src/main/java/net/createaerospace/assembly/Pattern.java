package net.createaerospace.assembly;

import java.util.Locale;

/** How a tool moves over its cell. PLACE is the gripper's own drop and is not written in recipes. */
public enum Pattern {
    PLACE, SEAM_WELD, PERIMETER_CUT, DIAGONAL_CUT, GRID_WIRE, FULL_SPRAY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Pattern byId(String id) {
        for (Pattern p : values()) if (p.id().equals(id)) return p;
        return null;
    }

    /** The pattern a tool uses when a recipe step does not name one. */
    public static Pattern defaultFor(ToolType tool) {
        return switch (tool) {
            case GRIPPER -> PLACE;
            case WELDING_TORCH -> SEAM_WELD;
            case LASER_CUTTER -> PERIMETER_CUT;
            case SPRAY_NOZZLE -> FULL_SPRAY;
            case WIRING_HEAD -> GRID_WIRE;
        };
    }
}
