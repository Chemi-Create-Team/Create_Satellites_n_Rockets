package net.createaerospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.ConsoleButton;
import net.createaerospace.block.GroundConsoleBlock;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteReadout;
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
    private static final ResourceLocation WHITE = CreateAerospace.id("textures/misc/white.png");
    private static final float TEXT_SCALE = 0.0065f;
    /** The screen is .84 of a block wide, in text pixels. */
    private static final float MAX_LINE_WIDTH = .84f / TEXT_SCALE;
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
            // squeeze a line that would run past the screen instead of guessing at string lengths in every language
            float fit = Math.min(1f, MAX_LINE_WIDTH / Math.max(1, font.width(line)));
            ps.pushPose();
            ps.translate(0, y, 0);
            ps.scale(fit, fit, 1);
            font.drawInBatch(line.getVisualOrderText(), 0, 0, 0x55FF77, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            ps.popPose();
            y += 9;
        }
        ps.popPose();
        ps.popPose();
    }

    private static java.util.List<Component> lines(GroundConsoleBlockEntity be, Satellite s, double tick) {
        if (s == null) return SatelliteReadout.noneLines();
        return SatelliteReadout.of(s, be.selectedIndex(), be.count(), tick, be.getBlockPos()).lines();
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
