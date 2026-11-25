package net.createaerospace.client;

import net.createaerospace.CreateAerospace;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring; only loaded on the client. */
public final class ClientSetup {
    public static final ModelLayerLocation ROCKET_LAYER = new ModelLayerLocation(CreateAerospace.id("rocket"), "main");

    private ClientSetup() {}

    public static void init(IEventBus bus) {
        bus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, e -> e.registerLayerDefinition(ROCKET_LAYER, RocketModel::createLayer));
        bus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> {
            e.registerEntityRenderer(CreateAerospace.ROCKET.get(), RocketRenderer::new);
            e.registerBlockEntityRenderer(CreateAerospace.GROUND_CONSOLE_BE.get(), GroundConsoleRenderer::new);
            e.registerBlockEntityRenderer(CreateAerospace.ASSEMBLY_JIG_BE.get(), AssemblyJigRenderer::new);
            e.registerBlockEntityRenderer(CreateAerospace.INDUSTRIAL_ARM_BE.get(), IndustrialArmRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, e -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) NightSky.render(e);
        });
    }
}
