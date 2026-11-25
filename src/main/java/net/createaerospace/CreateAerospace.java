package net.createaerospace;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.createaerospace.assembly.AssemblyRecipes;
import net.createaerospace.assembly.ToolType;
import net.createaerospace.block.AssemblyJigBlock;
import net.createaerospace.block.AssemblyJigBlockEntity;
import net.createaerospace.block.IndustrialArmBlock;
import net.createaerospace.block.IndustrialArmBlockEntity;
import net.createaerospace.item.AssemblyToolItem;
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

    public static final DeferredBlock<AssemblyJigBlock> ASSEMBLY_JIG = BLOCKS.registerBlock("assembly_jig", AssemblyJigBlock::new, metal());
    public static final DeferredBlock<IndustrialArmBlock> INDUSTRIAL_ARM = BLOCKS.registerBlock("industrial_arm", IndustrialArmBlock::new, metal().noOcclusion());

    public static final java.util.Map<ToolType, DeferredItem<AssemblyToolItem>> TOOLS = new java.util.EnumMap<>(ToolType.class);
    static {
        for (ToolType t : ToolType.values()) TOOLS.put(t, ITEMS.registerItem(t.id(), p -> new AssemblyToolItem(t, p), new Item.Properties()));
    }
    public static final DeferredItem<Item> WELDED_IRON_BEAM = ITEMS.registerSimpleItem("welded_iron_beam");
    public static final DeferredItem<Item> MAGNETIC_CORE_MEMORY = ITEMS.registerSimpleItem("magnetic_core_memory");

    public static final DeferredItem<SatelliteItem> SATELLITE = ITEMS.registerItem("satellite", SatelliteItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD_BE =
            BLOCK_ENTITIES.register("launch_pad", () -> BlockEntityType.Builder.of(LaunchPadBlockEntity::new, LAUNCH_PAD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundConsoleBlockEntity>> GROUND_CONSOLE_BE =
            BLOCK_ENTITIES.register("ground_console", () -> BlockEntityType.Builder.of(GroundConsoleBlockEntity::new, GROUND_CONSOLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyJigBlockEntity>> ASSEMBLY_JIG_BE =
            BLOCK_ENTITIES.register("assembly_jig", () -> BlockEntityType.Builder.of(AssemblyJigBlockEntity::new, ASSEMBLY_JIG.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialArmBlockEntity>> INDUSTRIAL_ARM_BE =
            BLOCK_ENTITIES.register("industrial_arm", () -> BlockEntityType.Builder.of(IndustrialArmBlockEntity::new, INDUSTRIAL_ARM.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(1f, 5f)
                    .clientTrackingRange(16).updateInterval(1).fireImmune().build(MOD_ID + ":rocket"));

    public static final DeferredHolder<DisplaySource, SatelliteDisplaySource> SATELLITE_SOURCE =
            DISPLAY_SOURCES.register("satellite", SatelliteDisplaySource::new);

    static {
        ITEMS.registerSimpleBlockItem(LAUNCH_PAD);
        ITEMS.registerSimpleBlockItem(GROUND_CONSOLE);
        ITEMS.registerSimpleBlockItem(ASSEMBLY_JIG);
        ITEMS.registerSimpleBlockItem(INDUSTRIAL_ARM);
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + MOD_ID))
                .icon(() -> SATELLITE.get().getDefaultInstance())
                .displayItems((p, out) -> ITEMS.getEntries().forEach(i -> out.accept((Item) i.get())))
                .build());
    }

    public CreateAerospace(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        DISPLAY_SOURCES.register(bus);
        bus.addListener(RegisterCapabilitiesEvent.class, e -> {
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LAUNCH_PAD_BE.get(), (be, side) -> be.tank);
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ASSEMBLY_JIG_BE.get(), (be, side) -> be.tank);
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, INDUSTRIAL_ARM_BE.get(), (be, side) -> be.tank);
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, INDUSTRIAL_ARM_BE.get(), (be, side) -> be.toolSlot);
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.AddReloadListenerEvent.class, e -> e.addListener(new AssemblyRecipes()));
        bus.addListener(RegisterPayloadHandlersEvent.class, e ->
                e.registrar("1").playToClient(SatellitesPayload.TYPE, SatellitesPayload.CODEC, SatellitesPayload::handle));
        bus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() ->
                {
            DisplaySource.BY_BLOCK.add(GROUND_CONSOLE.get(), SATELLITE_SOURCE.get());
            com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(INDUSTRIAL_ARM.get(), () -> 4.0);
        }));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)
                PacketDistributor.sendToPlayer(p, new SatellitesPayload(SatelliteData.get(p.server).list()));
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.RegisterCommandsEvent.class, e -> SatelliteCommand.register(e.getDispatcher()));
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) net.createaerospace.client.ClientSetup.init(bus);
    }
}
