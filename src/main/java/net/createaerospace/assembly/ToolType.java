package net.createaerospace.assembly;

import java.util.Locale;

/** The five end effectors of the industrial arm. */
public enum ToolType {
    GRIPPER, WELDING_TORCH, LASER_CUTTER, SPRAY_NOZZLE, WIRING_HEAD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ToolType byId(String id) {
        for (ToolType t : values()) if (t.id().equals(id)) return t;
        return null;
    }
}
