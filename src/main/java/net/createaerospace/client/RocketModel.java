package net.createaerospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.createaerospace.CreateAerospace;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Standard rocket mesh in block units. The main and booster profiles unwrap across separate strips of rocket.png. */
public final class RocketModel {
    private static final float TEXTURE_SIZE = 512;
    private static final int SIDES = 24;
    private static final float MAIN_BOOSTER_OFFSET = 3.45f;
    private static final float AUX_BOOSTER_OFFSET = 3.25f;
    private static final List<Vertex> MESH = build();

    private RocketModel() {}

    public static void render(PoseStack.Pose pose, VertexConsumer target, int light) {
        for (Vertex v : MESH) {
            target.addVertex(pose, v.x, v.y, v.z).setColor(-1).setUv(v.u, v.v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, v.nx, v.ny, v.nz);
        }
    }

    private static List<Vertex> build() {
        List<Vertex> vertices = new ArrayList<>();
        addShell(vertices, 0, 0, new Strip(4, 316, 12, 492, CreateAerospace.STANDARD_ROCKET_HEIGHT),
                new Ring(2.25f, 0), new Ring(2.25f, 2.55f), new Ring(3.4f, 3), new Ring(41.6f, 3),
                new Ring(42.4f, 2.86f), new Ring(43.2f, 2.86f), new Ring(44, 3.24f), new Ring(51.9f, 3.24f),
                new Ring(53.8f, 3.13f), new Ring(55.6f, 2.82f), new Ring(57.3f, 2.31f), new Ring(58.6f, 1.66f),
                new Ring(59.5f, .9f), new Ring(CreateAerospace.STANDARD_ROCKET_HEIGHT, 0));
        addEngine(vertices, 0, 0, 1.77f, 3.65f, 0);

        addBooster(vertices, -MAIN_BOOSTER_OFFSET, 0, 1.43f, 25, 0);
        addBooster(vertices, MAIN_BOOSTER_OFFSET, 0, 1.43f, 25, 0);
        addBooster(vertices, 0, -AUX_BOOSTER_OFFSET, 1.03f, 19, .25f);
        addBooster(vertices, 0, AUX_BOOSTER_OFFSET, 1.03f, 19, .25f);
        return List.copyOf(vertices);
    }

    private static void addBooster(List<Vertex> vertices, float x, float z, float r, float height, float base) {
        addShell(vertices, x, z, new Strip(328, 442, 141, 466, height),
                new Ring(base + 1.25f, 0), new Ring(base + 1.25f, .78f * r), new Ring(base + 2.1f, r),
                new Ring(height * .74f, r), new Ring(height * .83f, .91f * r), new Ring(height * .91f, .72f * r),
                new Ring(height * .97f, .4f * r), new Ring(height, 0));
        addEngine(vertices, x, z, .65f * r, 2.24f, base);
    }

    private static void addEngine(List<Vertex> vertices, float x, float z, float r, float height, float base) {
        addShell(vertices, x, z, new Strip(454, 507, 303, 466, height),
                new Ring(base, 0), new Ring(base, r), new Ring(base + .2f, r), new Ring(base + .44f, .91f * r),
                new Ring(height * .56f + base, .59f * r), new Ring(height, .61f * r));
    }

    private static void addShell(List<Vertex> vertices, float x, float z, Strip atlas, Ring... rings) {
        for (int row = 1; row < rings.length; row++) {
            Ring bottom = rings[row - 1], top = rings[row];
            float dy = top.y - bottom.y, dr = top.radius - bottom.radius;
            float length = (float) Math.hypot(dy, dr);
            for (int face = 0; face < SIDES; face++) {
                double left = 2 * Math.PI * face / SIDES, right = 2 * Math.PI * (face + 1) / SIDES;
                double normal = (left + right) / 2;
                float nx = (float) (-Math.sin(normal) * dy / length);
                float ny = -dr / length;
                float nz = (float) (-Math.cos(normal) * dy / length);
                addVertex(vertices, x, z, atlas, bottom, left, face, nx, ny, nz);
                addVertex(vertices, x, z, atlas, bottom, right, face + 1, nx, ny, nz);
                addVertex(vertices, x, z, atlas, top, right, face + 1, nx, ny, nz);
                addVertex(vertices, x, z, atlas, top, left, face, nx, ny, nz);
            }
        }
    }

    private static void addVertex(List<Vertex> vertices, float x, float z, Strip atlas, Ring ring, double angle, int edge,
                                  float nx, float ny, float nz) {
        float u = (atlas.left + (atlas.right - atlas.left) * edge / SIDES) / TEXTURE_SIZE;
        float v = (atlas.bottom + (atlas.top - atlas.bottom) * ring.y / atlas.height) / TEXTURE_SIZE;
        vertices.add(new Vertex(x - (float) Math.sin(angle) * ring.radius, ring.y, z - (float) Math.cos(angle) * ring.radius,
                u, v, nx, ny, nz));
    }

    private record Ring(float y, float radius) {}
    private record Strip(float left, float right, float top, float bottom, float height) {}
    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}
}
