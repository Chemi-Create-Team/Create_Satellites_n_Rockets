package net.createaerospace.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.createaerospace.CreateAerospace;
import net.createaerospace.relay.OrbitalDishEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class OrbitalDishRenderer implements BlockEntityRenderer<OrbitalDishEntity> {
    private static final RenderType METAL = RenderType.entityCutoutNoCull(
        CreateAerospace.id("textures/entity/plain.png"));
    private static final int DARK = 0x485363;
    private static final int FRAME = 0x73818e;
    private static final int STEEL = 0xbac5cd;
    private static final int PANEL = 0xe5ebf1;
    private static final int RIM = 0x8f9ca8;
    private static final int AMBER = 0xc18a55;
    private static final int SECTORS = 48;
    private static final double RADIUS = 7.5;
    private static final double DEPTH = 2.5;
    private static final Vec3 FEED = new Vec3(0, 0, -7.04);

    private static final List<Quad> TOWER = tower();
    private static final List<Quad> GIMBAL = gimbal();
    private static final List<Quad> REFLECTOR = reflector();

    public OrbitalDishRenderer(BlockEntityRendererProvider.Context ignored) { }

    @Override
    public void render(OrbitalDishEntity dish, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vertices = buffers.getBuffer(METAL);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        draw(TOWER, vertices, pose.last(), light);
        pose.translate(0, 8.75, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-dish.bearing()));
        draw(GIMBAL, vertices, pose.last(), light);
        pose.mulPose(Axis.XP.rotationDegrees(dish.elevation()));
        draw(REFLECTOR, vertices, pose.last(), light);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(OrbitalDishEntity dish) {
        Vec3 base = Vec3.atBottomCenterOf(dish.getBlockPos());
        return new AABB(base.add(-9, -1, -9), base.add(9, 18, 9));
    }

    @Override
    public int getViewDistance() {
        return 192;
    }

    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color) { }

    private static void draw(List<Quad> mesh, VertexConsumer out, PoseStack.Pose pose, int light) {
        for (var quad : mesh) {
            vertex(out, pose, quad.a, quad.normal, quad.color, 0, 0, light);
            vertex(out, pose, quad.b, quad.normal, quad.color, 1, 0, light);
            vertex(out, pose, quad.c, quad.normal, quad.color, 1, 1, light);
            vertex(out, pose, quad.d, quad.normal, quad.color, 0, 1, light);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 pos, Vec3 normal, int color, int u, int v, int light) {
        out.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
            .setColor(0xff000000 | color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static List<Quad> tower() {
        var mesh = new ArrayList<Quad>();
        box(mesh, -2.22, .98, -2.22, 2.22, 1.24, 2.22, DARK);
        box(mesh, -1.88, 1.24, -1.88, 1.88, 1.42, 1.88, FRAME);
        box(mesh, -.76, 1.12, -.76, .76, 7.86, .76, STEEL);
        box(mesh, -1.15, 7.21, -1.15, 1.15, 7.51, 1.15, FRAME);
        box(mesh, -.91, 7.49, -.91, .91, 7.83, .91, DARK);
        for (int i = 0; i < 4; i++) {
            double angle = (i + .5) * Math.PI / 2;
            double next = angle + Math.PI / 2;
            Vec3 foot = post(angle, 2.55, 1.35);
            Vec3 shoulder = post(angle, 1.51, 7.22);
            rod(mesh, foot, shoulder, .24, 4, STEEL);
            rod(mesh, foot.lerp(shoulder, .08), post(next, 1.85, 5.35), .107, 4, FRAME);
            rod(mesh, foot.lerp(shoulder, .51), post(next, 1.53, 7.18), .095, 4, FRAME);
            rod(mesh, foot.lerp(shoulder, .42), post(next, 2.12, 3.81), .093, 4, STEEL);
            rod(mesh, post(angle, 2.08, 4.05), post(next, 2.08, 4.05), .105, 4, FRAME);
            box(mesh, foot.x - .34, 1.11, foot.z - .34, foot.x + .34, 1.49, foot.z + .34, DARK);
            box(mesh, foot.x - .24, -.83, foot.z - .24, foot.x + .24, 1.15, foot.z + .24, FRAME);
            box(mesh, foot.x - .37, -.98, foot.z - .37, foot.x + .37, -.82, foot.z + .37, DARK);
        }
        for (int r = 0; r < 13; r++) {
            double y = 1.56 + r * .42;
            rod(mesh, new Vec3(-.39, y, .89), new Vec3(.39, y, .89), .037, 5, RIM);
        }
        for (double x : new double[]{-.4, .4}) {
            rod(mesh, new Vec3(x, 1.39, .88), new Vec3(x, 7.22, .88), .053, 5, RIM);
        }
        return List.copyOf(mesh);
    }

    private static Vec3 post(double angle, double radius, double y) {
        return new Vec3(radius * Math.cos(angle), y, radius * Math.sin(angle));
    }

    private static List<Quad> gimbal() {
        var mesh = new ArrayList<Quad>();
        box(mesh, -1.27, -1.01, -1.22, 1.27, -.79, 1.22, STEEL);
        box(mesh, -1.91, -.80, -.42, 1.91, -.54, 1.42, DARK);
        for (int side : new int[]{-1, 1}) {
            double x = side * 1.73;
            box(mesh, x - .22, -.55, .34, x + .22, .61, 1.03, FRAME);
            rod(mesh, new Vec3(x, -.57, 1.37), new Vec3(x, .53, .46), .18, 4, STEEL);
            rod(mesh, new Vec3(x - .24, 0, 0), new Vec3(x + .24, 0, 0), .44, 12, RIM);
            rod(mesh, new Vec3(x - .25, 0, 0), new Vec3(x + .25, 0, 0), .266, 12, DARK);
        }
        return List.copyOf(mesh);
    }

    private static List<Quad> reflector() {
        var mesh = new ArrayList<Quad>();
        double[] rings = {0, .54, 2.48, 4.95, 7.5};
        for (int i = 0; i < SECTORS; i++) {
            double a = Math.TAU * i / SECTORS, b = Math.TAU * (i + 1) / SECTORS;
            for (int band = 1; band < rings.length; band++) {
                double inner = rings[band - 1], outer = rings[band];
                int color = band == 1 ? 0x77858e : panelColor(i, band);
                var aInner = point(inner, a, 0);
                var bInner = point(inner, b, 0);
                var bOuter = point(outer, b, 0);
                var aOuter = point(outer, a, 0);
                quad(mesh, aInner, band == 1 ? bOuter : bInner, bOuter, aOuter, color);
                var aBack = point(inner, a, .145);
                var bBack = point(inner, b, .145);
                var cBack = point(outer, b, .145);
                var dBack = point(outer, a, .145);
                quad(mesh, dBack, cBack, band == 1 ? aBack : bBack, aBack, 0xc5cfd6);
                if (band > 1) {
                    rod(mesh, point(outer, a, -.012), point(outer, b, -.012),
                        band == rings.length - 1 ? .064 : .022, 4, RIM);
                }
            }
            if (i % 2 == 0) {
                for (int band = 1; band < rings.length; band++) {
                    rod(mesh, point(rings[band - 1], a, .24), point(rings[band], a, .24), .068, 4, FRAME);
                    if (band > 1) {
                        rod(mesh, point(rings[band - 1], a, -.016), point(rings[band], a, -.016), .018, 4, RIM);
                    }
                }
            }
            rod(mesh, point(RADIUS, a, .15), point(RADIUS, b, .15), .104, 5, STEEL);
            if (i % 16 == 2) {
                rod(mesh, point(7.09, a, -.06), FEED, .135, 6, STEEL);
                rod(mesh, point(6.62, a + .09, -.06), point(7.24, a - .05, -.08), .175, 4, RIM);
            }
            if (i % 8 == 0) {
                rod(mesh, point(5.02, a, .23), new Vec3(0, 0, 1.53), .12, 5, FRAME);
            }
        }
        rod(mesh, new Vec3(-1.77, 0, 0), new Vec3(1.77, 0, 0), .33, 8, DARK);
        rod(mesh, new Vec3(0, 0, -1.37), new Vec3(0, 0, 1.56), .58, 10, FRAME);
        rod(mesh, new Vec3(0, 0, -6.73), new Vec3(0, 0, -7.18), .37, 12, FRAME);
        rod(mesh, new Vec3(0, 0, -7.19), new Vec3(0, 0, -7.30), .26, 12, AMBER);
        return List.copyOf(mesh);
    }

    private static int panelColor(int sector, int band) {
        int shade = ((sector / 2 + band * 3) % 5) - 2;
        return PANEL + shade * 0x020202;
    }

    private static Vec3 point(double r, double angle, double offset) {
        return new Vec3(r * Math.cos(angle), r * Math.sin(angle), -1.48 - DEPTH * r * r / (RADIUS * RADIUS) + offset);
    }

    private static void rod(List<Quad> mesh, Vec3 from, Vec3 to, double radius, int sides, int color) {
        Vec3 axis = to.subtract(from).normalize();
        Vec3 reference = Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(reference).normalize().scale(radius);
        Vec3 v = axis.cross(u);
        var edge = u;
        for (int i = 1; i <= sides; i++) {
            double angle = Math.TAU * i / sides;
            var following = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            var a = from.add(edge);
            var b = from.add(following);
            var c = to.add(following);
            var d = to.add(edge);
            quad(mesh, a, b, c, d, color);
            quad(mesh, from, b, a, a, color);
            quad(mesh, to, d, c, c, color);
            edge = following;
        }
    }

    private static void box(List<Quad> mesh, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        var a = new Vec3(x0, y0, z0);
        var b = new Vec3(x1, y0, z0);
        var c = new Vec3(x1, y1, z0);
        var d = new Vec3(x0, y1, z0);
        var e = new Vec3(x0, y0, z1);
        var f = new Vec3(x1, y0, z1);
        var g = new Vec3(x1, y1, z1);
        var h = new Vec3(x0, y1, z1);
        quad(mesh, a, d, c, b, color);
        quad(mesh, e, f, g, h, color);
        quad(mesh, a, e, h, d, color);
        quad(mesh, b, c, g, f, color);
        quad(mesh, d, h, g, c, color);
        quad(mesh, a, b, f, e, color);
    }

    private static void quad(List<Quad> mesh, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        var normal = b.subtract(a).cross(c.subtract(a));
        if (normal.lengthSqr() < 1e-8) {
            normal = c.subtract(a).cross(d.subtract(a));
        }
        mesh.add(new Quad(a, b, c, d, normal.normalize(), color));
    }
}
