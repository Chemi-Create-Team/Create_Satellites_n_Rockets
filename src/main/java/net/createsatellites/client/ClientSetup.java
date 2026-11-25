package net.createsatellites.client;

import net.createsatellites.CreateSatellites;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring; only loaded on the client. */
public final class ClientSetup {
    public static final ModelLayerLocation ROCKET_LAYER = new ModelLayerLocation(CreateSatellites.id("rocket"), "main");

    private ClientSetup() {}

    public static void init(IEventBus bus) {
        bus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, e -> e.registerLayerDefinition(ROCKET_LAYER, RocketModel::createLayer));
        bus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> {
            e.registerEntityRenderer(CreateSatellites.ROCKET.get(), RocketRenderer::new);
            e.registerBlockEntityRenderer(CreateSatellites.GROUND_CONSOLE_BE.get(), GroundConsoleRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, e -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) NightSky.render(e);
        });
    }
}
