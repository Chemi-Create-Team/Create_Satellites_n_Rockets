package net.createsatellites;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreateSatellites.MOD_ID)
public class CreateSatellites {
    public static final String MOD_ID = "create_satellites_n_rockets";

    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 12f).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<Block> LAUNCH_PAD = BLOCKS.registerSimpleBlock("launch_pad", metal());
    public static final DeferredBlock<RocketEngineBlock> ROCKET_ENGINE = BLOCKS.registerBlock("rocket_engine",
            RocketEngineBlock::new, metal().lightLevel(s -> s.getValue(RocketEngineBlock.LIT) ? 15 : 0));
    public static final DeferredBlock<LaunchControllerBlock> LAUNCH_CONTROLLER = BLOCKS.registerBlock("launch_controller",
            LaunchControllerBlock::new, metal());
    public static final DeferredBlock<SatelliteCoreBlock> SATELLITE_CORE = BLOCKS.registerBlock("satellite_core",
            SatelliteCoreBlock::new, metal());
    public static final DeferredBlock<StationCoreBlock> STATION_CORE = BLOCKS.registerBlock("station_core",
            StationCoreBlock::new, metal());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RocketEngineBlockEntity>> ROCKET_ENGINE_BE =
            BLOCK_ENTITIES.register("rocket_engine", () -> BlockEntityType.Builder.of(RocketEngineBlockEntity::new, ROCKET_ENGINE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationCoreBlockEntity>> STATION_CORE_BE =
            BLOCK_ENTITIES.register("station_core", () -> BlockEntityType.Builder.of(StationCoreBlockEntity::new, STATION_CORE.get()).build(null));

    static {
        for (var b : new DeferredBlock<?>[] {LAUNCH_PAD, ROCKET_ENGINE, LAUNCH_CONTROLLER, SATELLITE_CORE, STATION_CORE}) {
            ITEMS.registerSimpleBlockItem(b);
        }
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + MOD_ID))
                .icon(() -> ROCKET_ENGINE.get().asItem().getDefaultInstance())
                .displayItems((p, out) -> ITEMS.getEntries().forEach(i -> out.accept((Item) i.get())))
                .build());
    }

    public CreateSatellites(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        bus.addListener(RegisterCapabilitiesEvent.class, e ->
                e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ROCKET_ENGINE_BE.get(), (be, side) -> be.tank));
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, e -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel l) Orbit.tick(l);
        });
    }
}
