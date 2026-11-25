package net.createsatellites.gametest;

import net.createsatellites.CreateSatellites;
import net.createsatellites.block.ConsoleButton;
import net.createsatellites.block.GroundConsoleBlock;
import net.createsatellites.block.GroundConsoleBlockEntity;
import net.createsatellites.block.LaunchPadBlockEntity;
import net.createsatellites.entity.RocketEntity;
import net.createsatellites.orbit.Satellite;
import net.createsatellites.orbit.SatelliteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateSatellites.MOD_ID)
@PrefixGameTestTemplate(false)
public class RocketGameTests {
    private static final BlockPos PAD = new BlockPos(1, 12, 1);
    private static final BlockPos CONSOLE = new BlockPos(1, 2, 1);

    private static void loadRocket(GameTestHelper h, Player player) {
        h.setBlock(PAD, CreateSatellites.LAUNCH_PAD.get().defaultBlockState());
        ItemStack stack = new ItemStack(CreateSatellites.SATELLITE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(PAD);
        h.getLevel().getBlockState(abs).useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertTrue(stack.isEmpty(), "the satellite is used up when it is loaded");
    }

    private static java.util.List<RocketEntity> rockets(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(RocketEntity.class, new AABB(h.absolutePos(PAD)).inflate(3, 300, 3));
    }

    private static long mine(GameTestHelper h, Player p) {
        return SatelliteData.get(h.getLevel().getServer()).list().stream().filter(s -> s.owner().equals(p.getUUID())).count();
    }

    @GameTest(template = "test_empty", batch = "rocket_launch", timeoutTicks = 400)
    public static void redstoneFiresTheRocketAndItLeavesASatelliteInOrbit(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        loadRocket(h, p);
        h.assertTrue(rockets(h).size() == 1 && !rockets(h).get(0).isAscending(), "a rocket stands on the pad");
        var tank = ((LaunchPadBlockEntity) h.getBlockEntity(PAD)).tank;
        h.assertTrue(tank.fill(new FluidStack(Fluids.LAVA, 4000), IFluidHandler.FluidAction.EXECUTE) == 4000, "pad takes lava");
        h.setBlock(PAD.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.runAfterDelay(2, () -> {
            h.assertTrue(rockets(h).get(0).isAscending(), "rocket lifts off");
            h.assertTrue(tank.getFluidAmount() == 0, "the launch burnt 4000 mB");
        });
        h.runAfterDelay(30, () -> h.assertTrue(rockets(h).get(0).getY() > h.absolutePos(PAD).getY() + 5, "rocket climbs"));
        h.succeedWhen(() -> {
            h.assertTrue(rockets(h).isEmpty(), "the rocket is gone once it has released its satellite");
            h.assertTrue(mine(h, p) == 1, "exactly one satellite belongs to the loader, got " + mine(h, p));
            var s = SatelliteData.get(h.getLevel().getServer()).list().stream().filter(x -> x.owner().equals(p.getUUID())).findFirst().get();
            SatelliteData.get(h.getLevel().getServer()).remove(h.getLevel().getServer(), s.id());
        });
    }

    @GameTest(template = "test_empty", batch = "rocket_dry", timeoutTicks = 100)
    public static void aPadWithoutFuelDoesNotLaunch(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        loadRocket(h, p);
        var tank = ((LaunchPadBlockEntity) h.getBlockEntity(PAD)).tank;
        tank.fill(new FluidStack(Fluids.LAVA, 3999), IFluidHandler.FluidAction.EXECUTE);
        h.setBlock(PAD.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.runAfterDelay(20, () -> {
            h.assertTrue(rockets(h).size() == 1 && !rockets(h).get(0).isAscending(), "rocket stays on the pad");
            h.assertTrue(tank.getFluidAmount() == 3999, "no fuel burnt");
            h.assertTrue(mine(h, p) == 0, "nothing in orbit");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", batch = "rocket_pad", timeoutTicks = 100)
    public static void aPadHoldsOnlyOneRocketAndOnlyTakesFuel(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        loadRocket(h, p);
        ItemStack second = new ItemStack(CreateSatellites.SATELLITE.get());
        BlockPos abs = h.absolutePos(PAD);
        h.getLevel().getBlockState(abs).useItemOn(second, h.getLevel(), p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertTrue(rockets(h).size() == 1 && !second.isEmpty(), "a second satellite is not loaded onto an occupied pad");
        var tank = ((LaunchPadBlockEntity) h.getBlockEntity(PAD)).tank;
        h.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "water is refused");
        h.succeed();
    }

    @GameTest(template = "test_empty", batch = "rocket_break", timeoutTicks = 100)
    public static void breakingThePadGivesTheSatelliteBack(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        loadRocket(h, p);
        h.setBlock(PAD, Blocks.AIR.defaultBlockState());
        h.succeedWhen(() -> {
            h.assertTrue(rockets(h).isEmpty(), "the rocket is removed");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(PAD)).inflate(4, 4, 4),
                    i -> i.getItem().is(CreateSatellites.SATELLITE.get())).isEmpty(), "and the satellite drops");
        });
    }

    @GameTest(template = "test_empty")
    public static void buttonsAreFoundInTheOperatorsFrameWhicheverWayTheConsoleFaces(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            h.setBlock(CONSOLE, CreateSatellites.GROUND_CONSOLE.get().defaultBlockState().setValue(GroundConsoleBlock.FACING, facing));
            BlockPos abs = h.absolutePos(CONSOLE);
            var state = h.getLevel().getBlockState(abs);
            Direction right = facing.getCounterClockWise();
            for (ConsoleButton b : ConsoleButton.values()) {
                // u runs along the operator's right, v from the operator's edge away from them
                Vec3 hit = Vec3.atCenterOf(abs).add(right.getStepX() * (b.u - .5) + facing.getStepX() * (.5 - b.v), 0,
                        right.getStepZ() * (b.u - .5) + facing.getStepZ() * (.5 - b.v));
                h.assertTrue(GroundConsoleBlock.buttonAt(state, abs, hit) == b, b + " found facing " + facing);
            }
            Vec3 monitor = Vec3.atCenterOf(abs).add(facing.getStepX() * -.4, 0, facing.getStepZ() * -.4);
            h.assertTrue(GroundConsoleBlock.buttonAt(state, abs, monitor) == null, "the far edge is the screen, not a button");
        }
        h.succeed();
    }

    @GameTest(template = "test_empty", batch = "console")
    public static void theConsoleSelectsAndControlsSatellitesOfItsOwner(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var data = SatelliteData.get(server);
        Player owner = h.makeMockPlayer(GameType.SURVIVAL), stranger = h.makeMockPlayer(GameType.SURVIVAL);
        Satellite a = Satellite.deployed("A", owner.getUUID(), "owner", 0, 0), b = Satellite.deployed("B", owner.getUUID(), "owner", 40, 0);
        data.put(server, a);
        data.put(server, b);
        h.setBlock(CONSOLE, CreateSatellites.GROUND_CONSOLE.get().defaultBlockState());
        var c = (GroundConsoleBlockEntity) h.getBlockEntity(CONSOLE);
        for (int i = 0; i < 20 && !c.selected().id().equals(a.id()); i++) c.press(owner, ConsoleButton.NEXT, true);
        h.assertTrue(c.selected().id().equals(a.id()), "NEXT walks the list");
        c.press(owner, ConsoleButton.NEXT, true);
        h.assertTrue(c.selected().id().equals(b.id()), "NEXT selects the following satellite");
        c.press(owner, ConsoleButton.PREVIOUS, false);
        h.assertTrue(c.selected().id().equals(a.id()), "PREVIOUS goes back");

        c.press(stranger, ConsoleButton.MODE, true);
        h.assertTrue(data.find(a.id()).mode() == Satellite.BEACON, "a stranger cannot change the mode");
        c.press(owner, ConsoleButton.MODE, true);
        h.assertTrue(data.find(a.id()).mode() == Satellite.FLASH, "the owner can");
        h.assertTrue(data.find(a.id()).tier() == 3 && data.find(a.id()).incl() == 0, "a satellite starts geostationary");
        c.press(owner, ConsoleButton.ALTITUDE, true);
        h.assertTrue(data.find(a.id()).tier() == 3, "right half of the altitude dial stops at the top");
        c.press(owner, ConsoleButton.ALTITUDE, false);
        h.assertTrue(data.find(a.id()).tier() == 2, "left half steps down");
        c.press(owner, ConsoleButton.ALTITUDE, false);
        c.press(owner, ConsoleButton.ALTITUDE, false);
        c.press(owner, ConsoleButton.ALTITUDE, false);
        h.assertTrue(data.find(a.id()).tier() == 0, "and stops at the lowest");
        c.press(owner, ConsoleButton.INCLINATION, true);
        h.assertTrue(data.find(a.id()).incl() == 1, "inclination steps up");

        c.press(stranger, ConsoleButton.DEORBIT, true);
        c.press(stranger, ConsoleButton.DEORBIT, true);
        h.assertTrue(data.find(a.id()) != null, "a stranger cannot deorbit it");
        c.press(owner, ConsoleButton.DEORBIT, true);
        h.assertTrue(data.find(a.id()) != null && c.guardOpen(), "the first press only lifts the cover");
        c.press(owner, ConsoleButton.DEORBIT, true);
        h.assertTrue(data.find(a.id()) == null && !c.guardOpen(), "the second press deorbits it and closes the cover");
        h.assertTrue(c.summary().getString().length() > 0, "the display link has something to show");
        data.remove(server, b.id());
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void aPadKeepsItsFuelWhenSaved(GameTestHelper h) {
        h.setBlock(PAD, CreateSatellites.LAUNCH_PAD.get().defaultBlockState());
        var pad = (LaunchPadBlockEntity) h.getBlockEntity(PAD);
        pad.tank.fill(new FluidStack(Fluids.LAVA, 3500), IFluidHandler.FluidAction.EXECUTE);
        var access = h.getLevel().registryAccess();
        var saved = pad.saveWithoutMetadata(access);
        pad.tank.setFluid(FluidStack.EMPTY);
        pad.loadWithComponents(saved, access);
        h.assertTrue(pad.tank.getFluidAmount() == 3500 && pad.tank.getFluid().getFluid() == Fluids.LAVA, "fuel survives saving");
        h.succeed();
    }
}
