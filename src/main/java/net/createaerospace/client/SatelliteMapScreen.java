package net.createaerospace.client;

import java.util.List;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteReadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A mission control wall: the world map with the ground tracks in the middle, the list of satellites on the left and the readout of the
 * selected one on the right. One degree is 1000 blocks, so the readout also gives the block position under the satellite.
 * The land is Natural Earth 1:110m (public domain), drawn into {@code textures/gui/world_map.png}, 512 x 256, equirectangular.
 */
public class SatelliteMapScreen extends Screen {
    private static final ResourceLocation WORLD = CreateAerospace.id("textures/gui/world_map.png");
    private static final int BACKDROP = 0xFF04070D, PANEL = 0xFF08101E, BLUE = 0xFF2F6FD0, BLUE_DARK = 0xFF0C2F66;
    private static final int OCEAN = 0xFF071A33, GRID = 0xFF123A66, GRID_MAIN = 0xFF2A64A8;
    private static final int AMBER = 0xFFFFB020, GREEN = 0xFF55FF77, YELLOW = 0xFFFFE060, DIM = 0xFF7F93AA;
    private static final int MARGIN = 6, BAR = 14, LEFT = 86, RIGHT = 100, GAP = 4, ROW = 20;
    private final BlockPos console;

    public SatelliteMapScreen(BlockPos console) {
        super(Component.translatable("screen.create_aerospace_engineering.map"));
        this.console = console;
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
        int mapY = MARGIN + BAR + 7;
        int w = Math.max(60, Math.min(areaW - 8, (height - mapY - 38) * 2)) & ~1, h = w / 2;
        // a wide screen leaves room under the map: centre the wall between the title bar and the bottom edge
        mapY += Math.max(0, (height - MARGIN - (mapY + h + 36)) / 2);
        int x0 = areaX + (areaW - w) / 2;

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

        frame(g, x0 - 3, mapY - 3, w + 6, h + 6);
        g.fill(x0, mapY, x0 + w, mapY + h, OCEAN);
        for (int lon = -150; lon <= 150; lon += 30) g.fill(x0 + px(lon, w), mapY, x0 + px(lon, w) + 1, mapY + h, lon == 0 ? GRID_MAIN : GRID);
        for (int lat = -60; lat <= 60; lat += 30) g.fill(x0, mapY + py(lat, h), x0 + w, mapY + py(lat, h) + 1, lat == 0 ? GRID_MAIN : GRID);
        g.blit(WORLD, x0, mapY, w, h, 0, 0, 512, 256, 512, 256);
        for (int lon = -150; lon <= 150; lon += 30) small(g, Integer.toString(lon), x0 + px(lon, w) + 2, mapY + h + 5, DIM);
        for (int lat = -60; lat <= 60; lat += 30) small(g, Integer.toString(lat), x0 + 2, mapY + py(lat, h) - 6, DIM);
        if (level == null) return;

        double tick = level.getGameTime() + pt;
        GroundConsoleBlockEntity be = level.getBlockEntity(console) instanceof GroundConsoleBlockEntity c ? c : null;
        Satellite sel = be == null ? null : be.selected();
        for (Satellite s : all) if (s != sel) track(g, s, tick, x0, mapY, w, h, 80, 0, 0x66B8C8E0, 0);
        if (sel != null) track(g, sel, tick, x0, mapY, w, h, 240, 0.15, YELLOW, 0xAA4A7090);
        mark(g, x0, mapY, w, h, OrbitMath.lonOf(console.getX()), OrbitMath.latOf(console.getZ()), 0xFF50FF70, null);
        for (Satellite s : all) {
            double[] p = OrbitMath.subpoint(s, tick);
            mark(g, x0, mapY, w, h, Math.toDegrees(p[1]), Math.toDegrees(p[0]), s == sel ? YELLOW : 0xFFE0E0E0, s.name());
        }

        // under the map: the selected satellite on one line, then the legend
        int ty = mapY + h + 14;
        if (sel == null) {
            g.drawCenteredString(font, Component.translatable("console.create_aerospace_engineering.none"), x0 + w / 2, ty, DIM);
        } else {
            double[] p = OrbitMath.subpoint(sel, tick);
            int[] b = OrbitMath.groundBlock(p);
            fit(g, Component.translatable("screen.create_aerospace_engineering.selected", sel.name(), OrbitMath.fmtLat(p[0]),
                    OrbitMath.fmtLon(p[1]), b[0], b[1], sel.altKm()), x0, ty, w, AMBER, true);
        }
        fit(g, Component.translatable("screen.create_aerospace_engineering.legend"), x0, ty + 11, w, DIM, true);

        if (!sides) return;
        // left: every satellite, the selected one lit
        int top = mapY - 3, ph = h + 6;
        frame(g, MARGIN, top, leftW, ph);
        g.drawString(font, Component.translatable("screen.create_aerospace_engineering.panel.list"), MARGIN + 5, top + 5, AMBER, false);
        int rows = Math.max(1, (ph - 20) / ROW), index = all.indexOf(sel);
        int first = Math.max(0, Math.min(all.size() - rows, index - rows / 2));
        for (int i = first; i < Math.min(all.size(), first + rows); i++) {
            Satellite s = all.get(i);
            int y = top + 18 + (i - first) * ROW;
            if (s == sel) g.fill(MARGIN + 2, y - 1, MARGIN + leftW - 2, y + ROW - 3, 0xFF1A2A44);
            double[] p = OrbitMath.subpoint(s, tick);
            fit(g, Component.literal((i + 1) + " " + s.name()), MARGIN + 5, y, leftW - 10, s == sel ? YELLOW : AMBER, false);
            fit(g, Component.literal(OrbitMath.fmtLat(p[0]) + " " + OrbitMath.fmtLon(p[1])), MARGIN + 5, y + 9, leftW - 10, DIM, false);
        }
        // right: the readout, the same lines as the screen of the console
        int rx = width - MARGIN - rightW;
        frame(g, rx, top, rightW, ph);
        g.drawString(font, Component.translatable("screen.create_aerospace_engineering.panel.selected"), rx + 5, top + 5, AMBER, false);
        List<Component> lines = sel == null ? SatelliteReadout.noneLines()
                : SatelliteReadout.of(sel, index, all.size(), tick, console).lines();
        for (int i = 0; i < lines.size(); i++) fit(g, lines.get(i), rx + 5, top + 19 + i * 11, rightW - 10, GREEN, false);
        if (sel != null) g.fill(rx + 5, top + ph - 9, rx + rightW - 5, top + ph - 6, SatelliteReadout.modeColor(sel.mode()) | 0xFF000000);
    }

    /** A blue-framed screen. */
    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BLUE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BLUE_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
    }

    private static int px(double lon, int w) {
        return (int) ((lon + 180) / 360 * w);
    }

    private static int py(double lat, int h) {
        return (int) ((90 - lat) / 180 * h);
    }

    private static void dot(GuiGraphics g, int x0, int y0, int w, int h, double lon, double lat, int r, int color) {
        int x = x0 + Math.min(w - 1, px(lon, w)), y = y0 + Math.min(h - 1, py(lat, h));
        g.fill(x - r + 1, y - r + 1, x + r, y + r, color);
    }

    /** The ground track: from a little before now to one orbit ahead. The Earth turns under it, so it is not a closed curve. */
    private static void track(GuiGraphics g, Satellite s, double tick, int x0, int y0, int w, int h, int steps, double before, int ahead, int behind) {
        for (int i = (int) -Math.round(before * steps); i <= steps; i++) {
            double[] t = OrbitMath.subpoint(s, tick + i * s.periodTicks() / (double) steps);
            dot(g, x0, y0, w, h, Math.toDegrees(t[1]), Math.toDegrees(t[0]), 1, i < 0 ? behind : ahead);
        }
    }

    private void mark(GuiGraphics g, int x0, int y0, int w, int h, double lon, double lat, int color, String label) {
        dot(g, x0, y0, w, h, lon, lat, 2, color);
        int x = x0 + px(lon, w), y = y0 + py(lat, h);
        if (label == null) {
            g.fill(x - 4, y, x + 5, y + 1, color);
            g.fill(x, y - 4, x + 1, y + 5, color);
        } else {
            g.drawString(font, label, Math.min(x + 4, x0 + w - font.width(label)), y - 4, color);
        }
    }

    /** Half-size text, for the axis. */
    private void small(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(.6f, .6f, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
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
