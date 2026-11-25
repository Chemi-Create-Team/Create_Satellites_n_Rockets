package net.createsatellites.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import net.createsatellites.CreateSatellites;
import net.createsatellites.LaunchControllerBlock;
import net.createsatellites.Orbit;
import net.createsatellites.RocketEngineBlock;
import net.createsatellites.RocketEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder(CreateSatellites.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrbitGameTests {
    private static final BlockPos AIR = new BlockPos(1, 12, 1);

    /** pad, engine (nozzle down), iron block, controller, standing in mid-air so it can fall. */
    private static BlockPos buildRocket(GameTestHelper h, boolean fuel) {
        h.setBlock(AIR, CreateSatellites.LAUNCH_PAD.get().defaultBlockState());
        h.setBlock(AIR.above(), CreateSatellites.ROCKET_ENGINE.get().defaultBlockState().setValue(RocketEngineBlock.FACING, Direction.DOWN));
        h.setBlock(AIR.above(2), Blocks.IRON_BLOCK.defaultBlockState());
        h.setBlock(AIR.above(3), CreateSatellites.LAUNCH_CONTROLLER.get().defaultBlockState());
        var engine = (RocketEngineBlockEntity) h.getBlockEntity(AIR.above());
        if (fuel) engine.tank.fill(new FluidStack(Fluids.LAVA, 8000), IFluidHandler.FluidAction.EXECUTE);
        return h.absolutePos(AIR.above(3));
    }

    /**
     * The test server only builds the three vanilla dimensions, and only the Nether and the End share a build height,
     * so they stand in for "Earth" and "orbit" when moving ships around. Plot slots are shared per dimension, so
     * every test that uses them has its own batch and runs alone.
     */
    private static ServerLevel earth(GameTestHelper h) {
        return h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    }

    private static ServerLevel orbit(GameTestHelper h) {
        return h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
    }

    /** engine (nozzle down) under an iron block under a controller, floating at the given spot of the stand-in Earth. */
    private static ServerSubLevel buildIn(ServerLevel level, BlockPos base, int fuel) {
        level.getChunk(base);
        level.setBlockAndUpdate(base, CreateSatellites.ROCKET_ENGINE.get().defaultBlockState().setValue(RocketEngineBlock.FACING, Direction.DOWN));
        level.setBlockAndUpdate(base.above(), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base.above(2), CreateSatellites.LAUNCH_CONTROLLER.get().defaultBlockState());
        ((RocketEngineBlockEntity) level.getBlockEntity(base)).tank.fill(new FluidStack(Fluids.LAVA, fuel), IFluidHandler.FluidAction.EXECUTE);
        return LaunchControllerBlock.assemble(level, base.above(2), false);
    }

    private static double vy(ServerSubLevel s) {
        return RigidBodyHandle.of(s).getLinearVelocity(new Vector3d()).y;
    }

    @GameTest(template = "test_empty")
    public static void fuelledEngineLiftsTheRocketAndAnEmptyOneFalls(GameTestHelper h) {
        ServerSubLevel lit = LaunchControllerBlock.assemble(h.getLevel(), buildRocket(h, true), true);
        h.assertTrue(lit != null, "launch controller assembles the rocket");
        h.assertTrue(lit.getPlot().getBoundingBox().volume() == 3, "pad stays behind: engine, iron block and controller lift off, got "
                + lit.getPlot().getBoundingBox().volume());
        h.runAfterDelay(12, () -> {
            h.assertTrue(vy(lit) > 5, "fuelled engine pushes the rocket up, vy=" + vy(lit));
            // switching the ignition off stops the burn
            var tank = ((RocketEngineBlockEntity) lit.getLevel().getBlockEntity(findEngine(lit))).tank;
            h.assertTrue(tank.getFluidAmount() < 8000, "the engine has burnt some fuel");
            LaunchControllerBlock.setIgnited(lit, false);
            h.runAfterDelay(3, () -> {
                int left = tank.getFluidAmount();
                h.runAfterDelay(10, () -> h.assertTrue(tank.getFluidAmount() == left, "no fuel is burnt after cutoff"));
            });
            // second rocket without fuel, same recipe
            h.setBlock(AIR.east(6), CreateSatellites.LAUNCH_PAD.get().defaultBlockState());
            h.setBlock(AIR.east(6).above(), CreateSatellites.ROCKET_ENGINE.get().defaultBlockState());
            h.setBlock(AIR.east(6).above(2), CreateSatellites.LAUNCH_CONTROLLER.get().defaultBlockState());
            ServerSubLevel dry = LaunchControllerBlock.assemble(h.getLevel(), h.absolutePos(AIR.east(6).above(2)), true);
            h.assertTrue(dry != null, "dry rocket assembles");
            double y0 = dry.logicalPose().position().y;
            h.runAfterDelay(30, () -> {
                // it sits on its launch pad, which stayed behind as part of the world
                h.assertTrue(dry.logicalPose().position().y < y0 + 0.5 && vy(dry) < 1, "engine without fuel does not push, vy=" + vy(dry));
                h.succeed();
            });
        });
    }

    @GameTest(template = "test_empty")
    public static void orbitHasNoGravity(GameTestHelper h) {
        var access = h.getLevel().getServer().registryAccess();
        h.assertTrue(access.registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).containsKey(Orbit.KEY.location()),
                "orbit dimension type parses");
        // the test server builds its own flat world without the orbit level, so read what Sable would be given
        try (var in = h.getLevel().getServer().getResourceManager()
                .getResourceOrThrow(CreateSatellites.id("dimension_physics/orbit.json")).open()) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in));
            var physics = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
            h.assertTrue(physics.dimension().equals(Orbit.KEY.location()), "physics file is for the orbit dimension");
            h.assertTrue(physics.baseGravity().orElseThrow().lengthSquared() == 0, "orbit has zero gravity");
        } catch (java.io.IOException e) {
            h.fail("orbit physics file missing: " + e);
        }
        h.assertTrue(DimensionPhysicsData.getGravity(h.getLevel()).lengthSquared() > 0, "overworld still has gravity");
        h.succeed();
    }

    @GameTest(template = "test_empty", batch = "dim_move")
    public static void shipKeepsBlocksAndFuelWhenItMovesBetweenDimensions(GameTestHelper h) {
        ServerLevel a = earth(h), b = orbit(h);
        ServerSubLevel ship = buildIn(a, new BlockPos(0, 200, 0), 5000);
        h.assertTrue(ship != null, "ship assembled");
        var aC = SubLevelContainer.getContainer(a);
        var bC = SubLevelContainer.getContainer(b);
        var id = ship.getUniqueId();
        BlockPos engine = findEngine(ship);
        h.assertTrue(engine != null, "engine is inside the new sub-level");
        Vector3d before = ship.logicalPose().position();
        double x = before.x, z = before.z;

        // like any Sable ship, one with no loaded chunks around it is put into storage after a moment
        b.setChunkForced((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, true);
        ServerSubLevel moved = Orbit.transfer(ship, b, 0);
        h.assertTrue(moved != null, "transfer works");
        h.assertTrue(aC.getSubLevel(id) == null, "the old dimension no longer has the ship");
        h.assertTrue(bC.getSubLevel(id) == moved, "the new dimension has the ship");
        h.assertTrue(Math.abs(moved.logicalPose().position().x - x) < 1e-6 && Math.abs(moved.logicalPose().position().z - z) < 1e-6
                && Math.abs(moved.logicalPose().position().y) < 5, "ship arrives at the same x/z and the requested height, at " + moved.logicalPose().position());
        h.assertTrue(b.getBlockState(engine).is(CreateSatellites.ROCKET_ENGINE.get()), "engine block arrived");
        h.assertTrue(b.getBlockState(engine.above(2)).is(CreateSatellites.LAUNCH_CONTROLLER.get()), "controller arrived");
        h.assertTrue(b.getBlockEntity(engine) instanceof RocketEngineBlockEntity e && e.tank.getFluidAmount() == 5000,
                "fuel arrived in the engine tank: " + b.getBlockEntity(engine) + " " + (b.getBlockEntity(engine) instanceof RocketEngineBlockEntity e3 ? e3.tank.getFluid() : ""));

        h.runAfterDelay(10, () -> {
            ServerSubLevel back = Orbit.transfer(moved, a, 100);
            h.assertTrue(back != null, "ship comes back");
            h.assertTrue(a.getBlockEntity(engine) instanceof RocketEngineBlockEntity e2 && e2.tank.getFluidAmount() == 5000, "fuel comes back");
            aC.removeSubLevel(back, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            b.setChunkForced((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, false);
            h.succeed();
        });
        if (true) return;
        h.succeed();
    }

    /** A ship whose plot slot is taken in the target dimension stays where it is, whole. */
    @GameTest(template = "test_empty", batch = "dim_occupied")
    public static void occupiedPlotSlotRefusesTransferWithoutLosingTheShip(GameTestHelper h) {
        ServerLevel a = earth(h), b = orbit(h);
        var bC = SubLevelContainer.getContainer(b);
        ServerSubLevel ship = buildIn(a, new BlockPos(64, 200, 0), 1000);
        // fill the target dimension's plot slots one real ship at a time until the same slot is in use
        List<ServerSubLevel> filler = new java.util.ArrayList<>();
        boolean taken = false;
        for (int i = 0; i < 64 && !taken; i++) {
            ServerSubLevel t = buildIn(b, new BlockPos(64 + 16 * i, 200, 64), 0);
            filler.add(t);
            taken = t.getPlot().plotPos.equals(ship.getPlot().plotPos);
        }
        h.assertTrue(taken, "found the same plot slot in the target dimension");
        ServerSubLevel moved = Orbit.transfer(ship, b, 0);
        filler.forEach(t -> bC.removeSubLevel(t, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED));
        h.assertTrue(moved == null, "transfer refused");
        h.assertTrue(SubLevelContainer.getContainer(a).getSubLevel(ship.getUniqueId()) == ship && !ship.isRemoved(), "ship is still where it was");
        SubLevelContainer.getContainer(a).removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        h.succeed();
    }

    @GameTest(template = "test_empty", batch = "dim_exchange")
    public static void flyingHighEnoughSendsTheShipToOrbitAndBelowItBringsItBack(GameTestHelper h) {
        ServerLevel a = earth(h), b = orbit(h);
        ServerSubLevel ship = buildIn(a, new BlockPos(128, 200, 0), 0);
        var id = ship.getUniqueId();
        var p = ship.logicalPose().position();
        SubLevelContainer.getContainer(a).physicsSystem().getPipeline()
                .teleport(ship, new Vector3d(p.x, Orbit.ASCENT_Y + 20, p.z), ship.logicalPose().orientation());
        ship.logicalPose().position().y = Orbit.ASCENT_Y + 20;
        Orbit.exchange(a, b);
        ServerSubLevel there = (ServerSubLevel) SubLevelContainer.getContainer(b).getSubLevel(id);
        h.assertTrue(there != null, "ship above the sky line is now in orbit; pose=" + ship.logicalPose().position() + " removed=" + ship.isRemoved()
                + " inA=" + (SubLevelContainer.getContainer(a).getSubLevel(id) != null));
        SubLevelContainer.getContainer(b).physicsSystem().getPipeline()
                .teleport(there, new Vector3d(p.x, Orbit.REENTRY_Y - 20, p.z), there.logicalPose().orientation());
        there.logicalPose().position().y = Orbit.REENTRY_Y - 20;
        Orbit.exchange(a, b);
        ServerSubLevel home = (ServerSubLevel) SubLevelContainer.getContainer(a).getSubLevel(id);
        h.assertTrue(home != null, "ship below the bottom of orbit is back on Earth");
        SubLevelContainer.getContainer(a).removeSubLevel(home, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void recipesAndFuelTagsLoad(GameTestHelper h) {
        var rm = h.getLevel().getServer().getRecipeManager();
        for (String n : List.of("launch_pad", "rocket_engine", "launch_controller", "satellite_core", "station_core")) {
            h.assertTrue(rm.byKey(CreateSatellites.id(n)).isPresent(), "recipe " + n + " loaded (all its ingredient ids exist)");
            h.assertTrue(BuiltInRegistries.BLOCK.containsKey(CreateSatellites.id(n)), "block " + n);
        }
        h.assertTrue(Fluids.LAVA.defaultFluidState().is(RocketEngineBlockEntity.FUEL), "lava burns");
        h.assertTrue(!Fluids.WATER.defaultFluidState().is(RocketEngineBlockEntity.FUEL), "water does not");
        h.succeed();
    }

    private static BlockPos findEngine(ServerSubLevel s) {
        var b = s.getPlot().getBoundingBox();
        for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
            if (s.getLevel().getBlockState(p).is(CreateSatellites.ROCKET_ENGINE.get())) return p.immutable();
        return null;
    }
}
