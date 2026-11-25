package net.createaerospace.orbit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createaerospace.block.ConsoleButton;
import net.minecraft.server.MinecraftServer;

/** What the console buttons do to a satellite. The console block and the Control Computer app both go through here. */
public final class SatelliteControl {
    /** A console button, and for a dial which half of it was pressed. */
    public record Press(ConsoleButton button, boolean up) {}

    private SatelliteControl() {}

    /** The selected satellite; the first one while nothing valid is selected. */
    public static Satellite pick(List<Satellite> all, UUID selected) {
        for (Satellite s : all) if (s.id().equals(selected)) return s;
        return all.isEmpty() ? null : all.get(0);
    }

    /** Mode, dials and deorbit. Permission and the deorbit cover are the caller's business. Everything else is only a selection. */
    public static void apply(MinecraftServer server, Satellite s, ConsoleButton button, boolean up, long now) {
        SatelliteData data = SatelliteData.get(server);
        int step = up ? 1 : -1;
        switch (button) {
            case MODE -> data.put(server, s.withMode((s.mode() + 1) % Satellite.MODES));
            case ALTITUDE -> data.put(server, s.retuned(clamp(s.tier() + step, OrbitMath.ALT_KM.length), s.incl(), now));
            case INCLINATION -> data.put(server, s.retuned(s.tier(), clamp(s.incl() + step, OrbitMath.INCLINATION_DEG.length), now));
            case DEORBIT -> data.remove(server, s.id());
            default -> {}
        }
    }

    /** Whether a button can do anything for this satellite: a dial at the end of its scale, or a selection with nothing else to select, cannot. */
    public static boolean enabled(Satellite s, int count, Press press) {
        return switch (press.button()) {
            case PREVIOUS, NEXT -> count > 1;
            case ALTITUDE -> press.up() ? s.tier() < OrbitMath.ALT_KM.length - 1 : s.tier() > 0;
            case INCLINATION -> press.up() ? s.incl() < OrbitMath.INCLINATION_DEG.length - 1 : s.incl() > 0;
            default -> true;
        };
    }

    public static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    /** Button ids of the Control Computer app: every console button except the map, a dial as a pair {@code _down} / {@code _up}. */
    public static List<String> appButtons() {
        List<String> ids = new ArrayList<>();
        for (ConsoleButton b : ConsoleButton.values()) {
            if (b == ConsoleButton.MAP) continue;
            if (b.dial()) {
                ids.add(b.label() + "_down");
                ids.add(b.label() + "_up");
            } else {
                ids.add(b.label());
            }
        }
        return ids;
    }

    public static Press parse(String id) {
        for (ConsoleButton b : ConsoleButton.values()) {
            if (b == ConsoleButton.MAP) continue;
            if (b.dial() ? id.equals(b.label() + "_down") || id.equals(b.label() + "_up") : id.equals(b.label())) return new Press(b, id.endsWith("_up"));
        }
        return null;
    }
}
