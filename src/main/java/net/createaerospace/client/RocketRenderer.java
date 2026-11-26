package net.createaerospace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createaerospace.CreateAerospace;
import net.createaerospace.api.client.RocketRenderers;
import net.createaerospace.entity.RocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RocketRenderer extends EntityRenderer<RocketEntity> {
    private static final ResourceLocation TEXTURE = CreateAerospace.id("textures/entity/rocket.png");
    private static final java.util.Set<ResourceLocation> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final RocketModel model;

    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        model = new RocketModel(ctx.bakeLayer(ClientSetup.ROCKET_LAYER));
        shadowRadius = 0.5f;
    }

    @Override
    public void render(RocketEntity rocket, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        var custom = RocketRenderers.get(rocket.typeId());
        if (custom != null) {
            var t = rocket.type().type();
            ps.pushPose();
            try {
                custom.render(new RocketRenderers.Context(rocket.typeId(), rocket.rocketItem(), rocket.isAscending(), rocket.ascentTicks(),
                        rocket.loadedKg(), t.maxPayloadKg(), rocket.payloadCount(), rocket.getBbWidth(), rocket.getBbHeight(),
                        rocket.level().getGameTime(), partialTick, ps, buffers, light));
            } catch (RuntimeException e) {
                if (FAILED.add(rocket.typeId())) CreateAerospace.LOGGER.error("Renderer of rocket {} failed", rocket.typeId(), e);
            }
            ps.popPose();
            super.render(rocket, yaw, partialTick, ps, buffers, light);
            return;
        }
        ps.pushPose();
        ps.scale(-1, -1, 1);
        ps.translate(0, -1.501, 0);
        model.renderToBuffer(ps, buffers.getBuffer(model.renderType(TEXTURE)), rocket.isAscending() ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY, -1);
        ps.popPose();
        super.render(rocket, yaw, partialTick, ps, buffers, light);
    }

    /** Client tick of a climbing rocket. */
    public static void exhaust(RocketEntity rocket) {
        var custom = RocketRenderers.get(rocket.typeId());
        var level = (net.minecraft.client.multiplayer.ClientLevel) rocket.level();
        try {
            if (custom != null) custom.exhaust(level, rocket.getX(), rocket.getY(), rocket.getZ(), rocket.ascentTicks(), rocket.getRandom());
            else RocketRenderers.defaultExhaust(level, rocket.getX(), rocket.getY(), rocket.getZ(), rocket.getRandom());
        } catch (RuntimeException e) {
            if (FAILED.add(rocket.typeId())) CreateAerospace.LOGGER.error("Exhaust of rocket {} failed", rocket.typeId(), e);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity rocket) {
        return TEXTURE;
    }
}
