package net.createsatellites;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.createsatellites.block.GroundConsoleBlock;
import net.createsatellites.block.GroundConsoleBlockEntity;
import net.createsatellites.block.LaunchPadBlock;
import net.createsatellites.block.LaunchPadBlockEntity;
import net.createsatellites.compat.SatelliteDisplaySource;
import net.createsatellites.entity.RocketEntity;
import net.createsatellites.item.SatelliteItem;
import net.createsatellites.orbit.SatelliteData;
import net.createsatellites.orbit.SatellitesPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreateSatellites.MOD_ID)
public class CreateSatellites {
    public static final String MOD_ID = "create_satellites_n_rockets";

    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Fluids a rocket burns: Create: Refinery 'n' Reactors fuels are listed in the tag as optional entries. */
    public static final TagKey<Fluid> ROCKET_FUEL = TagKey.create(Registries.FLUID, id("rocket_fuel"));

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    static final DeferredRegister<DisplaySource> DISPLAY_SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 12f).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = BLOCKS.registerBlock("launch_pad", LaunchPadBlock::new, metal());
    public static final DeferredBlock<GroundConsoleBlock> GROUND_CONSOLE = BLOCKS.registerBlock("ground_console", GroundConsoleBlock::new, metal());

    public static final DeferredItem<SatelliteItem> SATELLITE = ITEMS.registerItem("satellite", SatelliteItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD_BE =
            BLOCK_ENTITIES.register("launch_pad", () -> BlockEntityType.Builder.of(LaunchPadBlockEntity::new, LAUNCH_PAD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundConsoleBlockEntity>> GROUND_CONSOLE_BE =
            BLOCK_ENTITIES.register("ground_console", () -> BlockEntityType.Builder.of(GroundConsoleBlockEntity::new, GROUND_CONSOLE.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(1f, 5f)
                    .clientTrackingRange(16).updateInterval(1).fireImmune().build(MOD_ID + ":rocket"));

    public static final DeferredHolder<DisplaySource, SatelliteDisplaySource> SATELLITE_SOURCE =
            DISPLAY_SOURCES.register("satellite", SatelliteDisplaySource::new);

    static {
        ITEMS.registerSimpleBlockItem(LAUNCH_PAD);
        ITEMS.registerSimpleBlockItem(GROUND_CONSOLE);
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + MOD_ID))
                .icon(() -> SATELLITE.get().getDefaultInstance())
                .displayItems((p, out) -> ITEMS.getEntries().forEach(i -> out.accept((Item) i.get())))
                .build());
    }

    public CreateSatellites(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        DISPLAY_SOURCES.register(bus);
        bus.addListener(RegisterCapabilitiesEvent.class, e ->
                e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LAUNCH_PAD_BE.get(), (be, side) -> be.tank));
        bus.addListener(RegisterPayloadHandlersEvent.class, e ->
                e.registrar("1").playToClient(SatellitesPayload.TYPE, SatellitesPayload.CODEC, SatellitesPayload::handle));
        bus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() ->
                DisplaySource.BY_BLOCK.add(GROUND_CONSOLE.get(), SATELLITE_SOURCE.get())));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)
                PacketDistributor.sendToPlayer(p, new SatellitesPayload(SatelliteData.get(p.server).list()));
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.RegisterCommandsEvent.class, e -> SatelliteCommand.register(e.getDispatcher()));
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) net.createsatellites.client.ClientSetup.init(bus);
    }
}
