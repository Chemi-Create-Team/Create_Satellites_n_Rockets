package net.createaerospace.client;

import static net.createaerospace.client.H3Renderer.TAU;
import static net.createaerospace.client.H3Renderer.band;
import static net.createaerospace.client.H3Renderer.lathe;
import static net.createaerospace.client.H3Renderer.plume;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createaerospace.api.RocketApi;
import net.createaerospace.api.client.RocketRenderers;
import net.createaerospace.rocket.Falcon9;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * Falcon 9 Block 5 at full size from lathe profiles in metres: white tanks, black interstage, nine Merlin 1D under the octaweb, four
 * grid fins and four landing legs folded against the first stage. A reflown first stage is sooty. On the way back the second stage
 * flies on with its Merlin Vacuum, the first stage flips for the boostback burn, opens its grid fins, falls engines-down with an
 * entry burn on three engines, a landing burn on one, legs out, and touches down (timing in {@link Falcon9}). No logos.
 */
public final class Falcon9Renderer implements RocketRenderers.Renderer {
    private static final int BODY = 0xFFF2F2F0, BLACK = 0xFF1C1C20, SOOT = 0xFF34302C, TITANIUM = 0xFF707276, NOZZLE = 0xFF3A3836,
            BELL = 0xFF5E5852, LEG = 0xFF202024, FOOT = 0xFF5A5A5E, SKIRT = 0xFF4A4C52;
    private static final double R = Falcon9.CORE_D / 2, PIVOT = 20;
    /** The octaweb: one Merlin in the middle, eight on a ring. */
    private static final double[][] ENGINES = engines();

    private static double[][] engines() {
        double[][] e = new double[9][];
        e[0] = new double[] {0, 0};
        for (int i = 0; i < 8; i++) e[i + 1] = new double[] {1.25 * Math.cos(TAU * i / 8), 1.25 * Math.sin(TAU * i / 8)};
        return e;
    }

    public static void register() {
        RocketRenderers.register(Falcon9.ID, new Falcon9Renderer());
    }

    @Override
    public void render(RocketRenderers.Context c) {
        PoseStack ps = c.pose();
        int flights = RocketApi.flights(c.rocketItem()) + (c.returning() ? 1 : 0);
        boolean landed = !c.ascending() && !c.returning() && RocketApi.needsService(c.rocketItem());
        int light = c.ascending() || c.returning() ? 0xF000F0 : c.packedLight();
        float t = c.returnTicks() + c.partialTick();
        VertexConsumer vc = c.buffers().getBuffer(RenderType.entityCutoutNoCull(H3Renderer.WHITE));
        VertexConsumer fire = c.buffers().getBuffer(RenderType.entityTranslucentEmissive(H3Renderer.WHITE));
        float flicker = 0.85f + 0.15f * Mth.sin((c.gameTime() + c.partialTick()) * 2.9f);

        if (c.returning() && t < 90) {
            // the second stage flies on, away from the turning first stage
            ps.pushPose();
            ps.translate(0, 3 + 0.4 * t + 0.03 * t * t, 0);
            upper(vc, ps.last(), light, true);
            if (t > 8) plume(fire, ps.last(), 0, 0, Falcon9.STAGE1_TOP - 3, 1.5, 28 * flicker, 3.5, 0x60FFB070);
            ps.popPose();
        }
        ps.pushPose();
        if (c.returning()) {
            ps.translate(0, PIVOT, 0);
            ps.mulPose(Axis.XP.rotationDegrees((float) Falcon9.pitch(t)));
            ps.translate(0, -PIVOT, 0);
        }
        PoseStack.Pose pose = ps.last();
        double fins = c.returning() ? Falcon9.gridFins(t) : landed ? 1 : 0;
        double legs = c.returning() ? Falcon9.legs(t) : landed ? 1 : 0;
        firstStage(vc, pose, light, flights, fins, legs);
        if (!c.returning() && !landed) upper(vc, pose, light, false);
        if (c.ascending()) {
            float grow = 1 + c.ascentTicks() / 160f;
            for (double[] e : ENGINES) plume(fire, pose, e[0], e[1], 0, 0.42, 5 * flicker, 0.18, 0xF0FFF0C0);
            plume(fire, pose, 0, 0, 0.2, 1.9, 28 * flicker * grow, 3.2 * grow, 0xB0FFA838);
            plume(fire, pose, 0, 0, 0.2, 2.3, 44 * flicker * grow, 6 * grow, 0x50FF6A10);
        } else if (c.returning()) {
            if (Falcon9.boostback(t) || Falcon9.entryBurn(t)) {
                for (int i : new int[] {0, 1, 5}) plume(fire, pose, ENGINES[i][0], ENGINES[i][1], 0, 0.42, 6 * flicker, 0.2, 0xF0FFF0C0);
                plume(fire, pose, 0, 0, 0.2, 1.6, 18 * flicker, 2.8, 0xA0FFA838);
            }
            if (Falcon9.landingBurn(t)) {
                plume(fire, pose, 0, 0, 0, 0.42, 5 * flicker, 0.2, 0xF0FFF0C0);
                plume(fire, pose, 0, 0, 0.1, 0.8, 12 * flicker, 1.6, 0xB0FFB040);
            }
        }
        ps.popPose();
    }

    // ---- geometry ---------------------------------------------------------------------------------------------------------------

    private static int mix(int a, int b, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) Mth.lerp(f, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(f, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(f, a & 255, b & 255);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static void firstStage(VertexConsumer vc, PoseStack.Pose pose, int light, int flights, double fins, double legs) {
        // Merlin 1D bells, the octaweb heat shield and the thrust section
        for (double[] e : ENGINES) lathe(vc, pose, e[0], e[1], 0, TAU, light, new double[] {0, 0.46, 1.0, 0.30, 1.4, 0.2, 1.6, 0.3}, new int[] {BELL, NOZZLE, NOZZLE});
        lathe(vc, pose, 0, 0, 0, TAU, light, new double[] {1.55, 0, 1.55, R, 2.6, R}, new int[] {BLACK, SKIRT});
        // the tanks, sootier the more often it has flown (heavier towards the top, where the entry burn wraps it)
        double soot = Math.min(0.85, flights * 0.3);
        int n = 12;
        for (int k = 0; k < n; k++) {
            double y0 = 2.6 + (Falcon9.INTERSTAGE_BASE - 2.6) * k / n, y1 = 2.6 + (Falcon9.INTERSTAGE_BASE - 2.6) * (k + 1) / n;
            double v = (double) k / n;
            band(vc, pose, 0, 0, y0, R, y1, R, 0, TAU, mix(BODY, SOOT, soot * (0.45 + 0.55 * v) * (0.7 + 0.3 * Math.sin(k * 2.1 + flights))), light);
        }
        lathe(vc, pose, 0, 0, 0, TAU, light, new double[] {Falcon9.INTERSTAGE_BASE, R + .01, Falcon9.STAGE1_TOP, R + .01}, new int[] {BLACK});
        // grid fins: hinged at their base, folded up against the interstage, swung out flat for the way down
        for (int i = 0; i < 4; i++) {
            double a = TAU * i / 4;
            Vector3f radial = new Vector3f((float) Math.cos(a), 0, (float) Math.sin(a)), tangent = new Vector3f((float) -Math.sin(a), 0, (float) Math.cos(a));
            double phi = Math.toRadians(90 * fins);
            Vector3f out = new Vector3f(0, (float) Math.cos(phi), 0).add(new Vector3f(radial).mul((float) Math.sin(phi)));
            Vector3f thick = new Vector3f(radial).mul((float) Math.cos(phi)).add(0, (float) -Math.sin(phi), 0);
            Vector3f hinge = new Vector3f(radial).mul((float) (R + 0.12)).add(0, 38.6f, 0).sub(new Vector3f(tangent).mul(0.6f));
            box(vc, pose, hinge, out.mul(1.5f), new Vector3f(tangent).mul(1.2f), thick.mul(0.14f), TITANIUM, light);
        }
        // landing legs: carbon fibre, folded up along the tanks, swung down and out until their feet are level with the engines
        for (int i = 0; i < 4; i++) {
            double a = TAU * (i + 0.5) / 4;
            Vector3f radial = new Vector3f((float) Math.cos(a), 0, (float) Math.sin(a)), tangent = new Vector3f((float) -Math.sin(a), 0, (float) Math.cos(a));
            double phi = Math.toRadians(107.8 * legs);
            Vector3f along = new Vector3f(0, (float) Math.cos(phi), 0).add(new Vector3f(radial).mul((float) Math.sin(phi)));
            Vector3f thick = new Vector3f(radial).mul((float) Math.cos(phi)).add(0, (float) -Math.sin(phi), 0);
            Vector3f hinge = new Vector3f(radial).mul((float) (R + 0.05)).add(0, 2.6f, 0).sub(new Vector3f(tangent).mul(0.3f));
            box(vc, pose, hinge, new Vector3f(along).mul(8.5f), new Vector3f(tangent).mul(0.6f), new Vector3f(thick).mul(0.35f), LEG, light);
            box(vc, pose, new Vector3f(hinge).add(new Vector3f(along).mul(8.1f)), new Vector3f(along).mul(0.5f), new Vector3f(tangent).mul(0.6f),
                    new Vector3f(thick).mul(0.5f), FOOT, light);
            // the telescoping strut from the tank to the middle of the leg
            Vector3f body = new Vector3f(radial).mul((float) R).add(0, 8.5f, 0).add(new Vector3f(tangent).mul(-0.1f));
            Vector3f mid = new Vector3f(hinge).add(new Vector3f(along).mul(5.0f)).add(new Vector3f(tangent).mul(0.2f));
            box(vc, pose, body, mid.sub(body), new Vector3f(tangent).mul(0.2f), new Vector3f(0, 0.2f, 0).add(new Vector3f(radial).mul(0.05f)), FOOT, light);
        }
    }

    /** The second stage and the fairing; with its Merlin Vacuum bell showing once it has left the interstage. */
    private static void upper(VertexConsumer vc, PoseStack.Pose pose, int light, boolean separated) {
        if (separated) lathe(vc, pose, 0, 0, 0, TAU, light, new double[] {Falcon9.STAGE1_TOP - 3, 1.5, Falcon9.STAGE1_TOP, 0.6}, new int[] {NOZZLE});
        double rf = Falcon9.FAIRING_D / 2, base = Falcon9.STAGE2_TOP, cyl = 63.6;
        int n = 8;
        double[] yr = new double[10 + 2 * n];
        int[] colors = new int[4 + n];
        double[] head = {Falcon9.STAGE1_TOP, R, base, R, base + 1.5, rf, cyl, rf};
        System.arraycopy(head, 0, yr, 0, 8);
        for (int k = 1; k <= n; k++) {
            double f = (double) k / n;
            yr[6 + 2 * k] = cyl + (Falcon9.LENGTH - cyl) * f;
            yr[7 + 2 * k] = k == n ? 0 : rf * Math.pow(Math.cos(f * Math.PI / 2), 0.8) + 0.2 * (1 - f);
        }
        java.util.Arrays.fill(colors, BODY);
        lathe(vc, pose, 0, 0, 0, TAU, light, java.util.Arrays.copyOf(yr, 8 + 2 * n), colors);
        if (separated) lathe(vc, pose, 0, 0, 0, TAU, light, new double[] {Falcon9.STAGE1_TOP, 0, Falcon9.STAGE1_TOP, R}, new int[] {BLACK});
    }

    /** A parallelepiped from corner {@code o} along the edges {@code a}, {@code b}, {@code c}. */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, Vector3f o, Vector3f a, Vector3f b, Vector3f c, int color, int light) {
        Vector3f[][] pairs = {{a, b, c}, {b, c, a}, {c, a, b}};
        for (Vector3f[] e : pairs) {
            Vector3f n = new Vector3f(e[0]).cross(e[1]);
            if (n.lengthSquared() < 1e-10f) continue;
            n.normalize();
            if (n.dot(e[2]) > 0) n.negate();
            for (int side = 0; side < 2; side++) {
                Vector3f p = side == 0 ? new Vector3f(o) : new Vector3f(o).add(e[2]);
                float sn = side == 0 ? 1 : -1;
                Vector3f[] q = {p, new Vector3f(p).add(e[0]), new Vector3f(p).add(e[0]).add(e[1]), new Vector3f(p).add(e[1])};
                for (Vector3f v : q)
                    vc.addVertex(pose, v.x, v.y, v.z).setColor(color).setUv(.5f, .5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose, sn * n.x, sn * n.y, sn * n.z);
            }
        }
    }

    // ---- particles --------------------------------------------------------------------------------------------------------------

    @Override
    public void exhaust(ClientLevel level, double x, double y, double z, int ascentTicks, RandomSource random) {
        if (ascentTicks < 80) {
            // water deluge flashing to steam under nine Merlins
            double ground = ground(level, x, y, z);
            for (int i = 0; i < 12; i++) {
                double a = random.nextDouble() * TAU, sp = 0.35 + random.nextDouble() * 0.6;
                level.addParticle(ParticleTypes.CLOUD, x, ground + .3, z, Math.cos(a) * sp, 0.03 + random.nextDouble() * 0.05, Math.sin(a) * sp);
            }
            if (ascentTicks % 10 == 0) level.playLocalSound(x, ground, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4f, 0.4f + random.nextFloat() * .1f, false);
        }
        for (int i = 0; i < 6; i++)
            level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - .5) * 3, y - .3, z + (random.nextDouble() - .5) * 3, 0, -.7, 0);
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x + (random.nextDouble() - .5) * 3, y - 6, z + (random.nextDouble() - .5) * 3, 0, -.03, 0);
        level.addParticle(ParticleTypes.LARGE_SMOKE, x, y - 3, z, (random.nextDouble() - .5) * .2, -.3, (random.nextDouble() - .5) * .2);
    }

    @Override
    public void returnExhaust(ClientLevel level, double x, double y, double z, int t, RandomSource random) {
        if (t == 1) {
            for (int i = 0; i < 20; i++) level.addParticle(ParticleTypes.POOF, x, y + Falcon9.STAGE1_TOP, z, (random.nextDouble() - .5) * .5, random.nextDouble() * .3, (random.nextDouble() - .5) * .5);
            level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 6f, .7f, false);
        }
        // the base of the stage as it turns about its middle
        double pitch = Math.toRadians(Falcon9.pitch(t));
        double bx = x, by = y + PIVOT - PIVOT * Math.cos(pitch), bz = z - PIVOT * Math.sin(pitch);
        double dy = -Math.cos(pitch), dz = -Math.sin(pitch);
        if (Falcon9.boostback(t) || Falcon9.entryBurn(t)) {
            for (int i = 0; i < 5; i++) level.addParticle(ParticleTypes.FLAME, bx + (random.nextDouble() - .5) * 2, by, bz, 0, dy * .8, dz * .8);
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, bx, by + dy * 8, bz + dz * 8, 0, 0, 0);
        }
        // the triple sonic boom of a booster coming home
        int f = t - net.createaerospace.api.RocketApi.Recovery.HOLD_TICKS;
        if (f == 60 || f == 64 || f == 68) level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 16f, .45f, false);
        if (Falcon9.landingBurn(t)) {
            for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - .5) * .8, y - .2, z + (random.nextDouble() - .5) * .8, 0, -.8, 0);
            double ground = ground(level, x, y, z);
            if (y - ground < 30) {
                // the plume hits the pad and throws a ring of dust and steam
                for (int i = 0; i < 10; i++) {
                    double a = random.nextDouble() * TAU, sp = 0.3 + random.nextDouble() * 0.5;
                    level.addParticle(ParticleTypes.CLOUD, x, ground + .3, z, Math.cos(a) * sp, 0.02, Math.sin(a) * sp);
                }
            }
        }
    }

    /** The top of the ground under a point, looking down at most 64 blocks. */
    private static double ground(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < 64 && level.isEmptyBlock(p); i++) p.move(0, -1, 0);
        return p.getY() + 1;
    }
}
