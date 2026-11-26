package net.createaerospace.orbit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createaerospace.block.ConsoleButton;
import net.minecraft.core.BlockPos;
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

    /**
     * Mode, dials, reentry and abandoning. Permission and the cover are the caller's business. Everything else is only a selection.
     * {@code where} is the console or the computer: a commanded reentry burns up over it, so its crew sees the fireball.
     */
    public static void apply(MinecraftServer server, Satellite s, ConsoleButton button, boolean up, long now, BlockPos where) {
        SatelliteData data = SatelliteData.get(server);
        int step = up ? 1 : -1;
        if (s.reentering(now) || s.reentryPlanned() && !s.abandoned()) return;
        if (s.abandoned() && button != ConsoleButton.DEORBIT) return;
        switch (button) {
            case MODE -> data.put(server, s.withMode((s.mode() + 1) % Satellite.MODES));
            case ALTITUDE -> data.put(server, s.retuned(clamp(s.tier() + step, OrbitMath.ALT_KM.length), s.incl(), now));
            case INCLINATION -> data.put(server, s.retuned(s.tier(), clamp(s.incl() + step, OrbitMath.INCLINATION_DEG.length), now));
            case DEORBIT -> data.put(server, s.reenter(now, where.getX(), where.getZ()));
            case ABANDON -> data.put(server, abandoned(server, s, now));
            default -> {}
        }
    }

    /**
     * Debris from now on. A low orbit decays: the lowest tier comes down after 1 to 3 days, the next after 7 to 14, over a random
     * place within 1500 blocks of the world spawn (where the players most likely are); higher orbits stay up for good.
     */
    public static Satellite abandoned(MinecraftServer server, Satellite s, long now) {
        var random = server.overworld().getRandom();
        long decay = switch (s.tier()) {
            case 0 -> 24000 + random.nextInt(48001);
            case 1 -> 168000 + random.nextInt(168001);
            default -> -1;
        };
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        return s.abandon(decay < 0 ? Satellite.NO_REENTRY : now + decay,
                spawn.getX() + random.nextInt(3001) - 1500, spawn.getZ() + random.nextInt(3001) - 1500);
    }

    /**
     * Whether a button can do anything for this satellite: a dial at the end of its scale, a selection with nothing else to select,
     * anything but the selection while it comes down, and anything but a reentry once it is abandoned, cannot.
     */
    public static boolean enabled(Satellite s, int count, Press press) {
        if (press.button() != ConsoleButton.PREVIOUS && press.button() != ConsoleButton.NEXT && press.button() != ConsoleButton.MAP) {
            if (s.reentryPlanned() && !s.abandoned()) return false;
            if (s.abandoned()) return press.button() == ConsoleButton.DEORBIT;
        }
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
