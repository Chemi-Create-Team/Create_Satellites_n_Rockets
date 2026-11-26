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
        });
        bus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, e -> e.enqueueWork(net.createaerospace.compat.cfs.CfsCompat::registerClient));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent.class, ClientSetup::tooltip);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, e -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) NightSky.render(e);
        });
    }

    /** Mass on every payload and the numbers on every rocket, CAE's and those of other mods alike. */
    private static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) {
        var gray = net.minecraft.ChatFormatting.GRAY;
        int kg = net.createaerospace.api.AerospaceApi.massKg(e.getItemStack());
        if (kg > 0) e.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.create_aerospace_engineering.mass", kg).withStyle(gray));
        var rocket = net.createaerospace.api.RocketApi.rocket(net.createaerospace.api.RocketApi.rocketOf(e.getItemStack()));
        if (rocket != null) e.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.create_aerospace_engineering.rocket",
                rocket.type().maxPayloadKg(), rocket.type().fuelMb()).withStyle(gray));
    }
}
