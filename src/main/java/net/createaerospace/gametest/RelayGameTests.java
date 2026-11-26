package net.createaerospace.gametest;

import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.api.AerospaceApi;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
import net.createaerospace.photo.OrbitalPhoto;
import net.createaerospace.relay.OrbitalDishEntity;
import net.createaerospace.relay.RelayStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateAerospace.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RelayGameTests {
    @GameTest(template = "test_empty", batch = "cae_relay")
    public static void relayInventoryPersistsAndCanBeRecoveredAfterDeorbit(GameTestHelper h) {
        var level = h.getLevel();
        var first = new BlockPos(64, 96, 64);
        var second = first.east(24000);
        var satellite = Satellite.deployed(UUID.randomUUID(), "Relay test", UUID.randomUUID(), "Operator",
                OrbitMath.lonOf(first.getX()), level.getGameTime(), CreateAerospace.COMMUNICATION_PAYLOAD.toString());
        var orbits = SatelliteData.get(level.getServer());
        orbits.put(level.getServer(), satellite);
        h.assertTrue(AerospaceApi.payloadOf(new ItemStack(CreateAerospace.COMMUNICATION_SATELLITE.get())).equals(CreateAerospace.COMMUNICATION_PAYLOAD),
                "The communications satellite uses the existing launch-pad payload path");
        h.assertTrue(BuiltInRegistries.BLOCK.getKey(CreateAerospace.DISH.get())
                .equals(ResourceLocation.parse("create_frontier_science:me_parabolic_antenna")), "The dish keeps its saved block ID");

        BlockPos placed = new BlockPos(2, 2, 2);
        h.setBlock(placed, CreateAerospace.DISH.get());
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(placed), Direction.DOWN)
                == ((OrbitalDishEntity) h.getBlockEntity(placed)).items, "The placed block exposes its item handler to automation");

        try {
            // Load persisted station state at both coordinates without generating remote terrain in GameTest.
            var a = station(level, first, satellite.id()).items;
            var b = station(level, second, satellite.id()).items;
            var items = new ItemStack(Items.IRON_INGOT, 24);
            h.assertTrue(a.insertItem(0, items, true).isEmpty() && b.getStackInSlot(0).isEmpty(),
                    "Simulation reports capacity without transferring anything");
            h.assertTrue(a.insertItem(0, items, false).isEmpty() && b.getStackInSlot(0).getCount() == 24,
                    "The shared inventory is accessible from stations 24,000 blocks apart");

            var storage = RelayStorage.get(level.getServer());
            var restored = RelayStorage.load(storage.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            h.assertTrue(restored.find(satellite.id()).items().getStackInSlot(0).getCount() == 24, "Shared inventory survives serialisation");
            orbits.put(level.getServer(), satellite.withMode(Satellite.STANDBY));
            h.assertTrue(b.getStackInSlot(0).isEmpty() && a.insertItem(1, items, true).getCount() == 24,
                    "Standby refuses both extraction and simulated insertion");

            orbits.put(level.getServer(), satellite.reenter(level.getGameTime(), 0, 0));
            h.assertTrue(b.extractItem(0, 24, true).getCount() == 24 && a.insertItem(0, items, false).getCount() == 24,
                    "Once reentry starts, inventory is available for withdrawal only");
            orbits.remove(level.getServer(), satellite.id());
            h.assertTrue(b.extractItem(0, 24, false).getCount() == 24 && a.getStackInSlot(0).isEmpty(),
                    "Inventory stays available for recovery after deorbit");
            h.succeed();
        } finally {
            orbits.remove(level.getServer(), satellite.id());
        }
    }

    @GameTest(template = "test_empty", batch = "cae_photo")
    public static void thePhotoReadsActualWorldSurface(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(BlockPos.ZERO);
        var sample = origin.atY(160);
        level.setBlockAndUpdate(sample, Blocks.RED_CONCRETE.defaultBlockState());
        try {
            byte[] pixels = OrbitalPhoto.capture(level, origin);
            int middle = ((OrbitalPhoto.SIZE / 2) * OrbitalPhoto.SIZE + OrbitalPhoto.SIZE / 2) * 3;
            int red = Byte.toUnsignedInt(pixels[middle]), green = Byte.toUnsignedInt(pixels[middle + 1]),
                    blue = Byte.toUnsignedInt(pixels[middle + 2]);
            h.assertTrue(pixels.length == OrbitalPhoto.BYTES && red > 100 && red > 2 * green && red > 2 * blue,
                    "The block at the centre of the camera appears as a red surface pixel, got " + red + "/" + green + "/" + blue);
        } finally {
            level.removeBlock(sample, false);
        }
        h.succeed();
    }

    private static OrbitalDishEntity station(ServerLevel level, BlockPos pos, UUID target) {
        var dish = new OrbitalDishEntity(pos, CreateAerospace.DISH.get().defaultBlockState());
        dish.setLevel(level);
        var tag = new CompoundTag();
        tag.putUUID("RelayTarget", target);
        dish.loadWithComponents(tag, level.registryAccess());
        return dish;
    }
}
