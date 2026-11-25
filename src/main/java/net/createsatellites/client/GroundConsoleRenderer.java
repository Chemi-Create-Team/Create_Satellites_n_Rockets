package net.createsatellites.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createsatellites.CreateSatellites;
import net.createsatellites.block.ConsoleButton;
import net.createsatellites.block.GroundConsoleBlock;
import net.createsatellites.block.GroundConsoleBlockEntity;
import net.createsatellites.orbit.OrbitMath;
import net.createsatellites.orbit.Satellite;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** The live parts of the console: the readout, mode lamp, dial scales and the cover of the deorbit button. */
public class GroundConsoleRenderer implements BlockEntityRenderer<GroundConsoleBlockEntity> {
    private static final ResourceLocation WHITE = CreateSatellites.id("textures/misc/white.png");
    private static final float TEXT_SCALE = 0.0065f;
    private static final int[] MODE_COLORS = {0xFFB020, 0x30FF60, 0x40E0FF};
    private final Font font;

    public GroundConsoleRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.getFont();
    }

    @Override
    public void render(GroundConsoleBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        ps.pushPose();
        ps.translate(.5, GroundConsoleBlock.TOP + .003, .5);
        ps.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(GroundConsoleBlock.FACING).toYRot()));
        Satellite s = be.selected();
        VertexConsumer q = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        Matrix4f m = ps.last().pose();
        if (s != null) {
            int c = MODE_COLORS[s.mode()];
            quad(q, ps, ConsoleButton.MODE.u, ConsoleButton.MODE.v, .05f, c, 255, 0);
            scale(q, ps, ConsoleButton.ALTITUDE, s.tier());
            scale(q, ps, ConsoleButton.INCLINATION, s.incl());
        }
        boolean open = be.guardOpen();
        if (open) quad(q, ps, ConsoleButton.DEORBIT.u, ConsoleButton.DEORBIT.v, .07f, 0xFF2020, 255, 0);
        else quad(q, ps, ConsoleButton.DEORBIT.u, ConsoleButton.DEORBIT.v, .1f, 0xFF3030, 110, .02f);

        long now = level.getGameTime();
        ps.pushPose();
        ps.translate(-.42, 0, .5 - .95);
        ps.mulPose(Axis.XP.rotationDegrees(90));
        ps.scale(TEXT_SCALE, TEXT_SCALE, -TEXT_SCALE);
        int y = 0;
        for (var line : lines(be, s, now + partial)) {
            font.drawInBatch(line.getVisualOrderText(), 0, y, 0x55FF77, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            y += 9;
        }
        ps.popPose();
        ps.popPose();
    }

    private static java.util.List<Component> lines(GroundConsoleBlockEntity be, Satellite s, double tick) {
        String k = "console.create_satellites_n_rockets.";
        if (s == null) return java.util.List.of(Component.translatable(k + "none"), Component.translatable(k + "none_hint"));
        double[] p = OrbitMath.subpoint(s, tick);
        int[] block = OrbitMath.groundBlock(p);
        var pos = be.getBlockPos();
        var look = OrbitMath.lookFromBlock(s, tick, pos.getX() + .5, pos.getZ() + .5);
        return java.util.List.of(
                Component.translatable(k + "title", s.name(), be.selectedIndex() + 1, be.count()),
                Component.translatable(k + "pos", OrbitMath.fmtLat(p[0]), OrbitMath.fmtLon(p[1])),
                Component.translatable(k + "block", block[0], block[1]),
                Component.translatable(k + "orbit", s.altKm(), OrbitMath.INCLINATION_DEG[s.incl()], String.format("%.1f", s.periodTicks() / 1200.0)),
                Component.translatable(k + "mode", Component.translatable(k + "mode." + s.mode())),
                look.elevation() > 0
                        ? Component.translatable(k + "above", (int) Math.toDegrees(look.elevation()), (int) Math.floorMod((long) Math.toDegrees(look.azimuth()), 360L))
                        : Component.translatable(k + "below"),
                Component.translatable(k + "owner", s.ownerName()));
    }

    /** Four ticks above a dial, lit up to the current setting. */
    private static void scale(VertexConsumer q, PoseStack ps, ConsoleButton dial, int value) {
        for (int i = 0; i < 4; i++)
            quad(q, ps, dial.u + (i - 1.5) * .07, dial.v + .17, .025f, i <= value ? 0xFFD050 : 0x403018, 255, 0);
    }

    /** A square lying on the console top, centred on the operator-frame point (u, v). */
    private static void quad(VertexConsumer q, PoseStack ps, double u, double v, float half, int rgb, int alpha, float lift) {
        float x = (float) (u - .5), z = (float) (.5 - v);
        var pose = ps.last();
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float[][] c = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        for (float[] k : c)
            q.addVertex(pose, x + k[0] * half, lift, z + k[1] * half).setColor(r, g, b, alpha).setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
