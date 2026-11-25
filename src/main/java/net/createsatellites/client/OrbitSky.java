package net.createsatellites.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createsatellites.CreateSatellites;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Black sky with stars; the Earth hangs below, the Moon and the Sun off to the sides. It is a picture, not a simulation. */
public class OrbitSky extends DimensionSpecialEffects {
    private static final ResourceLocation EARTH = CreateSatellites.id("textures/environment/earth.png");
    private static final ResourceLocation MOON = CreateSatellites.id("textures/environment/moon.png");
    private static final ResourceLocation SUN = CreateSatellites.id("textures/environment/sun.png");
    /** Direction and apparent diameter in degrees. */
    private static final Vector3f EARTH_DIR = new Vector3f(0.25f, -0.85f, 0.45f).normalize();
    private static final Vector3f MOON_DIR = new Vector3f(-0.65f, 0.4f, -0.6f).normalize();
    private static final Vector3f SUN_DIR = new Vector3f(0.6f, 0.45f, 0.65f).normalize();
    private static final float[][] STARS = new float[600][4];

    static {
        var r = new java.util.Random(42);
        for (float[] s : STARS) {
            Vector3f d = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
            s[0] = d.x; s[1] = d.y; s[2] = d.z; s[3] = 0.4f + r.nextFloat() * 0.6f;
        }
    }

    public OrbitSky() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return Vec3.ZERO;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera, Matrix4f projection,
                             boolean isFoggy, Runnable setupFog) {
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] s : STARS) {
            Vector3f d = new Vector3f(s[0], s[1], s[2]);
            Vector3f right = d.cross(0, 1, 0, new Vector3f());
            if (right.lengthSquared() < 1e-4f) continue;
            right.normalize(0.35f * s[3]);
            Vector3f up = d.cross(right, new Vector3f()).normalize(0.35f * s[3]);
            Vector3f c = d.mul(100, new Vector3f());
            int g = (int) (140 + 115 * s[3]);
            quadColor(b, modelView, c, right, up, g);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // the Earth turns once in about two real minutes
        disc(modelView, SUN, SUN_DIR, 18, 0, true);
        disc(modelView, MOON, MOON_DIR, 9, 0, false);
        disc(modelView, EARTH, EARTH_DIR, 75, (ticks + partialTick) / 2400f * Mth.TWO_PI, false);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        return true;
    }

    private static void quadColor(com.mojang.blaze3d.vertex.BufferBuilder b, Matrix4f m, Vector3f c, Vector3f r, Vector3f u, int grey) {
        b.addVertex(m, c.x - r.x - u.x, c.y - r.y - u.y, c.z - r.z - u.z).setColor(grey, grey, grey, 255);
        b.addVertex(m, c.x + r.x - u.x, c.y + r.y - u.y, c.z + r.z - u.z).setColor(grey, grey, grey, 255);
        b.addVertex(m, c.x + r.x + u.x, c.y + r.y + u.y, c.z + r.z + u.z).setColor(grey, grey, grey, 255);
        b.addVertex(m, c.x - r.x + u.x, c.y - r.y + u.y, c.z - r.z + u.z).setColor(grey, grey, grey, 255);
    }

    private static void disc(Matrix4f m, ResourceLocation tex, Vector3f dir, float degrees, float spin, boolean additive) {
        RenderSystem.setShaderTexture(0, tex);
        if (additive) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        float half = 100f * (float) Math.tan(Math.toRadians(degrees) / 2);
        Vector3f right = dir.cross(0, 1, 0, new Vector3f());
        if (right.lengthSquared() < 1e-4f) right.set(1, 0, 0);
        right.normalize(half);
        Vector3f up = dir.cross(right, new Vector3f()).normalize(half);
        Vector3f c = dir.mul(100, new Vector3f());
        var b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // spin the quad itself: rotating the uv would sample outside the picture at the corners
        float cos = Mth.cos(spin), sin = Mth.sin(spin);
        Vector3f r2 = new Vector3f(right).mul(cos).add(new Vector3f(up).mul(sin));
        Vector3f u2 = new Vector3f(up).mul(cos).sub(new Vector3f(right).mul(sin));
        int[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int[] k : corner) {
            b.addVertex(m, c.x + r2.x * k[0] + u2.x * k[1], c.y + r2.y * k[0] + u2.y * k[1], c.z + r2.z * k[0] + u2.z * k[1])
                    .setUv(k[0] < 0 ? 0 : 1, k[1] < 0 ? 0 : 1);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    @EventBusSubscriber(modid = CreateSatellites.MOD_ID, value = Dist.CLIENT)
    public static class Register {
        @SubscribeEvent
        public static void effects(RegisterDimensionSpecialEffectsEvent e) {
            e.register(CreateSatellites.id("orbit"), new OrbitSky());
        }
    }
}
