package net.createsatellites.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createsatellites.CreateSatellites;
import net.createsatellites.entity.RocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RocketRenderer extends EntityRenderer<RocketEntity> {
    private static final ResourceLocation TEXTURE = CreateSatellites.id("textures/entity/rocket.png");
    private final RocketModel model;

    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        model = new RocketModel(ctx.bakeLayer(ClientSetup.ROCKET_LAYER));
        shadowRadius = 0.5f;
    }

    @Override
    public void render(RocketEntity rocket, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        ps.scale(-1, -1, 1);
        ps.translate(0, -1.501, 0);
        model.renderToBuffer(ps, buffers.getBuffer(model.renderType(TEXTURE)), rocket.isAscending() ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY, -1);
        ps.popPose();
        super.render(rocket, yaw, partialTick, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity rocket) {
        return TEXTURE;
    }
}
