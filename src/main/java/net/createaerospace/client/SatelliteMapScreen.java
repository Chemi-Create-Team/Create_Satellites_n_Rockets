package net.createaerospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.client.globe.VoxelGlobe;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteReadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * A mission control wall: the Earth as a sphere of cubes with the satellites over it in the middle (drag to turn it), the list of
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

    public SatelliteMapScreen(BlockPos console) {
        super(Component.translatable("screen.create_aerospace_engineering.map"));
        this.console = console;
        viewLat = Math.max(-40, Math.min(40, OrbitMath.latOf(console.getZ())));
        viewLon = OrbitMath.lonOf(console.getX());
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
        int size = Math.max(60, Math.min(areaW - 8, height - top - 44)) & ~1;
        top += Math.max(0, (height - MARGIN - (top + size + 44)) / 2);
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

        // the globe: quads with a depth, kept between z 35 and z 165 so that the names (z 250) stay on top
        float k = size / 2f / (VoxelGlobe.N * 1.95f);
        var view = new VoxelGlobe.View(viewLat, viewLon, x0 + size / 2f, y0 + size / 2f, k);
        var sink = Globes.sink(g.bufferSource().getBuffer(RenderType.debugQuads()), g.pose().last().pose(), 100, .5f);
        GlobeScene.draw(sink, view, all, sel, tick, OrbitMath.latOf(console.getZ()), OrbitMath.lonOf(console.getX()),
                x0, y0, x0 + size, y0 + size, Math.max(.5f, size / 400f), -VoxelGlobe.N * k * 2.4f);
        g.flush();
        g.enableScissor(x0, y0, x0 + size, y0 + size);
        for (var label : GlobeScene.labels(view, all, sel, tick)) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0, 250);
            g.drawString(font, label.name(), Math.min((int) label.x() + 6, x0 + size - font.width(label.name())), (int) label.y() - 4, label.selected() ? YELLOW : 0xFFE0E0E0, true);
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

        if (!sides) return;
        // left: every satellite, the selected one lit
        int py = y0 - 3, ph = size + 6;
        frame(g, MARGIN, py, leftW, ph);
        g.drawString(font, Component.translatable("screen.create_aerospace_engineering.panel.list"), MARGIN + 5, py + 5, AMBER, false);
        int rows = Math.max(1, (ph - 20) / ROW), index = all.indexOf(sel);
        int first = Math.max(0, Math.min(all.size() - rows, index - rows / 2));
        for (int i = first; i < Math.min(all.size(), first + rows); i++) {
            Satellite s = all.get(i);
            int y = py + 18 + (i - first) * ROW;
            if (s == sel) g.fill(MARGIN + 2, y - 1, MARGIN + leftW - 2, y + ROW - 3, 0xFF1A2A44);
            double[] p = OrbitMath.subpoint(s, tick);
            fit(g, Component.literal((i + 1) + " " + s.name()), MARGIN + 5, y, leftW - 10, s == sel ? YELLOW : AMBER, false);
            fit(g, Component.literal(OrbitMath.fmtLat(p[0]) + " " + OrbitMath.fmtLon(p[1])), MARGIN + 5, y + 9, leftW - 10, DIM, false);
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
        return true;
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
