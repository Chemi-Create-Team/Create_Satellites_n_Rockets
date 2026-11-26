package net.createaerospace.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Satellites above the horizon at night: a bright point that draws a fading trail behind it. The point follows the satellite's real
 * position over the player's place on the map, so a pass takes about as long as the orbit says.
 * <p>
 * A satellite burning up on reentry is a fireball, by day as well: a white-hot head in a yellow and orange glow, a trail cooling to
 * red behind it, and near the end the pieces it breaks into, each with a trail of its own. It is seen from wherever it is above the
 * horizon, at the size its distance gives.
 */
public final class NightSky {
    private static final float DISTANCE = 100;
    private static final int TRAIL_POINTS = 20;
    private static final double TRAIL_STEP_TICKS = 3;

    private NightSky() {}

    /** 0 in daylight and under rain, 1 on a clear night. */
    static float darkness(float starBrightness, float rain) {
        return Math.min(1f, starBrightness * 2f) * (1f - rain);
    }

    public static void render(RenderLevelStageEvent e) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || ClientSatellites.get().isEmpty()) return;
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float dark = darkness(level.getStarBrightness(partial), level.getRainLevel(partial));
        var cam = e.getCamera().getPosition();
        double tick = level.getGameTime() + partial;
        Matrix4f m = e.getModelViewMatrix();
        boolean fire = false;
        for (Satellite s : ClientSatellites.get()) fire |= s.burning(tick);
        if (fire) fireballs(m, tick, cam.x, cam.z, dark, level.getRainLevel(partial));
        if (dark < 0.05f) return;

        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Satellite s : ClientSatellites.get()) {
            if (s.burning(tick)) continue;
            var now = OrbitMath.lookFromBlock(s, tick, cam.x, cam.z);
            if (now.elevation() < -0.05) continue;
            float glow = switch (s.mode()) {
                case Satellite.FLASH -> (float) (0.45 + 0.55 * Math.sin(tick * 0.9));
                case Satellite.BEACON -> 1f;
                default -> 0.35f;
            };
            float size = s.mode() == Satellite.STANDBY ? 0.7f : 1.3f;
            // the trail is a ribbon through the recent positions, thinner and fainter the older it is
            Vector3f prev = null;
            float prevWidth = 0, prevAlpha = 0;
            for (int k = 0; k <= TRAIL_POINTS; k++) {
                var l = k == 0 ? now : OrbitMath.lookFromBlock(s, tick - k * TRAIL_STEP_TICKS, cam.x, cam.z);
                if (l.elevation() < 0) { prev = null; continue; }
                double[] dir = OrbitMath.direction(l);
                Vector3f p = new Vector3f((float) dir[0], (float) dir[1], (float) dir[2]).normalize(DISTANCE);
                float fade = 1f - (float) k / (TRAIL_POINTS + 1);
                float width = size * 0.45f * (0.25f + 0.75f * fade), alpha = dark * glow * 0.8f * fade * fade;
                if (prev != null) { ribbon(b, m, prev, p, prevWidth, width, prevAlpha, alpha); any = true; }
                prev = p; prevWidth = width; prevAlpha = alpha;
            }
            point(b, m, OrbitMath.direction(now), size, dark * glow);
            any = true;
        }
        if (any) BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static void fireballs(Matrix4f m, double tick, double camX, double camZ, float dark, float rain) {
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // additive: the glow brightens the sky behind it, by day too
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Satellite s : ClientSatellites.get()) {
            if (!s.burning(tick)) continue;
            var head = OrbitMath.lookFromBlock(s, tick, camX, camZ);
            if (head.elevation() < -0.02) continue;
            double f = OrbitMath.fireProgress(s, tick);
            // flares up, burns at full strength, breaks up and flares again, then goes out
            double envelope = Math.min(1, f / 0.12) * Math.min(1, (1 - f) / 0.12) * (f > 0.62 && f < 0.72 ? 1.35 : 1);
            float flicker = (float) (0.85 + 0.15 * Math.sin(tick * 1.7 + s.id().hashCode()));
            float light = (float) envelope * flicker * (0.85f + 0.15f * dark) * (1 - 0.5f * rain);
            float size = (float) Math.max(0.7, Math.min(3.2, 450 / head.rangeKm()));
            Vector3f p = at(head);
            Vector3f before = at(OrbitMath.lookFromBlock(s, tick - 2, camX, camZ));
            Vector3f side = p.cross(new Vector3f(p).sub(before), new Vector3f());
            if (side.lengthSquared() > 1e-8f) side.normalize();
            any = true;
            trail(b, m, s, tick, camX, camZ, new Vector3f(), size, light, 60);
            glow(b, m, p, size * 3.6f, 255, 120, 40, light * 0.35f);
            glow(b, m, p, size * 1.7f, 255, 200, 90, light * 0.75f);
            glow(b, m, p, size * 0.75f, 255, 250, 235, light);
            if (f > 0.55) {
                // the pieces drift apart sideways and fall behind
                float spread = (float) (f - 0.55) * 2.2f;
                for (int j = 0; j < 4; j++) {
                    double lag = 2 + j * 1.6 + (f - 0.55) * 10 * (j + 1);
                    Vector3f off = new Vector3f(side).mul((j % 2 == 0 ? 1 : -1) * spread * (0.6f + 0.5f * j));
                    Vector3f q = at(OrbitMath.lookFromBlock(s, tick - lag, camX, camZ)).add(off);
                    float fl = light * (0.55f - 0.08f * j);
                    trail(b, m, s, tick - lag, camX, camZ, off, size * 0.45f, fl, 16);
                    glow(b, m, q, size * 1.1f, 255, 150, 60, fl * 0.5f);
                    glow(b, m, q, size * 0.4f, 255, 235, 200, fl);
                }
            }
        }
        if (any) BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static Vector3f at(OrbitMath.Look l) {
        double[] dir = OrbitMath.direction(l);
        return new Vector3f((float) dir[0], (float) dir[1], (float) dir[2]).normalize(DISTANCE);
    }

    /** The trail of a fireball over its last {@code points} positions, white-hot behind the head and cooling through orange to red. */
    private static void trail(BufferBuilder b, Matrix4f m, Satellite s, double tick, double camX, double camZ, Vector3f offset, float size, float light, int points) {
        Vector3f prev = null;
        float prevWidth = 0;
        int[] prevColor = null;
        double start = s.reentryStart() + OrbitMath.REENTRY_DESCENT_TICKS;
        for (int k = 0; k <= points; k++) {
            double t = tick - k * 2.0;
            if (t < start) break;
            var l = OrbitMath.lookFromBlock(s, t, camX, camZ);
            if (l.elevation() < -0.02) break;
            Vector3f p = at(l).add(offset);
            float fade = 1f - (float) k / (points + 1);
            float width = size * (0.2f + 0.65f * fade);
            int[] c = {255, (int) (90 + 160 * fade * fade), (int) (30 + 200 * fade * fade * fade), (int) (255 * Math.min(1, light) * fade * fade)};
            if (prev != null) ribbon(b, m, prev, p, prevWidth, width, prevColor, c);
            prev = p;
            prevWidth = width;
            prevColor = c;
        }
    }

    /** A round glow, full at the middle and clear at its rim. */
    private static void glow(BufferBuilder b, Matrix4f m, Vector3f c, float radius, int r, int g, int bl, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        if (a == 0) return;
        Vector3f d = new Vector3f(c).normalize();
        Vector3f right = d.cross(0, 1, 0, new Vector3f());
        if (right.lengthSquared() < 1e-4f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = d.cross(right, new Vector3f()).normalize();
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            Vector3f e0 = new Vector3f(right).mul((float) Math.cos(a0) * radius).add(new Vector3f(up).mul((float) Math.sin(a0) * radius)).add(c);
            Vector3f e1 = new Vector3f(right).mul((float) Math.cos(a1) * radius).add(new Vector3f(up).mul((float) Math.sin(a1) * radius)).add(c);
            b.addVertex(m, c.x, c.y, c.z).setColor(r, g, bl, a);
            b.addVertex(m, e0.x, e0.y, e0.z).setColor(r, g, bl, 0);
            b.addVertex(m, e1.x, e1.y, e1.z).setColor(r, g, bl, 0);
            b.addVertex(m, c.x, c.y, c.z).setColor(r, g, bl, a);
        }
    }

    private static void ribbon(BufferBuilder b, Matrix4f m, Vector3f from, Vector3f to, float w0, float w1, int[] c0, int[] c1) {
        Vector3f side = from.cross(new Vector3f(to).sub(from), new Vector3f());
        if (side.lengthSquared() < 1e-8f) return;
        side.normalize();
        b.addVertex(m, from.x - side.x * w0, from.y - side.y * w0, from.z - side.z * w0).setColor(c0[0], c0[1], c0[2], c0[3]);
        b.addVertex(m, from.x + side.x * w0, from.y + side.y * w0, from.z + side.z * w0).setColor(c0[0], c0[1], c0[2], c0[3]);
        b.addVertex(m, to.x + side.x * w1, to.y + side.y * w1, to.z + side.z * w1).setColor(c1[0], c1[1], c1[2], c1[3]);
        b.addVertex(m, to.x - side.x * w1, to.y - side.y * w1, to.z - side.z * w1).setColor(c1[0], c1[1], c1[2], c1[3]);
    }

    private static void point(BufferBuilder b, Matrix4f m, double[] dir, float size, float alpha) {
        Vector3f d = new Vector3f((float) dir[0], (float) dir[1], (float) dir[2]).normalize();
        Vector3f right = d.cross(0, 1, 0, new Vector3f());
        if (right.lengthSquared() < 1e-4f) right.set(1, 0, 0);
        right.normalize(size);
        Vector3f up = d.cross(right, new Vector3f()).normalize(size);
        Vector3f c = d.mul(DISTANCE, new Vector3f());
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        b.addVertex(m, c.x - right.x - up.x, c.y - right.y - up.y, c.z - right.z - up.z).setColor(255, 250, 225, a);
        b.addVertex(m, c.x + right.x - up.x, c.y + right.y - up.y, c.z + right.z - up.z).setColor(255, 250, 225, a);
        b.addVertex(m, c.x + right.x + up.x, c.y + right.y + up.y, c.z + right.z + up.z).setColor(255, 250, 225, a);
        b.addVertex(m, c.x - right.x + up.x, c.y - right.y + up.y, c.z - right.z + up.z).setColor(255, 250, 225, a);
    }

    private static void ribbon(BufferBuilder b, Matrix4f m, Vector3f from, Vector3f to, float w0, float w1, float a0, float a1) {
        Vector3f side = from.cross(new Vector3f(to).sub(from), new Vector3f());
        if (side.lengthSquared() < 1e-8f) return;
        side.normalize();
        int c0 = Math.max(0, Math.min(255, (int) (a0 * 255))), c1 = Math.max(0, Math.min(255, (int) (a1 * 255)));
        b.addVertex(m, from.x - side.x * w0, from.y - side.y * w0, from.z - side.z * w0).setColor(255, 250, 225, c0);
        b.addVertex(m, from.x + side.x * w0, from.y + side.y * w0, from.z + side.z * w0).setColor(255, 250, 225, c0);
        b.addVertex(m, to.x + side.x * w1, to.y + side.y * w1, to.z + side.z * w1).setColor(255, 250, 225, c1);
        b.addVertex(m, to.x - side.x * w1, to.y - side.y * w1, to.z - side.z * w1).setColor(255, 250, 225, c1);
    }
}
