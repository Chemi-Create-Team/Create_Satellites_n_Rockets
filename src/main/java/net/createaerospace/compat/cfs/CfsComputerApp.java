package net.createaerospace.compat.cfs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.ConsoleButton;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.client.globe.Spots;
import net.createaerospace.compat.cfs.CfsLayout.View;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteControl;
import net.createaerospace.orbit.SatelliteData;
import net.createaerospace.orbit.SatelliteReadout;
import net.createfrontier.api.computer.ComputerApps;
import net.createfrontier.api.computer.ComputerCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The ground console as a canvas app of the CFS Control Computer (API v2, more with v3), under "Extra apps", with two views: the
 * controls of the console and the cube Earth. The server keeps which view and which satellite a display shows and does what the
 * buttons say; the drawing is {@code client.CfsCanvasRenderer}, from the same {@link CfsLayout}.
 * <p>
 * With CFS API v3 (CFS 0.3.38) the server knows the canvas size, so a click on a satellite's dot on the map selects it (found with
 * {@link CfsLayout#mapView} and {@link Spots}, as the renderer draws them), and each display keeps its view, turn and selection in
 * the state CFS saves with the display, through restarts. With API v2 the state is kept in memory per computer and the map has no
 * clicks.
 * <p>
 * What the controls do comes from {@link SatelliteControl}, the same code the console block uses, and the readings from
 * {@link SatelliteReadout}. When the console gains a button or a line, change it there and in the layout and the renderer.
 */
final class CfsComputerApp implements ComputerCanvas.Application {
    static final String K = "computer." + CreateAerospace.MOD_ID + ".";

    /** The running CFS has API v3: canvas size and saved state. API_VERSION is a compile-time constant, so it is read at run time. */
    static final boolean V3 = apiVersion() >= 3;

    private static int apiVersion() {
        try {
            return ComputerApps.class.getField("API_VERSION").getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 2;
        }
    }

    /** View, turn of the Earth, selection and deorbit cover of one display. */
    static final class Desk {
        View view = View.CONTROL;
        /** The Earth on the map, turned by this many steps of 45 degrees. */
        int rot;
        UUID selected;
        /** The guarded button that was pressed once and waits for its second press, and when. */
        ConsoleButton armed;
        long guardOpenedAt = Long.MIN_VALUE / 2;
        /** With API v2: the last game tick the computer showed or used the app; a desk left alone for {@link #FORGET_TICKS} is forgotten. */
        long seen;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("View", view.ordinal());
            t.putInt("Rot", rot);
            if (selected != null) t.putUUID("Sel", selected);
            if (armed != null) {
                t.putString("Armed", armed.name());
                t.putLong("GuardOpened", guardOpenedAt);
            }
            return t;
        }

        static Desk load(CompoundTag t) {
            Desk d = new Desk();
            d.view = View.of(t.getInt("View"));
            d.rot = Math.floorMod(t.getInt("Rot"), 8);
            d.selected = t.hasUUID("Sel") ? t.getUUID("Sel") : null;
            for (ConsoleButton b : ConsoleButton.values()) if (b.name().equals(t.getString("Armed"))) d.armed = b;
            if (d.armed != null) d.guardOpenedAt = t.getLong("GuardOpened");
            return d;
        }
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    /** A Minecraft day. With API v2, a computer that was taken down or never opened again does not stay in memory for good. */
    private static final long FORGET_TICKS = 24000;

    private final Map<Key, Desk> desks = new HashMap<>();
    private long pruned;

    static void register() {
        ComputerApps.register(CreateAerospace.id("ground_control"), new CfsComputerApp());
    }

    @Override
    public Component title() {
        return Component.translatable(K + "title");
    }

    /** The desk of this display: from the state CFS saves (API v3), or from memory per computer (API v2). */
    private Desk desk(ComputerCanvas.Context c) {
        if (V3) return Desk.load(c.state().get());
        long now = c.level().getGameTime();
        if (now - pruned >= 1200 || now < pruned) {
            pruned = now;
            desks.values().removeIf(d -> now - d.seen > FORGET_TICKS);
        }
        Desk desk = desks.computeIfAbsent(new Key(c.level().dimension(), c.computerPos().immutable()), k -> new Desk());
        desk.seen = now;
        return desk;
    }

    /** Keeps a changed desk. In memory it already is; CFS saves the state only when it changed. */
    private static void keep(ComputerCanvas.Context c, Desk desk) {
        if (V3) c.state().set(desk.save());
    }

    /** Only what changes when somebody presses something; the satellites and the time are the client's own. */
    @Override
    public ComputerCanvas.Frame canvas(ComputerCanvas.Context c) {
        Desk desk = desk(c);
        CompoundTag data = new CompoundTag();
        data.putInt("View", desk.view.ordinal());
        data.putInt("Rot", desk.rot);
        Satellite s = SatelliteControl.pick(SatelliteData.get(c.level().getServer()).list(), desk.selected);
        if (s != null) data.putUUID("Sel", s.id());
        data.putLong("CoverUntil", Math.max(0, desk.guardOpenedAt + GroundConsoleBlockEntity.GUARD_TICKS));
        if (desk.armed != null) data.putString("Armed", desk.armed.label());
        return new ComputerCanvas.Frame(ComputerCanvas.Presentation.WINDOWED, data);
    }

    @Override
    public void click(ComputerCanvas.Context c, ServerPlayer player, ComputerCanvas.Frame displayed, double x, double y) {
        // the buttons are found in the view the player was looking at
        View shown = View.of(displayed.data().getInt("View"));
        String hit = CfsLayout.hit(shown, x, y);
        Desk desk = desk(c);
        if (hit == null) {
            if (shown == View.MAP && V3) clickMap(c, desk, displayed, x, y);
            return;
        }
        switch (hit) {
            case CfsLayout.TAB_CONTROL -> desk.view = View.CONTROL;
            case CfsLayout.TAB_MAP -> desk.view = View.MAP;
            case CfsLayout.ROTATE_LEFT -> desk.rot = Math.floorMod(desk.rot + 1, 8);
            case CfsLayout.ROTATE_RIGHT -> desk.rot = Math.floorMod(desk.rot - 1, 8);
            default -> press(c, player, desk, SatelliteControl.parse(hit));
        }
        keep(c, desk);
    }

    /**
     * A click on the globe selects the satellite whose dot is there, as the map was drawn: the canvas size from CFS, the turn of the
     * frame the player saw. Selecting is what the arrow buttons do, so anybody may.
     */
    private static void clickMap(ComputerCanvas.Context c, Desk desk, ComputerCanvas.Frame displayed, double x, double y) {
        float w = c.width(), h = c.height(displayed.presentation());
        BlockPos board = c.boardPos();
        var map = CfsLayout.mapView(w, h, OrbitMath.latOf(board.getZ()), OrbitMath.lonOf(board.getX()), displayed.data().getInt("Rot"));
        if (!map.contains(x * w, y * h)) return;
        List<Satellite> all = SatelliteData.get(c.level().getServer()).list();
        Satellite s = Spots.pick(map.view(), all, c.level().getGameTime(), x * w, y * h, map.reach());
        if (s == null) return;
        desk.selected = s.id();
        keep(c, desk);
    }

    private static void press(ComputerCanvas.Context c, ServerPlayer player, Desk desk, SatelliteControl.Press press) {
        List<Satellite> all = SatelliteData.get(c.level().getServer()).list();
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
                // reentry and abandoning ask once more: the first click arms that button, a second click on the same button within
                // its time does it (a click on the other one only arms the other one)
                if (press.button().guarded() && (press.button() != desk.armed || now - desk.guardOpenedAt >= GroundConsoleBlockEntity.GUARD_TICKS)) {
                    desk.armed = press.button();
                    desk.guardOpenedAt = now;
                    return;
                }
                desk.armed = null;
                desk.guardOpenedAt = Long.MIN_VALUE / 2;
                SatelliteControl.apply(c.level().getServer(), s, press.button(), press.up(), now, c.boardPos());
            }
        }
    }
}
