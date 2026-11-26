package net.createaerospace;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.createaerospace.relay.OrbitalDishBlock;
import net.createaerospace.relay.OrbitalDishEntity;
import net.createaerospace.block.GroundConsoleBlock;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.block.LaunchPadBlock;
import net.createaerospace.block.LaunchPadBlockEntity;
import net.createaerospace.compat.SatelliteDisplaySource;
import net.createaerospace.entity.RocketEntity;
import net.createaerospace.item.SatelliteItem;
import net.createaerospace.orbit.SatelliteData;
import net.createaerospace.orbit.SatellitesPayload;
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

@Mod(CreateAerospace.MOD_ID)
public class CreateAerospace {
    public static final String MOD_ID = "create_aerospace_engineering";
    public static final float STANDARD_ROCKET_WIDTH = 10f, STANDARD_ROCKET_HEIGHT = 60f;

    /** Whether rockets can fail ({@code RocketApi.RocketType.failureChance}); on by default. */
    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> LAUNCH_FAILURES =
            net.minecraft.world.level.GameRules.register("caeLaunchFailures", net.minecraft.world.level.GameRules.Category.MISC,
                    net.minecraft.world.level.GameRules.BooleanValue.create(true));

    /** Whether communications satellites can take photos of the ground from the console map; on by default. */
    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> SATELLITE_PHOTOS =
            net.minecraft.world.level.GameRules.register("caeSatellitePhotos", net.minecraft.world.level.GameRules.Category.MISC,
                    net.minecraft.world.level.GameRules.BooleanValue.create(true));

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
    public static final DeferredBlock<net.createaerospace.block.ServiceGantryBlock> SERVICE_GANTRY =
            BLOCKS.registerBlock("service_gantry", net.createaerospace.block.ServiceGantryBlock::new, metal());

    // Keep the relocated block, item and block-entity IDs so installed dishes survive the move.
    private static final String DISH_ID = "me_parabolic_antenna";
    private static final DeferredRegister.Blocks RELOCATED_BLOCKS = DeferredRegister.createBlocks("create_frontier_science");
    private static final DeferredRegister.Items RELOCATED_ITEMS = DeferredRegister.createItems("create_frontier_science");
    private static final DeferredRegister<BlockEntityType<?>> RELOCATED_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "create_frontier_science");
    public static final DeferredBlock<OrbitalDishBlock> DISH = RELOCATED_BLOCKS.register(DISH_ID, OrbitalDishBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrbitalDishEntity>> DISH_BE =
            RELOCATED_ENTITIES.register(DISH_ID, () -> BlockEntityType.Builder.of(OrbitalDishEntity::new, DISH.get()).build(null));
    public static final ResourceLocation COMMUNICATION_PAYLOAD = id("communication_satellite");
    public static final DeferredItem<Item> COMMUNICATION_SATELLITE = ITEMS.registerSimpleItem("communication_satellite",
            new Item.Properties().stacksTo(4));

    public static final DeferredItem<SatelliteItem> SATELLITE = ITEMS.registerItem("satellite", SatelliteItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<Item> ROCKET_ITEM = ITEMS.registerSimpleItem("rocket", new Item.Properties().stacksTo(4));
    /** In the order of {@code rocket.H3.Variant}. */
    public static final java.util.List<DeferredItem<Item>> H3_ITEMS = java.util.Arrays.stream(net.createaerospace.rocket.H3.Variant.values())
            .map(v -> ITEMS.registerSimpleItem(v.path, new Item.Properties().stacksTo(1))).toList();
    public static final DeferredItem<Item> FALCON_9 = ITEMS.registerSimpleItem("falcon_9", new Item.Properties().stacksTo(1));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD_BE =
            BLOCK_ENTITIES.register("launch_pad", () -> BlockEntityType.Builder.of(LaunchPadBlockEntity::new, LAUNCH_PAD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundConsoleBlockEntity>> GROUND_CONSOLE_BE =
            BLOCK_ENTITIES.register("ground_console", () -> BlockEntityType.Builder.of(GroundConsoleBlockEntity::new, GROUND_CONSOLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<net.createaerospace.block.ServiceGantryBlockEntity>> SERVICE_GANTRY_BE =
            BLOCK_ENTITIES.register("service_gantry", () -> BlockEntityType.Builder.of(net.createaerospace.block.ServiceGantryBlockEntity::new, SERVICE_GANTRY.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(STANDARD_ROCKET_WIDTH, STANDARD_ROCKET_HEIGHT)
                    .clientTrackingRange(16).updateInterval(1).fireImmune().build(MOD_ID + ":rocket"));

    public static final DeferredHolder<DisplaySource, SatelliteDisplaySource> SATELLITE_SOURCE =
            DISPLAY_SOURCES.register("satellite", SatelliteDisplaySource::new);

    static {
        var dishItem = RELOCATED_ITEMS.registerSimpleBlockItem(DISH);
        ITEMS.registerSimpleBlockItem(LAUNCH_PAD);
        ITEMS.registerSimpleBlockItem(GROUND_CONSOLE);
        ITEMS.registerSimpleBlockItem(SERVICE_GANTRY);
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + MOD_ID))
                .icon(() -> SATELLITE.get().getDefaultInstance())
                .displayItems((p, out) -> {
                    ITEMS.getEntries().forEach(i -> out.accept((Item) i.get()));
                    out.accept(dishItem.get());
                })
                .build());
    }

    public CreateAerospace(IEventBus bus) {
        RELOCATED_BLOCKS.register(bus);
        RELOCATED_ITEMS.register(bus);
        RELOCATED_ENTITIES.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        DISPLAY_SOURCES.register(bus);
        bus.addListener(RegisterCapabilitiesEvent.class, e -> {
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LAUNCH_PAD_BE.get(), (be, side) -> be.tank);
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DISH_BE.get(), (be, side) -> be.items);
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SERVICE_GANTRY_BE.get(), (be, side) -> be.parts);
        });
        bus.addListener(RegisterPayloadHandlersEvent.class, net.createaerospace.photo.PhotoPackets::register);
        bus.addListener(RegisterPayloadHandlersEvent.class, net.createaerospace.block.ConsoleSelect::register);
        bus.addListener(RegisterPayloadHandlersEvent.class, e ->
                e.registrar("2").playToClient(SatellitesPayload.TYPE, SatellitesPayload.CODEC, SatellitesPayload::handle));
        bus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() ->
                {
            net.createaerospace.api.RocketApi.registerRocket(net.createaerospace.api.RocketApi.STANDARD, ROCKET_ITEM.get(),
                    new net.createaerospace.api.RocketApi.RocketType(Component.translatable("item." + MOD_ID + ".rocket"), 1000, 4000, 200, 0.03, 2.5, STANDARD_ROCKET_WIDTH, STANDARD_ROCKET_HEIGHT));
            net.createaerospace.rocket.H3.register(H3_ITEMS);
            net.createaerospace.rocket.Falcon9.register(FALCON_9.get());
            net.createaerospace.api.AerospaceApi.registerPayload(COMMUNICATION_PAYLOAD, COMMUNICATION_SATELLITE.get(),
                    () -> Component.translatable("item." + MOD_ID + ".communication_satellite"));
            DisplaySource.BY_BLOCK.add(GROUND_CONSOLE.get(), SATELLITE_SOURCE.get());
            net.createaerospace.compat.cfs.CfsCompat.register();
        }));
        NeoForge.EVENT_BUS.addListener(net.createaerospace.photo.PhotoPackets::tick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)
                PacketDistributor.sendToPlayer(p, new SatellitesPayload(SatelliteData.get(p.server).list(), SatelliteData.get(p.server).alerts()));
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, e -> {
            long now = e.getServer().overworld().getGameTime();
            if (now % 20 == 0) SatelliteData.get(e.getServer()).tick(e.getServer(), now);
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.RegisterCommandsEvent.class, e -> SatelliteCommand.register(e.getDispatcher()));
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) net.createaerospace.client.ClientSetup.init(bus);
    }
}
