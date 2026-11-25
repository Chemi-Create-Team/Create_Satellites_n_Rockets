package net.createaerospace.compat.cfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.ConsoleButton;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteControl;
import net.createaerospace.orbit.SatelliteData;
import net.createaerospace.orbit.SatelliteReadout;
import net.createfrontier.api.computer.ComputerApps;
import net.createfrontier.api.computer.ComputerApps.Application;
import net.createfrontier.api.computer.ComputerApps.Button;
import net.createfrontier.api.computer.ComputerApps.Context;
import net.createfrontier.api.computer.ComputerApps.Panel;
import net.createfrontier.api.computer.ComputerApps.Reading;
import net.createfrontier.api.computer.ComputerApps.Tone;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The ground console as an app of the CFS Control Computer, under "Extra apps". It shows the satellite the computer has selected
 * and has the console's buttons, except the map (that is a client screen, the Control Computer cannot draw it).
 * <p>
 * What it shows comes from {@link SatelliteReadout} and what the buttons do from {@link SatelliteControl}, the same code the
 * console block uses. When the console gains a line or a button, change it there and add it here.
 */
final class CfsComputerApp implements Application {
    private static final String K = "computer." + CreateAerospace.MOD_ID + ".";

    /** Selection and deorbit cover of each computer. ponytail: kept in memory, so a restart goes back to the first satellite. */
    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final class Desk {
        UUID selected;
        long guardOpenedAt = Long.MIN_VALUE / 2;
    }

    private final Map<Key, Desk> desks = new HashMap<>();

    static void register() {
        ComputerApps.register(CreateAerospace.id("ground_control"), new CfsComputerApp());
    }

    @Override
    public Component title() {
        return Component.translatable(K + "title");
    }

    private Desk desk(Context c) {
        return desks.computeIfAbsent(new Key(c.level().dimension(), c.computerPos().immutable()), k -> new Desk());
    }

    @Override
    public Panel snapshot(Context c) {
        List<Satellite> all = SatelliteData.get(c.level().getServer()).list();
        Desk desk = desk(c);
        Satellite s = SatelliteControl.pick(all, desk.selected);
        if (s == null) return new Panel(Component.translatable(SatelliteReadout.K + "none"), Tone.WARNING,
                List.of(new Reading(Component.translatable(K + "hint"), Component.translatable(SatelliteReadout.K + "none_hint"))), List.of());
        long now = c.level().getGameTime();
        var r = SatelliteReadout.of(s, all.indexOf(s), all.size(), now, c.computerPos());
        boolean cover = now - desk.guardOpenedAt < GroundConsoleBlockEntity.GUARD_TICKS;
        List<Button> buttons = new ArrayList<>();
        for (String id : SatelliteControl.appButtons()) {
            var press = SatelliteControl.parse(id);
            boolean enabled = switch (press.button()) {
                case PREVIOUS, NEXT -> all.size() > 1;
                case ALTITUDE -> press.up() ? s.tier() < OrbitMath.ALT_KM.length - 1 : s.tier() > 0;
                case INCLINATION -> press.up() ? s.incl() < OrbitMath.INCLINATION_DEG.length - 1 : s.incl() > 0;
                default -> true;
            };
            boolean deorbit = press.button() == ConsoleButton.DEORBIT;
            buttons.add(new Button(id, Component.translatable(K + "button." + id + (deorbit && cover ? "_confirm" : "")),
                    deorbit ? Tone.DANGER : Tone.NORMAL, enabled));
        }
        return new Panel(Component.literal(r.name()), r.mode() == Satellite.STANDBY ? Tone.NORMAL : Tone.GOOD, List.of(
                reading("selected", Component.literal(r.number() + " / " + r.count())),
                reading("owner", Component.literal(r.owner())),
                reading("position", Component.literal(r.lat() + "  " + r.lon())),
                reading("block", Component.literal("X " + r.blockX() + "  Z " + r.blockZ())),
                reading("altitude", Component.literal(r.altKm() + " km")),
                reading("inclination", Component.literal(r.inclDeg() + "°")),
                reading("period", Component.literal(r.periodMin() + " min")),
                reading("mode", r.modeName()),
                reading("visibility", r.visibility())), buttons);
    }

    private static Reading reading(String key, Component value) {
        return new Reading(Component.translatable(K + "reading." + key), value);
    }

    @Override
    public void press(Context c, ServerPlayer player, String id) {
        var press = SatelliteControl.parse(id);
        List<Satellite> all = SatelliteData.get(c.level().getServer()).list();
        Desk desk = desk(c);
        Satellite s = SatelliteControl.pick(all, desk.selected);
        if (press == null || s == null) return;
        int i = all.indexOf(s);
        long now = c.level().getGameTime();
        switch (press.button()) {
            case PREVIOUS -> desk.selected = all.get(Math.floorMod(i - 1, all.size())).id();
            case NEXT -> desk.selected = all.get(Math.floorMod(i + 1, all.size())).id();
            default -> {
                if (!s.controlledBy(player.getUUID(), player.hasPermissions(2))) {
                    player.displayClientMessage(Component.translatable(SatelliteReadout.K + "denied"), true);
                    return;
                }
                if (press.button() == ConsoleButton.DEORBIT && now - desk.guardOpenedAt >= GroundConsoleBlockEntity.GUARD_TICKS) {
                    desk.guardOpenedAt = now;
                    return;
                }
                desk.guardOpenedAt = Long.MIN_VALUE / 2;
                SatelliteControl.apply(c.level().getServer(), s, press.button(), press.up(), now);
            }
        }
    }
}
