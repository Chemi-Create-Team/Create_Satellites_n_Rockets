package net.createaerospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.UUID;
import net.createaerospace.block.ConsoleSelect;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.client.globe.CubeEarth;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteReadout;
import net.createaerospace.photo.PhotoPackets;
import net.createaerospace.relay.RelayStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A mission control wall: the Earth as a cube of 8 by 8 tiles a face under drifting clouds, with the satellites as dots and their
 * orbits as circles, in the middle (drag to turn it), the list of
 * satellites on the left and the readout of the selected one on the right. One degree is 1000 blocks, so the readout also gives the
 * block position under the satellite. The land comes from {@code textures/gui/land_mask.png}, Natural Earth, public domain.
 */
public class SatelliteMapScreen extends Screen {
    private static final int BACKDROP = 0xFF04070D, PANEL = 0xFF08101E, BLUE = 0xFF2F6FD0, BLUE_DARK = 0xFF0C2F66;
    private static final int AMBER = 0xFFFFB020, GREEN = 0xFF55FF77, YELLOW = 0xFFFFE060, DIM = 0xFF7F93AA;
    private static final int MARGIN = 6, BAR = 14, LEFT = 86, RIGHT = 100, GAP = 4, ROW = 20;
    private final BlockPos console;
    /** The point of the Earth in the middle, in degrees. Starts over the console. */
    private double viewLat, viewLon;
    private Button photo;
    /** Where the globe and the list were last drawn, for the mouse. */
    private CubeEarth.View lastView;
    private int globeX, globeY, globeSize, listX, listY, listW, listRows, listFirst;
    private double lastTick;
    /** Rows the list is scrolled away from the selected satellite; back to 0 when the selection changes. */
    private int scroll;
    private UUID scrolledFrom;
    /** A press on the globe that has not turned into a drag selects the dot under it when released. */
    private boolean pressedOnGlobe;
    private double dragged;

    public SatelliteMapScreen(BlockPos console) {
        super(Component.translatable("screen.create_aerospace_engineering.map"));
        this.console = console;
        viewLat = Math.max(-40, Math.min(40, OrbitMath.latOf(console.getZ())));
        viewLon = OrbitMath.lonOf(console.getX());
    }

    BlockPos console() {
        return console;
    }

    @Override
    protected void init() {
        photo = addRenderableWidget(Button.builder(Component.translatable("photo.create_aerospace_engineering.take"), b -> {
            if (minecraft.level != null && minecraft.level.getBlockEntity(console) instanceof GroundConsoleBlockEntity c
                    && c.selected() != null) {
                PacketDistributor.sendToServer(new PhotoPackets.Request(console, c.selected().id()));
            }
        }).bounds((width - 108) / 2, height - 22, 108, 18).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, BACKDROP);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        var level = Minecraft.getInstance().level;
        boolean sides = width >= 380;
        int leftW = sides ? LEFT : 0, rightW = sides ? RIGHT : 0, gap = sides ? GAP : 0;
        int areaX = MARGIN + leftW + gap, areaW = width - 2 * MARGIN - leftW - rightW - 2 * gap;
        int top = MARGIN + BAR + 4;
        int size = Math.max(48, Math.min(areaW - 8, height - top - 65)) & ~1;
        top += Math.max(0, (height - MARGIN - (top + size + 65)) / 2);
        int x0 = areaX + (areaW - size) / 2, y0 = top + 3;

        // title bar with the clock and the satellite count in amber, like the counters over a mission control wall
        g.fill(MARGIN, MARGIN, width - MARGIN, MARGIN + BAR, PANEL);
        g.fill(MARGIN, MARGIN + BAR - 1, width - MARGIN, MARGIN + BAR, BLUE);
        g.drawString(font, title, MARGIN + 5, MARGIN + 3, AMBER, false);
        List<Satellite> all = ClientSatellites.get();
        Component count = Component.translatable("screen.create_aerospace_engineering.count", all.size());
        int right = width - MARGIN - 5;
        if (level != null) {
            long t = level.getDayTime();
            Component clock = Component.translatable("screen.create_aerospace_engineering.clock", t / 24000 + 1,
                    String.format("%02d:%02d", (t / 1000 + 6) % 24, t % 1000 * 60 / 1000));
            right -= font.width(clock);
            g.drawString(font, clock, right, MARGIN + 3, AMBER, false);
            right -= 12;
        }
        g.drawString(font, count, right - font.width(count), MARGIN + 3, AMBER, false);

        frame(g, x0 - 3, y0 - 3, size + 6, size + 6);
        g.fill(x0, y0, x0 + size, y0 + size, 0xFF03060C);
        if (level == null) return;

        double tick = level.getGameTime() + pt;
        GroundConsoleBlockEntity be = level.getBlockEntity(console) instanceof GroundConsoleBlockEntity c ? c : null;
        Satellite sel = be == null ? null : be.selected();

        // the globe: quads with a depth, kept between z 40 and z 160 so that the names (z 250) stay on top, clipped to the frame
        float k = size / 2f / CubeEarth.FIT;
        var view = new CubeEarth.View(viewLat, viewLon, x0 + size / 2f, y0 + size / 2f, k);
        lastView = view;
        lastTick = tick;
        globeX = x0;
        globeY = y0;
        globeSize = size;
        Satellite hovered = onGlobe(mx, my) ? GlobeScene.pick(view, all, tick, mx, my, 7) : null;
        g.enableScissor(x0, y0, x0 + size, y0 + size);
        var sink = Globes.sink(g.bufferSource().getBuffer(RenderType.debugQuads()), g.pose().last().pose(), 100, 60 / (CubeEarth.DEPTH * k));
        GlobeScene.draw(sink, view, all, sel, tick, OrbitMath.latOf(console.getZ()), OrbitMath.lonOf(console.getX()),
                x0, y0, x0 + size, y0 + size, Math.max(.5f, size / 400f), -CubeEarth.DEPTH * k);
        g.flush();
        // with many satellites only the names that do not cover each other: the selected one and the one under the pointer first
        for (var label : GlobeScene.labels(view, all, sel, hovered, tick, font::width, 6, x0 + size, 9, 24)) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0, 250);
            g.drawString(font, label.name(), (int) label.x(), (int) label.y() - 4, label.selected() ? YELLOW : 0xFFE0E0E0, true);
            ps.popPose();
        }
        g.disableScissor();

        // a recent launch failure or reentry blinks over the top of the globe
        var alert = SatelliteReadout.activeAlert(ClientSatellites.alerts(), tick);
        if (alert != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 260);
            g.fill(x0, y0, x0 + size, y0 + 13, SatelliteReadout.alertLit(tick) ? 0xD0400808 : 0xA0200404);
            fit(g, SatelliteReadout.alertLine(alert), x0 + 3, y0 + 3, size - 6, 0xFFFF6060, true);
            g.pose().popPose();
        }

        // under the globe: the selected satellite on one line, then the legend
        int ty = y0 + size + 7;
        if (sel == null) {
            g.drawCenteredString(font, Component.translatable("console.create_aerospace_engineering.none"), x0 + size / 2, ty, DIM);
        } else {
            double[] p = OrbitMath.subpoint(sel, tick);
            int[] b = OrbitMath.groundBlock(p);
            fit(g, Component.translatable("screen.create_aerospace_engineering.selected", sel.name(), OrbitMath.fmtLat(p[0]),
                    OrbitMath.fmtLon(p[1]), b[0], b[1], (int) Math.round(OrbitMath.altKm(sel, tick))), areaX, ty, areaW, AMBER, true);
        }
        fit(g, Component.translatable("screen.create_aerospace_engineering.legend"), areaX, ty + 11, areaW, DIM, true);
        fit(g, Component.translatable("screen.create_aerospace_engineering.legend.orbits"), areaX, ty + 21, areaW, DIM, true);

        // lit only when the server would take the photo: a working relay of this player, beaconing, above the console's horizon
        var me = minecraft.player;
        photo.active = !RelayStorage.retired(sel) && RelayStorage.isRelay(sel) && sel.mode() != Satellite.STANDBY && me != null
                && sel.controlledBy(me.getUUID(), me.hasPermissions(2))
                && OrbitMath.lookFromBlock(sel, tick, console.getX() + .5, console.getZ() + .5).elevation() > 0;
        photo.render(g, mx, my, pt);
        if (!sides) return;
        // left: every satellite, the selected one lit
        int py = y0 - 3, ph = size + 6;
        frame(g, MARGIN, py, leftW, ph);
        g.drawString(font, Component.translatable("screen.create_aerospace_engineering.panel.list"), MARGIN + 5, py + 5, AMBER, false);
        // the rows round the selected satellite, scrolled with the wheel; a chip in the colour of its orbit, a click selects
        int rows = Math.max(1, (ph - 20) / ROW), index = all.indexOf(sel);
        UUID selId = sel == null ? null : sel.id();
        if (!java.util.Objects.equals(selId, scrolledFrom)) {
            scroll = 0;
            scrolledFrom = selId;
        }
        int centred = index - rows / 2, first = Math.max(0, Math.min(all.size() - rows, centred + scroll));
        scroll = first - centred;
        listX = MARGIN;
        listY = py + 18;
        listW = leftW;
        listRows = rows;
        listFirst = first;
        for (int i = first; i < Math.min(all.size(), first + rows); i++) {
            Satellite s = all.get(i);
            int y = py + 18 + (i - first) * ROW;
            if (s == sel) g.fill(MARGIN + 2, y - 1, MARGIN + leftW - 2, y + ROW - 3, 0xFF1A2A44);
            else if (mx >= MARGIN && mx < MARGIN + leftW && my >= y - 1 && my < y + ROW - 3) g.fill(MARGIN + 2, y - 1, MARGIN + leftW - 2, y + ROW - 3, 0xFF101C30);
            g.fill(MARGIN + 5, y + 1, MARGIN + 9, y + 5, GlobeScene.color(s, tick));
            double[] p = OrbitMath.subpoint(s, tick);
            fit(g, Component.literal((i + 1) + " " + s.name()), MARGIN + 11, y, leftW - 18, s == sel ? YELLOW : AMBER, false);
            fit(g, Component.literal(OrbitMath.fmtLat(p[0]) + " " + OrbitMath.fmtLon(p[1])), MARGIN + 11, y + 9, leftW - 18, DIM, false);
        }
        if (all.size() > rows) {
            // where the rows shown are in the whole list
            int barTop = py + 18, bottom = py + ph - 4, span = bottom - barTop;
            int t0 = barTop + span * first / all.size(), t1 = Math.max(t0 + 3, barTop + span * (first + rows) / all.size());
            g.fill(MARGIN + leftW - 5, barTop, MARGIN + leftW - 3, bottom, BLUE_DARK);
            g.fill(MARGIN + leftW - 5, t0, MARGIN + leftW - 3, t1, DIM);
        }
        // right: the readout, the same lines as the screen of the console
        int rx = width - MARGIN - rightW;
        frame(g, rx, py, rightW, ph);
        g.drawString(font, Component.translatable("screen.create_aerospace_engineering.panel.selected"), rx + 5, py + 5, AMBER, false);
        List<Component> lines = sel == null ? SatelliteReadout.noneLines()
                : SatelliteReadout.of(sel, index, all.size(), tick, console).lines();
        for (int i = 0; i < lines.size(); i++) fit(g, lines.get(i), rx + 5, py + 19 + i * 11, rightW - 10, GREEN, false);
        if (sel != null) g.fill(rx + 5, py + ph - 9, rx + rightW - 5, py + ph - 6, SatelliteReadout.modeColor(sel.mode()) | 0xFF000000);
    }

    /** Dragging turns the Earth under the finger. */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        viewLon -= dx * .6;
        viewLat = Math.max(-85, Math.min(85, viewLat + dy * .6));
        dragged += Math.abs(dx) + Math.abs(dy);
        return true;
    }

    private boolean onGlobe(double mx, double my) {
        return lastView != null && mx >= globeX && mx < globeX + globeSize && my >= globeY && my < globeY + globeSize;
    }

    private boolean onList(double mx, double my) {
        return listRows > 0 && mx >= listX && mx < listX + listW && my >= listY && my < listY + listRows * ROW;
    }

    /** A click on a line of the list selects that satellite; a press on the globe may become a click on a dot or a drag. */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        if (onList(mx, my)) {
            List<Satellite> all = ClientSatellites.get();
            int i = listFirst + (int) ((my - listY) / ROW);
            if (i >= 0 && i < all.size()) select(all.get(i));
            return true;
        }
        pressedOnGlobe = onGlobe(mx, my);
        dragged = 0;
        return pressedOnGlobe;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && pressedOnGlobe) {
            pressedOnGlobe = false;
            if (dragged < 3 && onGlobe(mx, my)) {
                Satellite s = GlobeScene.pick(lastView, ClientSatellites.get(), lastTick, mx, my, 7);
                if (s != null) select(s);
                return true;
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    /** The wheel scrolls the list, three lines at a time. */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (onList(mx, my) && scrollY != 0) {
            scroll -= (int) Math.signum(scrollY) * 3;
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void select(Satellite s) {
        PacketDistributor.sendToServer(new ConsoleSelect(console, s.id()));
    }

    /** A blue-framed screen. */
    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BLUE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BLUE_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
    }

    /** Squeezes a line that would run past its panel instead of guessing at string lengths in every language. */
    private void fit(GuiGraphics g, Component c, int x, int y, int maxWidth, int color, boolean center) {
        float scale = Math.min(1f, maxWidth / (float) Math.max(1, font.width(c)));
        float shift = center ? (maxWidth - font.width(c) * scale) / 2 : 0;
        g.pose().pushPose();
        g.pose().translate(x + shift, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
