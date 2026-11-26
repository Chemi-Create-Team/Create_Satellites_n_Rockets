package net.createaerospace.client;

import java.util.List;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.ConsoleButton;
import net.createaerospace.client.globe.VoxelGlobe;
import net.createaerospace.compat.cfs.CfsLayout;
import net.createaerospace.compat.cfs.CfsLayout.Rect;
import net.createaerospace.compat.cfs.CfsLayout.View;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteControl;
import net.createaerospace.orbit.SatelliteReadout;
import net.createfrontier.api.computer.client.ComputerCanvasRenderers;
import net.createfrontier.api.computer.client.ComputerCanvasRenderers.Context;
import net.createfrontier.api.computer.client.ComputerCanvasRenderers.Graphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderType;

/**
 * Draws the ground console app of the CFS Control Computer: the controls, or the Earth as a sphere of cubes with the satellites over it. The satellites and the time come from the
 * client's own copy; the frame only says which view, which satellite and until when the deorbit cover is open. Every position of a
 * button is from {@link CfsLayout}. Colors are those of {@link SatelliteMapScreen}. Only loaded when CFS is.
 */
public final class CfsCanvasRenderer {
    private static final String K = "computer.create_aerospace_engineering.";
    private static final int BACKDROP = 0xFF04070D, PANEL = 0xFF08101E, BLUE = 0xFF2F6FD0, BLUE_DARK = 0xFF0C2F66, HOVER = 0xFF1A3A66;
    private static final int AMBER = 0xFFFFB020, GREEN = 0xFF55FF77, YELLOW = 0xFFFFE060, DIM = 0xFF7F93AA, RED = 0xFFFF9B98, OFF = 0xFF344351;

    private CfsCanvasRenderer() {}

    public static void register() {
        ComputerCanvasRenderers.register(CreateAerospace.id("ground_control"), CfsCanvasRenderer::render);
    }

    private static void render(Context c) {
        var g = c.graphics();
        float w = c.width(), h = c.height();
        View view = View.of(c.data().getInt("View"));
        List<Satellite> all = ClientSatellites.get();
        Satellite sel = SatelliteControl.pick(all, c.data().hasUUID("Sel") ? c.data().getUUID("Sel") : (UUID) null);
        double tick = c.gameTime() + c.partialTick();
        String hover = c.pointerX() < 0 ? null : CfsLayout.hit(view, c.pointerX(), c.pointerY());

        g.fill(0, 0, w, h, -.002f, BACKDROP);
        tab(g, w, h, CfsLayout.TAB_CONTROL_RECT, Component.translatable(K + "tab.control"), view == View.CONTROL, CfsLayout.TAB_CONTROL.equals(hover));
        tab(g, w, h, CfsLayout.TAB_MAP_RECT, Component.translatable(K + "tab.map"), view == View.MAP, CfsLayout.TAB_MAP.equals(hover));
        var alert = SatelliteReadout.activeAlert(ClientSatellites.alerts(), c.gameTime() + c.partialTick());
        if (alert != null) {
            // the warning takes the place of the count, blinking
            Rect bar = new Rect(.40f, .03f, .97f, .11f);
            box(g, w, h, bar, -.003f, SatelliteReadout.alertLit(c.gameTime() + c.partialTick()) ? 0xFF5A0A0A : 0xFF2A0606, 0xFFFF4040);
            text(g, w, h, SatelliteReadout.alertLine(alert), bar.l() + .01f, bar.t() + .02f, bar.r() - bar.l() - .02f, .042f, .5f, 0xFFFF7070);
        } else {
            text(g, w, h, Component.translatable("screen.create_aerospace_engineering.count", all.size()), .60f, .045f, .37f, .05f, 1, AMBER);
        }
        if (view == View.CONTROL) controls(c, g, all, sel, tick, hover);
        else map(c, g, all, sel, tick, hover);
    }

    private static void controls(Context c, Graphics g, List<Satellite> all, Satellite sel, double tick, String hover) {
        float w = c.width(), h = c.height();
        Rect st = CfsLayout.STATUS;
        box(g, w, h, st, -.003f, PANEL, BLUE);
        if (sel == null) {
            text(g, w, h, Component.translatable(SatelliteReadout.K + "none"), st.l() + .02f, st.t() + .012f, .9f, .056f, 0, AMBER);
            return;
        }
        var r = SatelliteReadout.of(sel, all.indexOf(sel), all.size(), tick, c.boardPos());
        Component status = r.payloadName() == null ? Component.literal(r.name()) : Component.translatable(K + "status_payload", r.name(), r.payloadName());
        if (r.stateLine() != null) status = Component.translatable(K + "status_state", status, r.stateLine());
        text(g, w, h, status, st.l() + .02f, st.t() + .012f, .9f, .056f, 0, YELLOW);

        Component[] values = {Component.literal(r.number() + " / " + r.count()), Component.literal(r.owner()),
                Component.literal(r.lat() + "  " + r.lon()), Component.literal("X " + r.blockX() + "  Z " + r.blockZ()),
                Component.literal(r.altKm() + " km"), Component.literal(r.inclDeg() + "°"), Component.literal(r.periodMin() + " min"),
                r.modeName(), r.visibility()};
        String[] keys = {"selected", "owner", "position", "block", "altitude", "inclination", "period", "mode", "visibility"};
        int[] colors = {AMBER, AMBER, AMBER, AMBER, AMBER, AMBER, AMBER, SatelliteReadout.modeColor(r.mode()) | 0xFF000000, r.above() ? GREEN : DIM};
        Rect rd = CfsLayout.READINGS;
        float cw = (rd.r() - rd.l() - .04f) / 3, ch = (rd.b() - rd.t() - .04f) / 3;
        for (int i = 0; i < values.length; i++) {
            float left = rd.l() + (i % 3) * (cw + .02f), top = rd.t() + (i / 3) * (ch + .02f);
            box(g, w, h, new Rect(left, top, left + cw, top + ch), -.003f, PANEL, BLUE_DARK);
            text(g, w, h, Component.translatable(K + "reading." + keys[i]), left + .012f, top + .018f, cw - .024f, .032f, 0, DIM);
            text(g, w, h, values[i], left + .012f, top + ch * .46f, cw - .024f, ch * .42f, 0, colors[i]);
        }
        boolean cover = c.gameTime() < c.data().getLong("CoverUntil");
        for (String id : CfsLayout.buttons(View.CONTROL)) {
            var press = SatelliteControl.parse(id);
            boolean guarded = press.button().guarded();
            button(g, w, h, CfsLayout.button(View.CONTROL, id), Component.translatable(K + "button." + id + (guarded && cover ? "_confirm" : "")),
                    guarded ? RED : AMBER, SatelliteControl.enabled(sel, all.size(), press), id.equals(hover));
        }
    }

    private static void map(Context c, Graphics g, List<Satellite> all, Satellite sel, double tick, String hover) {
        float w = c.width(), h = c.height();
        Rect area = CfsLayout.MAP;
        box(g, w, h, area, -.003f, 0xFF03060C, BLUE);
        // the Earth fits a square in the middle of the area; the orbits reach 1.75 Earth radii
        float aw = (area.r() - area.l()) * w, ah = (area.b() - area.t()) * h, size = Math.min(aw, ah) * .96f;
        float x0 = area.l() * w + (aw - size) / 2, y0 = area.t() * h + (ah - size) / 2;
        float k = size / 2f / (VoxelGlobe.N * 1.95f), zs = .2f;
        double homeLat = OrbitMath.latOf(c.boardPos().getZ()), homeLon = OrbitMath.lonOf(c.boardPos().getX());
        // over the board by default; the turn buttons add steps of 45 degrees
        var view = new VoxelGlobe.View(Math.max(-40, Math.min(40, homeLat)), homeLon + 45 * c.data().getInt("Rot"), x0 + size / 2, y0 + size / 2, k);
        var sink = Globes.sink(c.buffers().getBuffer(RenderType.debugQuads()), c.pose().last().pose(), -2.6f * VoxelGlobe.N * k * zs, -zs);
        GlobeScene.draw(sink, view, all, sel, tick, homeLat, homeLon, x0, y0, x0 + size, y0 + size, k * .2f, -2.4f * VoxelGlobe.N * k);
        float lh = size * .035f;
        for (var label : GlobeScene.labels(view, all, sel, tick)) {
            float width = Math.min(size * .3f, x0 + size - label.x() - k * 3);
            if (width > 0) g.text(Component.literal(label.name()), label.x() + k * 3, label.y() - lh * .55f, width, lh * 1.1f, -.3f, 0,
                    label.selected() ? YELLOW : 0xFFE0E0E0);
        }
        for (String id : CfsLayout.buttons(View.MAP)) {
            var press = SatelliteControl.parse(id);
            boolean enabled = press == null || sel != null && SatelliteControl.enabled(sel, all.size(), press);
            button(g, w, h, CfsLayout.button(View.MAP, id), Component.translatable(K + "button." + id), AMBER, enabled, id.equals(hover));
        }
        Rect sum = CfsLayout.MAP_SUMMARY;
        if (sel == null) {
            text(g, w, h, Component.translatable("console.create_aerospace_engineering.none"), sum.l(), sum.t() + .01f, sum.r() - sum.l(), .045f, 0, DIM);
        } else {
            double[] p = OrbitMath.subpoint(sel, tick);
            int[] b = OrbitMath.groundBlock(p);
            text(g, w, h, Component.translatable("screen.create_aerospace_engineering.selected", sel.name(), OrbitMath.fmtLat(p[0]),
                    OrbitMath.fmtLon(p[1]), b[0], b[1], (int) Math.round(OrbitMath.altKm(sel, tick))), sum.l(), sum.t() + .01f, sum.r() - sum.l(), .045f, 0, AMBER);
        }
    }

    private static void box(Graphics g, float w, float h, Rect r, float z, int fill, int border) {
        float e = Math.min(w, h) * .004f;
        g.fill(r.l() * w, r.t() * h, r.r() * w, r.b() * h, z, border);
        g.fill(r.l() * w + e, r.t() * h + e, r.r() * w - e, r.b() * h - e, z - .0005f, fill);
    }

    private static void tab(Graphics g, float w, float h, Rect r, Component label, boolean active, boolean hover) {
        box(g, w, h, r, -.003f, active ? 0xFF1A2A44 : hover ? HOVER : PANEL, active ? AMBER : BLUE);
        text(g, w, h, label, r.l() + .01f, r.t() + .016f, r.r() - r.l() - .02f, .048f, .5f, active ? AMBER : DIM);
    }

    private static void button(Graphics g, float w, float h, Rect r, Component label, int color, boolean enabled, boolean hover) {
        box(g, w, h, r, -.003f, hover && enabled ? HOVER : enabled ? PANEL : 0xFF18212B, enabled ? color : OFF);
        float size = (r.b() - r.t()) * .42f;
        text(g, w, h, label, r.l() + .01f, (r.t() + r.b()) / 2 - size / 2, r.r() - r.l() - .02f, size, .5f, enabled ? color : DIM);
    }

    /** Text in fractions of the canvas. */
    private static void text(Graphics g, float w, float h, Component t, float left, float top, float width, float height, float align, int color) {
        g.text(t, left * w, top * h, width * w, height * h, -.01f, align, color);
    }
}
