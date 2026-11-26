package net.createaerospace.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import net.createaerospace.CreateAerospace;
import net.createaerospace.client.globe.CubeEarth;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** The cube Earth, built once from {@code textures/gui/land_mask.png}, and the sinks that draw it. */
public final class Globes {
    private static final ResourceLocation MASK = CreateAerospace.id("textures/gui/land_mask.png");
    private static CubeEarth globe;

    private Globes() {}

    public static synchronized CubeEarth get() {
        if (globe == null) globe = new CubeEarth(land());
        return globe;
    }

    private static CubeEarth.Land land() {
        try (var in = Minecraft.getInstance().getResourceManager().open(MASK); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            boolean[] land = new boolean[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) land[y * w + x] = (img.getPixelRGBA(x, y) & 255) > 127;
            return (lat, lon) -> land[Math.min(h - 1, Math.max(0, (int) ((90 - lat) / 180 * h))) * w + Math.min(w - 1, Math.max(0, (int) ((lon + 180) / 360 * w)))];
        } catch (IOException | RuntimeException e) {
            CreateAerospace.LOGGER.warn("Could not read {}, the Earth is all sea", MASK, e);
            return (lat, lon) -> false;
        }
    }

    /** Quads into a vertex consumer (position and colour, no culling); a depth {@code d} becomes {@code zBase + d * zScale}. */
    public static CubeEarth.Sink sink(VertexConsumer vc, Matrix4f m, float zBase, float zScale) {
        return (x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, argb) -> {
            vc.addVertex(m, x0, y0, zBase + z0 * zScale).setColor(argb);
            vc.addVertex(m, x1, y1, zBase + z1 * zScale).setColor(argb);
            vc.addVertex(m, x2, y2, zBase + z2 * zScale).setColor(argb);
            vc.addVertex(m, x3, y3, zBase + z3 * zScale).setColor(argb);
        };
    }
}
