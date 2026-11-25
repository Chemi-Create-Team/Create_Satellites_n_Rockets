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
        if (dark < 0.05f) return;
        var cam = e.getCamera().getPosition();
        double tick = level.getGameTime() + partial;
        Matrix4f m = e.getModelViewMatrix();

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
