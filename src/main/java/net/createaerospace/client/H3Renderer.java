package net.createaerospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createaerospace.CreateAerospace;
import net.createaerospace.api.client.RocketRenderers;
import net.createaerospace.rocket.H3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The H3, built from lathe profiles in real metres and drawn at {@link H3#SCALE}. Lift-off makes a ground cloud of steam from the
 * pad's water; the SRB-3s burn with a bright flame and a thick white trail, the LE-9s with an almost clear, faintly blue hydrogen
 * flame with shock diamonds. The SRB-3s drop away at {@link H3#SRB_SEP} of the climb and the fairing opens at {@link H3#FAIRING_SEP}.
 */
public final class H3Renderer implements RocketRenderers.Renderer {
    private static final ResourceLocation WHITE = CreateAerospace.id("textures/misc/white.png");
    private static final int SEG = 24;
    // fresh polyisocyanurate foam is cream and yellows towards orange in the sun; H3s fly yellow-orange
    private static final int FOAM = 0xFFE3A24A, FAIRING = 0xFFF4F4F2, CARBON = 0xFF2B2B30, AFT = 0xFF7D8088, ATTACH = 0xFFEBC27E,
            SRB = 0xFFF0F0EE, NOZZLE = 0xFF4A4642, BELL = 0xFF6E6A66, HINOMARU = 0xFFC8102E;

    private final H3.Variant v;
    private final int srbSepTick, fairingSepTick;

    public H3Renderer(H3.Variant v) {
        this.v = v;
        srbSepTick = H3.tickAt(v.acceleration, H3.SRB_SEP);
        fairingSepTick = H3.tickAt(v.acceleration, H3.FAIRING_SEP);
    }

    public static void registerAll() {
        for (H3.Variant v : H3.Variant.values()) RocketRenderers.register(v.id(), new H3Renderer(v));
    }

    // ---- geometry -------------------------------------------------------------------------------------------------------------

    /** A lathe profile: pairs of (y, r) in metres, each band coloured like its lower end. */
    private static void lathe(VertexConsumer vc, PoseStack.Pose pose, double cx, double cz, double a0, double a1, int light, double[] yr, int[] colors) {
        for (int i = 0; i + 3 < yr.length; i += 2) {
            band(vc, pose, cx, cz, yr[i], yr[i + 1], yr[i + 2], yr[i + 3], a0, a1, colors[i / 2], light);
        }
    }

    private static void band(VertexConsumer vc, PoseStack.Pose pose, double cx, double cz, double y0, double r0, double y1, double r1,
                             double a0, double a1, int color, int light) {
        band(vc, pose, cx, cz, y0, r0, y1, r1, a0, a1, color, color, light);
    }

    /** {@code color0} at the y0 ring, {@code color1} at the y1 ring; flames fade to clear at their tip this way. */
    private static void band(VertexConsumer vc, PoseStack.Pose pose, double cx, double cz, double y0, double r0, double y1, double r1,
                             double a0, double a1, int color0, int color1, int light) {
        int n = Math.max(2, (int) Math.ceil(SEG * (a1 - a0) / (2 * Math.PI)));
        double dy = y1 - y0, dr = r0 - r1;
        for (int k = 0; k < n; k++) {
            double b0 = a0 + (a1 - a0) * k / n, b1 = a0 + (a1 - a0) * (k + 1) / n;
            vertex(vc, pose, cx, cz, y0, r0, b0, dy, dr, color0, light);
            vertex(vc, pose, cx, cz, y0, r0, b1, dy, dr, color0, light);
            vertex(vc, pose, cx, cz, y1, r1, b1, dy, dr, color1, light);
            vertex(vc, pose, cx, cz, y1, r1, b0, dy, dr, color1, light);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, double cx, double cz, double y, double r, double a,
                               double dy, double dr, int color, int light) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float nx = (float) (c * dy), ny = (float) dr, nz = (float) (s * dy);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) { nx = 0; ny = 1; nz = 0; len = 1; }
        vc.addVertex(pose, (float) (cx + r * c), (float) y, (float) (cz + r * s)).setColor(color).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx / len, ny / len, nz / len);
    }

    private static final double TAU = Math.PI * 2;

    private void core(VertexConsumer vc, PoseStack.Pose pose, int light) {
        double r = H3.CORE_D / 2;
        // engine section, foam of the LH2 and LOX tanks, carbon interstage, second stage, payload adapter
        lathe(vc, pose, 0, 0, 0, TAU, light,
                new double[]{3.0, 0, 3.0, r, 5.5, r, 34.0, r, 40.0, r, 45.5, r, 46.6, r},
                new int[]{CARBON, AFT, FOAM, CARBON, FOAM, CARBON});
        if (v.srbs > 0) {
            // the SRB-3 attach areas are finished differently from the rest of the foam (water-repellent against icing)
            lathe(vc, pose, 0, 0, 0, TAU, light, new double[]{6.0, r + 0.02, 8.0, r + 0.02}, new int[]{ATTACH});
            lathe(vc, pose, 0, 0, 0, TAU, light, new double[]{13.2, r + 0.02, 15.0, r + 0.02}, new int[]{ATTACH});
        }
        // LE-9: two, or three in a triangle; regeneratively cooled throat, bell to the exit
        for (int i = 0; i < v.engines; i++) {
            double a = v.engines == 3 ? Math.toRadians(90 + 120 * i) : Math.PI * i;
            double ex = Math.cos(a) * (v.engines == 3 ? 1.35 : 1.15), ez = Math.sin(a) * (v.engines == 3 ? 1.35 : 1.15);
            lathe(vc, pose, ex, ez, 0, TAU, light, new double[]{0, 0.88, 1.8, 0.62, 2.7, 0.38, 3.0, 0.45}, new int[]{BELL, NOZZLE, NOZZLE});
        }
    }

    /** One fairing half (side = +1 or -1 along x), from the payload adapter up. */
    private void fairing(VertexConsumer vc, PoseStack.Pose pose, int light, int side) {
        double r = (v.wide ? H3.WIDE_D : H3.CORE_D) / 2, base = 46.6, top = v.lengthM(), cyl = v.wide ? 9.0 : 5.0;
        int n = 8;
        double[] yr = new double[4 + 2 * n];
        int[] colors = new int[1 + n];
        yr[0] = base; yr[1] = r; yr[2] = base + cyl; yr[3] = r;
        for (int k = 1; k <= n; k++) {
            double t = (double) k / n;
            yr[2 + 2 * k] = base + cyl + (top - base - cyl) * t;
            yr[3 + 2 * k] = k == n ? 0 : r * Math.pow(Math.cos(t * Math.PI / 2), 0.8) + 0.25 * (1 - t);
        }
        java.util.Arrays.fill(colors, FAIRING);
        double a0 = side > 0 ? -Math.PI / 2 : Math.PI / 2;
        lathe(vc, pose, 0, 0, a0, a0 + Math.PI, light, yr, colors);
    }

    /** One SRB-3 centred at (x, z), bottom of its nozzle at y = 0. */
    private static void srb(VertexConsumer vc, PoseStack.Pose pose, double x, double z, int light) {
        double r = H3.SRB_D / 2;
        lathe(vc, pose, x, z, 0, TAU, light,
                new double[]{0.0, 0.78, 1.2, 0.5, 1.2, r, 12.3, r, 13.4, 0.95, 14.4, 0.45, 15.0, 0},
                new int[]{NOZZLE, CARBON, SRB, SRB, SRB, SRB});
    }

    private double[][] srbPositions() {
        double d = H3.CORE_D / 2 + H3.SRB_D / 2 + 0.05;
        return v.srbs == 4 ? new double[][]{{d, 0}, {-d, 0}, {0, d}, {0, -d}} : v.srbs == 2 ? new double[][]{{d, 0}, {-d, 0}} : new double[0][];
    }

    // ---- markings ---------------------------------------------------------------------------------------------------------------

    private static void flag(VertexConsumer vc, PoseStack.Pose pose, double z, double y, int light) {
        // 2:3 white field with the red disc, flat on the face of the tank
        double w = 1.5, h = 1.0;
        quad(vc, pose, -w / 2, y, z, w / 2, y + h, z, 0xFFFFFFFF, light);
        int n = 16;
        double cy = y + h / 2, rr = h * 0.3;
        for (int k = 0; k < n; k++) {
            double a = TAU * k / n, b = TAU * (k + 1) / n;
            vc.addVertex(pose, 0, (float) cy, (float) z + 0.01f).setColor(HINOMARU).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose, (float) (rr * Math.cos(a)), (float) (cy + rr * Math.sin(a)), (float) z + 0.01f).setColor(HINOMARU).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose, (float) (rr * Math.cos(b)), (float) (cy + rr * Math.sin(b)), (float) z + 0.01f).setColor(HINOMARU).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose, 0, (float) cy, (float) z + 0.01f).setColor(HINOMARU).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z, double x1, double y1, double z1, int color, int light) {
        vc.addVertex(pose, (float) x0, (float) y0, (float) z).setColor(color).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, (float) x1, (float) y0, (float) z).setColor(color).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, (float) x0, (float) y1, (float) z1).setColor(color).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }

    /** "JAPAN" in tall black letters stacked down the first stage, as on the H3 (H-II to H-IIB said NIPPON). */
    private static void lettering(PoseStack ps, MultiBufferSource buffers, int light) {
        Font font = Minecraft.getInstance().font;
        String word = "JAPAN";
        float letter = 2.2f; // metres per letter
        for (int face = 0; face < 2; face++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(face * 180));
            for (int i = 0; i < word.length(); i++) {
                ps.pushPose();
                ps.translate(0, 29.0 - i * letter * 1.15, H3.CORE_D / 2 + 0.03);
                float s = letter / 8f;
                ps.scale(s, -s, s);
                String c = word.substring(i, i + 1);
                font.drawInBatch(c, -font.width(c) / 2f, -8, 0xFF141414, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    // ---- flames -----------------------------------------------------------------------------------------------------------------

    /** A glowing cone hanging down from (x, y0, z), {@code argb} at the nozzle fading to clear at the tip. */
    private static void plume(VertexConsumer vc, PoseStack.Pose pose, double x, double z, double y0, double r0, double length, double r1, int argb) {
        band(vc, pose, x, z, y0 - length, r1, y0, r0, 0, TAU, argb & 0x00FFFFFF, argb, 0xF000F0);
    }

    private void flames(PoseStack.Pose pose, MultiBufferSource buffers, float t, long gameTime, boolean srbsOn) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        float flicker = 0.85f + 0.15f * Mth.sin((gameTime + t) * 2.7f);
        for (int i = 0; i < v.engines; i++) {
            double a = v.engines == 3 ? Math.toRadians(90 + 120 * i) : Math.PI * i;
            double ex = Math.cos(a) * (v.engines == 3 ? 1.35 : 1.15), ez = Math.sin(a) * (v.engines == 3 ? 1.35 : 1.15);
            // hydrogen burns almost clear: a faint blue sheath with bright shock diamonds
            plume(vc, pose, ex, ez, 0, 0.85, 9 * flicker, 0.4, 0x407FB4FF);
            for (int d = 0; d < 3; d++) {
                double y = -1.2 - d * 2.1;
                band(vc, pose, ex, ez, y - 0.6, 0.0, y, 0.32 - d * 0.06, 0, TAU, 0xC0E6F2FF, 0xF000F0);
                band(vc, pose, ex, ez, y, 0.32 - d * 0.06, y + 0.6, 0.0, 0, TAU, 0xC0E6F2FF, 0xF000F0);
            }
        }
        if (srbsOn) {
            for (double[] p : srbPositions()) {
                plume(vc, pose, p[0], p[1], 0, 0.75, 6 * flicker, 0.3, 0xFFFFF8E0);
                plume(vc, pose, p[0], p[1], 0.2, 0.9, 14 * flicker, 1.4, 0xD0FFC050);
                plume(vc, pose, p[0], p[1], 0.2, 1.0, 22 * flicker, 2.2, 0x70FF8A20);
            }
        }
    }

    // ---- render -----------------------------------------------------------------------------------------------------------------

    @Override
    public void render(RocketRenderers.Context c) {
        PoseStack ps = c.pose();
        float t = c.ascending() ? c.ascentTicks() + c.partialTick() : 0;
        int light = c.ascending() ? 0xF000F0 : c.packedLight();
        ps.pushPose();
        ps.scale((float) H3.SCALE, (float) H3.SCALE, (float) H3.SCALE);
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = c.buffers().getBuffer(RenderType.entityCutoutNoCull(WHITE));
        core(vc, pose, light);
        flag(vc, pose, H3.CORE_D / 2 + 0.03, 31.0, light);
        boolean srbsOn = t < srbSepTick;
        for (double[] p : srbPositions()) {
            if (srbsOn) {
                srb(vc, pose, p[0], p[1], light);
            } else if (t - srbSepTick < 80) {
                // dropping away: pushed out by the separation thrusters, tipping over, falling behind the climbing core
                double dt = t - srbSepTick, len = Math.hypot(p[0], p[1]);
                ps.pushPose();
                ps.translate(p[0] / len * 0.12 * dt, -0.05 * dt * dt / H3.SCALE, p[1] / len * 0.12 * dt);
                ps.translate(p[0], 0, p[1]);
                ps.mulPose(Axis.of(new org.joml.Vector3f((float) -p[1], 0, (float) p[0]).normalize()).rotationDegrees((float) (-1.2 * dt)));
                ps.translate(-p[0], 0, -p[1]);
                srb(vc, ps.last(), p[0], p[1], light);
                ps.popPose();
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            double dt = t - fairingSepTick;
            if (dt < 0) {
                fairing(vc, pose, light, side);
            } else if (dt < 80) {
                // clamshell halves hinge outward from their base and fall away
                ps.pushPose();
                ps.translate(side * 0.1 * dt, -0.04 * dt * dt / H3.SCALE, 0);
                ps.translate(side * H3.CORE_D / 2, 46.6, 0);
                ps.mulPose(Axis.ZP.rotationDegrees((float) (-side * Math.min(90, 2.5 * dt))));
                ps.translate(-side * H3.CORE_D / 2, -46.6, 0);
                fairing(vc, ps.last(), light, side);
                ps.popPose();
            }
        }
        lettering(ps, c.buffers(), light);
        if (c.ascending()) flames(pose, c.buffers(), t, c.gameTime(), srbsOn);
        ps.popPose();
    }

    @Override
    public void exhaust(ClientLevel level, double x, double y, double z, int ascentTicks, RandomSource random) {
        double s = H3.SCALE;
        // water deluge flashing to steam: a ground cloud rolling out from the pad while the rocket clears the tower height
        if (ascentTicks < 70) {
            double ground = y - H3.climbed(v.acceleration, ascentTicks) + 0.3;
            int n = v.srbs == 0 ? 6 : 14;
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2, sp = (0.25 + random.nextDouble() * 0.45) * (0.5 + s);
                level.addParticle(ParticleTypes.CLOUD, x, ground, z, Math.cos(a) * sp, 0.02 + random.nextDouble() * 0.05, Math.sin(a) * sp);
            }
            if (v.srbs > 0) for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2, sp = 0.1 + random.nextDouble() * 0.25;
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, x + Math.cos(a) * 4 * s, ground, z + Math.sin(a) * 4 * s,
                        Math.cos(a) * sp, 0.03, Math.sin(a) * sp);
            }
            if (ascentTicks % 10 == 0)
                level.playLocalSound(x, ground, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, v.srbs == 0 ? 2f : 4f, 0.35f + random.nextFloat() * 0.1f, false);
        }
        int srbSep = H3.tickAt(v.acceleration, H3.SRB_SEP), fairingSep = H3.tickAt(v.acceleration, H3.FAIRING_SEP);
        if (ascentTicks < srbSep) {
            // SRB-3: ammonium perchlorate composite leaves a dense white column that hangs in the air
            for (double[] p : srbPositions()) {
                double px = x + p[0] * s, pz = z + p[1] * s;
                for (int i = 0; i < 3; i++)
                    level.addParticle(ParticleTypes.FLAME, px + (random.nextDouble() - .5) * 1.2 * s, y - .3, pz + (random.nextDouble() - .5) * 1.2 * s, 0, -.6, 0);
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, px + (random.nextDouble() - .5) * 2 * s, y - 3 * s, pz + (random.nextDouble() - .5) * 2 * s,
                        (random.nextDouble() - .5) * .04, -0.02, (random.nextDouble() - .5) * .04);
                level.addParticle(ParticleTypes.LARGE_SMOKE, px, y - 1, pz, (random.nextDouble() - .5) * .1, -.2, (random.nextDouble() - .5) * .1);
            }
        } else if (ascentTicks == srbSep && v.srbs > 0) {
            for (double[] p : srbPositions())
                for (int i = 0; i < 12; i++)
                    level.addParticle(ParticleTypes.POOF, x + p[0] * s, y + 14 * s, z + p[1] * s, (random.nextDouble() - .5) * .3, (random.nextDouble() - .5) * .3, (random.nextDouble() - .5) * .3);
            level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3f, 1.4f, false);
        }
        if (ascentTicks == fairingSep) {
            for (int i = 0; i < 16; i++)
                level.addParticle(ParticleTypes.POOF, x, y + 46.6 * s, z, (random.nextDouble() - .5) * .4, (random.nextDouble() - .5) * .2, (random.nextDouble() - .5) * .4);
            level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 3f, 0.8f, false);
        }
        // LE-9: the hydrogen flame itself is nearly invisible; a thin trail of water vapour condenses behind it
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CLOUD, x + (random.nextDouble() - .5) * 2.4 * s, y - 6 * s, z + (random.nextDouble() - .5) * 2.4 * s, 0, -.05, 0);
        }
        for (int i = 0; i < v.engines; i++) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - .5) * 2 * s, y - .2, z + (random.nextDouble() - .5) * 2 * s, 0, -.4, 0);
        }
    }
}
