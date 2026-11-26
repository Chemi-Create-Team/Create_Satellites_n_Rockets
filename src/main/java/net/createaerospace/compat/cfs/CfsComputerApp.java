package net.createaerospace.compat.cfs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.ConsoleButton;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.compat.cfs.CfsLayout.View;
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
 * The ground console as a canvas app of the CFS Control Computer (API v2), under "Extra apps", with two views: the controls of the
 * console and the Earth as a sphere of cubes. The server keeps which view and which satellite a computer shows and does what the buttons say; the
 * drawing is {@code client.CfsCanvasRenderer}, from the same {@link CfsLayout}.
 * <p>
 * What the controls do comes from {@link SatelliteControl}, the same code the console block uses, and the readings from
 * {@link SatelliteReadout}. When the console gains a button or a line, change it there and in the layout and the renderer.
 */
final class CfsComputerApp implements ComputerCanvas.Application {
    static final String K = "computer." + CreateAerospace.MOD_ID + ".";

    /** View, turn of the Earth, selection and deorbit cover of each computer. ponytail: kept in memory, so a restart goes back to the control view and the first satellite. */
    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final class Desk {
        View view = View.CONTROL;
        /** The Earth on the map, turned by this many steps of 45 degrees. */
        int rot;
        UUID selected;
        /** The guarded button that was pressed once and waits for its second press, and when. */
        ConsoleButton armed;
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

    private Desk desk(ComputerCanvas.Context c) {
        return desks.computeIfAbsent(new Key(c.level().dimension(), c.computerPos().immutable()), k -> new Desk());
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
        String hit = CfsLayout.hit(View.of(displayed.data().getInt("View")), x, y);
        if (hit == null) return;
        Desk desk = desk(c);
        switch (hit) {
            case CfsLayout.TAB_CONTROL -> desk.view = View.CONTROL;
            case CfsLayout.TAB_MAP -> desk.view = View.MAP;
            case CfsLayout.ROTATE_LEFT -> desk.rot = Math.floorMod(desk.rot + 1, 8);
            case CfsLayout.ROTATE_RIGHT -> desk.rot = Math.floorMod(desk.rot - 1, 8);
            default -> press(c, player, desk, SatelliteControl.parse(hit));
        }
    }

    private void press(ComputerCanvas.Context c, ServerPlayer player, Desk desk, SatelliteControl.Press press) {
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
