package net.createaerospace.client;

import java.util.List;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** A flat map of the world's longitude and latitude. One degree is 1000 blocks, so the text also gives the block position under the satellite. */
public class SatelliteMapScreen extends Screen {
    private final BlockPos console;

    public SatelliteMapScreen(BlockPos console) {
        super(Component.translatable("screen.create_aerospace_engineering.map"));
        this.console = console;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        var level = Minecraft.getInstance().level;
        int w = Math.min(width - 20, (height - 70) * 2), h = w / 2;
        int x0 = (width - w) / 2, y0 = 22;
        g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF9AA0A8);
        g.fill(x0, y0, x0 + w, y0 + h, 0xFF0B1A2E);
        for (int lon = -150; lon <= 150; lon += 30) g.fill(x0 + px(lon, w), y0, x0 + px(lon, w) + 1, y0 + h, lon == 0 ? 0xFF3A5A80 : 0xFF16304F);
        for (int lat = -60; lat <= 60; lat += 30) g.fill(x0, y0 + py(lat, h), x0 + w, y0 + py(lat, h) + 1, lat == 0 ? 0xFF3A5A80 : 0xFF16304F);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (level == null) return;
        double tick = level.getGameTime() + pt;
        GroundConsoleBlockEntity be = level.getBlockEntity(console) instanceof GroundConsoleBlockEntity c ? c : null;
        Satellite sel = be == null ? null : be.selected();
        if (sel != null) {
            for (int i = 1; i <= 60; i++) {
                double[] t = OrbitMath.subpoint(sel, tick + i * sel.periodTicks() / 60.0);
                dot(g, x0, y0, w, h, Math.toDegrees(t[1]), Math.toDegrees(t[0]), 1, 0xFF4A7090);
            }
        }
        mark(g, x0, y0, w, h, OrbitMath.lonOf(console.getX()), OrbitMath.latOf(console.getZ()), 0xFF50FF70, null);
        List<Satellite> all = ClientSatellites.get();
        for (Satellite s : all) {
            double[] p = OrbitMath.subpoint(s, tick);
            mark(g, x0, y0, w, h, Math.toDegrees(p[1]), Math.toDegrees(p[0]), s == sel ? 0xFFFFE060 : 0xFFE0E0E0, s.name());
        }
        int ty = y0 + h + 6;
        if (sel == null) {
            g.drawCenteredString(font, Component.translatable("console.create_aerospace_engineering.none"), width / 2, ty, 0xAAAAAA);
        } else {
            double[] p = OrbitMath.subpoint(sel, tick);
            int[] b = OrbitMath.groundBlock(p);
            g.drawCenteredString(font, Component.translatable("screen.create_aerospace_engineering.selected", sel.name(), OrbitMath.fmtLat(p[0]),
                    OrbitMath.fmtLon(p[1]), b[0], b[1], sel.altKm()), width / 2, ty, 0xFFE060);
        }
        g.drawCenteredString(font, Component.translatable("screen.create_aerospace_engineering.legend"), width / 2, ty + 12, 0x909090);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
