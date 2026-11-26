package net.createaerospace.gametest;

import net.createaerospace.CreateAerospace;
import net.createaerospace.block.ConsoleButton;
import net.createaerospace.block.GroundConsoleBlock;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.block.LaunchPadBlock;
import net.createaerospace.block.LaunchPadBlockEntity;
import net.createaerospace.entity.RocketEntity;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
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

@GameTestHolder(CreateAerospace.MOD_ID)
@PrefixGameTestTemplate(false)
public class RocketGameTests {
    private static final BlockPos PAD = new BlockPos(1, 12, 1);
    private static final BlockPos CONSOLE = new BlockPos(1, 2, 1);

    /** Use {@code stack} on the pad as {@code player}. */
    private static void use(GameTestHelper h, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(PAD);
        h.getLevel().getBlockState(abs).useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    /** Launches in tests succeed unless a test makes one fail ({@code RocketEntity.setFailAt}). */
    private static void noRandomFailures(GameTestHelper h) {
        h.getLevel().getGameRules().getRule(CreateAerospace.LAUNCH_FAILURES).set(false, h.getLevel().getServer());
    }

    private static void loadRocket(GameTestHelper h, Player player) {
        noRandomFailures(h);
        h.setBlock(PAD, CreateAerospace.LAUNCH_PAD.get().defaultBlockState());
        ItemStack rocket = new ItemStack(CreateAerospace.ROCKET_ITEM.get());
        use(h, player, rocket);
        h.assertTrue(rocket.isEmpty() && rockets(h).size() == 1, "the rocket item stands a rocket up");
        ItemStack stack = new ItemStack(CreateAerospace.SATELLITE.get());
        use(h, player, stack);
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
        ItemStack second = new ItemStack(CreateAerospace.ROCKET_ITEM.get());
        use(h, p, second);
        h.assertTrue(rockets(h).size() == 1 && !second.isEmpty(), "a second rocket is not stood on an occupied pad");
        var tank = ((LaunchPadBlockEntity) h.getBlockEntity(PAD)).tank;
        h.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "water is refused");
        h.succeed();
    }

    @GameTest(template = "test_empty", batch = "rocket_clearance")
    public static void theStandardRocketNeedsSixtyBlocksOfClearanceAndBelongsToOnePad(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(PAD, CreateAerospace.LAUNCH_PAD.get());
        BlockPos highObstacle = PAD.above(59).east(4);
        h.setBlock(highObstacle, Blocks.STONE);
        ItemStack stack = new ItemStack(CreateAerospace.ROCKET_ITEM.get());
        use(h, p, stack);
        h.assertTrue(!stack.isEmpty() && rockets(h).isEmpty(), "the high obstacle within the width and height prevents placement");
        h.setBlock(highObstacle, Blocks.AIR);
        h.setBlock(PAD.above(61), Blocks.STONE);
        use(h, p, stack);
        h.assertTrue(stack.isEmpty() && rockets(h).size() == 1, "an obstacle above the nose leaves room to place the rocket");
        var rocket = rockets(h).get(0);
        h.assertTrue(rocket.getBbHeight() == 60f && rocket.getBbWidth() == 10f, "the standard rocket is 60 blocks tall and 10 blocks wide");
        BlockPos pad = h.absolutePos(PAD);
        h.assertTrue(LaunchPadBlock.standing(h.getLevel(), pad) == rocket
                && LaunchPadBlock.standing(h.getLevel(), pad.east()) == null, "only the pad at the rocket's base can control it");
        rocket.discard();
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
                    i -> i.getItem().is(CreateAerospace.SATELLITE.get())).isEmpty(), "and the satellite drops");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(PAD)).inflate(4, 4, 4),
                    i -> i.getItem().is(CreateAerospace.ROCKET_ITEM.get())).isEmpty(), "and the rocket item too");
        });
    }

    @GameTest(template = "test_empty", batch = "rocket_h3", timeoutTicks = 100)
    public static void anH3_24WStandsAtFullSizeAndLiftsThirteenSatellites(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(PAD, CreateAerospace.LAUNCH_PAD.get().defaultBlockState());
        use(h, p, new ItemStack(CreateAerospace.H3_ITEMS.get(2).get()));
        h.assertTrue(rockets(h).size() == 1, "the H3-24W stands on the pad");
        RocketEntity r = rockets(h).get(0);
        h.assertTrue(r.typeId().equals(net.createaerospace.rocket.H3.Variant.H3_24W.id()), "as an H3-24W");
        h.assertTrue(Math.abs(r.getBbHeight() - 63f) < 1e-3 && Math.abs(r.getBbWidth() - 10.4f) < 1e-3, "63 m by 10.4 m at full size, got " + r.getBbWidth() + " x " + r.getBbHeight());
        ItemStack sats = new ItemStack(CreateAerospace.SATELLITE.get(), 16);
        for (int i = 0; i < 16; i++) use(h, p, sats);
        h.assertTrue(r.payloadCount() == 13 && r.loadedKg() == 6500, "6.5 t to GTO is 13 satellites, got " + r.payloadCount());
        h.assertTrue(net.createaerospace.rocket.H3.climbed(0.034, net.createaerospace.rocket.H3.tickAt(0.034, 1)) >= net.createaerospace.rocket.H3.CLIMB,
                "the separation timing reaches the top of the climb");
        h.succeed();
    }

    @GameTest(template = "test_empty", batch = "rocket_mass", timeoutTicks = 100)
    public static void aRocketTakesPayloadsUpToItsMassLimitAndCanBeTakenDown(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(PAD, CreateAerospace.LAUNCH_PAD.get().defaultBlockState());
        ItemStack sats = new ItemStack(CreateAerospace.SATELLITE.get(), 3);
        use(h, p, sats);
        h.assertTrue(sats.getCount() == 3 && rockets(h).isEmpty(), "a payload needs a rocket on the pad");
        use(h, p, new ItemStack(CreateAerospace.ROCKET_ITEM.get()));
        h.assertTrue(net.createaerospace.api.AerospaceApi.massKg(sats) == 500, "CAE's satellite weighs 500 kg");
        use(h, p, sats);
        use(h, p, sats);
        use(h, p, sats);
        RocketEntity r = rockets(h).get(0);
        h.assertTrue(sats.getCount() == 1 && r.payloadCount() == 2 && r.loadedKg() == 1000, "two fit under 1000 kg, the third does not");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(true);
        BlockPos abs = h.absolutePos(PAD);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertTrue(rockets(h).isEmpty(), "sneaking with an empty hand takes the rocket down");
        h.assertTrue(p.getInventory().countItem(CreateAerospace.SATELLITE.get()) == 2 && p.getInventory().countItem(CreateAerospace.ROCKET_ITEM.get()) == 1,
                "and gives back the rocket and its payloads");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void buttonsAreFoundInTheOperatorsFrameWhicheverWayTheConsoleFaces(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            h.setBlock(CONSOLE, CreateAerospace.GROUND_CONSOLE.get().defaultBlockState().setValue(GroundConsoleBlock.FACING, facing));
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
        h.setBlock(CONSOLE, CreateAerospace.GROUND_CONSOLE.get().defaultBlockState());
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
        h.assertTrue(!data.find(a.id()).reentryPlanned(), "a stranger cannot send it down");
        c.press(owner, ConsoleButton.DEORBIT, true);
        h.assertTrue(!data.find(a.id()).reentryPlanned() && c.guardOpen(), "the first press only lifts the cover");
        c.press(owner, ConsoleButton.DEORBIT, true);
        long now = h.getLevel().getGameTime();
        Satellite down = data.find(a.id());
        h.assertTrue(down.reentering(now) && !c.guardOpen(), "the second press starts the reentry and closes the cover");
        BlockPos at = h.absolutePos(CONSOLE);
        h.assertTrue(down.targetX() == at.getX() && down.targetZ() == at.getZ(), "it comes down over the console");
        c.press(owner, ConsoleButton.MODE, true);
        h.assertTrue(data.find(a.id()).mode() == down.mode(), "nothing else works on a satellite coming down");

        c.press(owner, ConsoleButton.NEXT, true);
        h.assertTrue(c.selected().id().equals(b.id()), "select B");
        c.press(owner, ConsoleButton.ABANDON, true);
        h.assertTrue(!data.find(b.id()).abandoned(), "abandoning needs the cover open");
        c.press(owner, ConsoleButton.DEORBIT, true);
        c.press(owner, ConsoleButton.ABANDON, true);
        Satellite junk = data.find(b.id());
        h.assertTrue(junk.abandoned() && !junk.reentryPlanned() && junk.mode() == Satellite.STANDBY, "abandoned: debris, lamp off, a geostationary one stays up");
        c.press(owner, ConsoleButton.MODE, true);
        h.assertTrue(data.find(b.id()).mode() == Satellite.STANDBY, "nobody but an operator controls debris");
        h.assertTrue(c.summary().getString().length() > 0, "the display link has something to show");
        data.remove(server, a.id());
        data.remove(server, b.id());
        h.succeed();
    }

    @GameTest(template = "test_empty", batch = "rocket_failure", timeoutTicks = 300)
    public static void aFailedLaunchExplodesLosesItsPayloadsAndWarnsEveryConsole(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        loadRocket(h, p);
        var tank = ((LaunchPadBlockEntity) h.getBlockEntity(PAD)).tank;
        tank.fill(new FluidStack(Fluids.LAVA, 4000), IFluidHandler.FluidAction.EXECUTE);
        rockets(h).get(0).setFailAt(30);
        var data = SatelliteData.get(h.getLevel().getServer());
        int alertsBefore = data.alerts().size();
        long launchedAt = h.getLevel().getGameTime();
        h.setBlock(PAD.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.succeedWhen(() -> {
            h.assertTrue(rockets(h).isEmpty(), "the rocket is gone");
            var last = data.alerts().get(data.alerts().size() - 1);
            h.assertTrue(data.alerts().size() > alertsBefore || last.time() >= launchedAt, "a warning was raised");
            h.assertTrue(last.kind() == net.createaerospace.orbit.Alert.LAUNCH_FAILURE && last.count() == 1
                    && last.subject().equals(net.createaerospace.api.RocketApi.STANDARD.toString()), "a launch failure of the standard rocket with one payload");
            h.assertTrue(last.shown(h.getLevel().getGameTime()), "and it is shown now");
            h.assertTrue(mine(h, p) == 0, "nothing reached orbit");
            h.assertTrue(h.getBlockState(PAD).is(CreateAerospace.LAUNCH_PAD.get()), "the explosion breaks no blocks");
        });
    }

    @GameTest(template = "test_empty", batch = "reentry", timeoutTicks = 200)
    public static void aReentryComesDownOverItsTargetAsAFireballAndIsRemoved(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var data = SatelliteData.get(server);
        long now = h.getLevel().getGameTime();
        int tx = 5000, tz = -3000;
        Satellite geo = Satellite.deployed("Fall", java.util.UUID.randomUUID(), "o", 60, now);
        Satellite s = geo.reenter(now, tx, tz);
        int D = net.createaerospace.orbit.OrbitMath.REENTRY_DESCENT_TICKS, F = net.createaerospace.orbit.OrbitMath.REENTRY_FIRE_TICKS;
        h.assertTrue(net.createaerospace.orbit.OrbitMath.altKm(s, now + D / 2.0) < geo.altKm() && net.createaerospace.orbit.OrbitMath.altKm(s, now + D) <= 120.01,
                "it sinks to the top of the fireball during the descent");
        h.assertTrue(!s.burning(now + D - 1) && s.burning(now + D + 1) && s.burning(now + D + F - 1) && !s.burning(now + D + F), "it burns only during the fireball");
        java.util.function.DoubleUnaryOperator fromTarget = t -> {
            int[] b = net.createaerospace.orbit.OrbitMath.groundBlock(net.createaerospace.orbit.OrbitMath.subpoint(s, t));
            return Math.hypot(b[0] - tx, b[1] - tz);
        };
        // 4 degrees before and half a degree past the target, 0.7 degrees to its side
        h.assertTrue(Math.abs(fromTarget.applyAsDouble(now + D) - Math.hypot(4000, 700)) < 150, "the fireball starts 4 degrees before the target, got " + fromTarget.applyAsDouble(now + D));
        h.assertTrue(Math.abs(fromTarget.applyAsDouble(now + D + F) - Math.hypot(500, 700)) < 80, "and burns out half a degree past it, got " + fromTarget.applyAsDouble(now + D + F));
        double closest = Double.MAX_VALUE;
        for (int i = 0; i <= 20; i++) closest = Math.min(closest, fromTarget.applyAsDouble(now + D + F * i / 20.0));
        h.assertTrue(Math.abs(closest - 700) < 80, "passing beside the target, not over it, got " + closest);
        var high = net.createaerospace.orbit.OrbitMath.lookFromBlock(s, now + D + F * 0.85, tx, tz);
        var low = net.createaerospace.orbit.OrbitMath.lookFromBlock(s, now + D + F * 0.15, tx, tz);
        h.assertTrue(Math.toDegrees(high.elevation()) > 20 && Math.toDegrees(low.elevation()) > 5, "seen up in the sky from the target, got "
                + Math.toDegrees(low.elevation()) + " then " + Math.toDegrees(high.elevation()));
        h.assertTrue(Math.abs(Math.toDegrees(high.azimuth() - low.azimuth())) > 20, "and moving across it, not straight at the watcher");
        h.assertTrue(net.createaerospace.orbit.OrbitMath.subpoint(s, now - 10)[1] == net.createaerospace.orbit.OrbitMath.subpoint(geo, now - 10)[1],
                "before the reentry it is where its orbit says");

        // one that has already burnt up, and one about to start: the server tick removes the first and warns about the second
        Satellite gone = Satellite.deployed("Gone", java.util.UUID.randomUUID(), "o", 61, now).reenter(now - D - F - 5, tx, tz);
        Satellite soon = Satellite.deployed("Soon", java.util.UUID.randomUUID(), "o", 62, now).reenter(now + 5, tx, tz);
        data.put(server, gone);
        data.put(server, soon);
        h.succeedWhen(() -> {
            h.assertTrue(data.find(gone.id()) == null, "a burnt-up satellite leaves the list");
            h.assertTrue(data.alerts().stream().anyMatch(a -> a.kind() == net.createaerospace.orbit.Alert.REENTRY && a.subject().equals("Soon")),
                    "a reentry that begins raises a warning");
            data.remove(server, soon.id());
        });
    }

    @GameTest(template = "test_empty", batch = "abandon")
    public static void anAbandonedLowSatelliteDecaysAndAHighOneStaysUp(GameTestHelper h) {
        var server = h.getLevel().getServer();
        long now = h.getLevel().getGameTime();
        java.util.UUID owner = java.util.UUID.randomUUID();
        Satellite low = Satellite.deployed("Low", owner, "o", 0, now).retuned(0, 2, now);
        Satellite a = net.createaerospace.orbit.SatelliteControl.abandoned(server, low, now);
        h.assertTrue(a.abandoned() && a.reentryStart() >= now + 24000 && a.reentryStart() <= now + 72000, "the lowest orbit comes down in 1 to 3 days");
        h.assertTrue(!a.reentering(now) && !a.controlledBy(owner, false) && a.controlledBy(owner, true), "debris: only an operator controls it");
        Satellite high = net.createaerospace.orbit.SatelliteControl.abandoned(server, Satellite.deployed("High", owner, "o", 0, now), now);
        h.assertTrue(high.abandoned() && !high.reentryPlanned(), "a geostationary one stays up");
        var sp = server.overworld().getSharedSpawnPos();
        h.assertTrue(Math.abs(a.targetX() - sp.getX()) <= 1500 && Math.abs(a.targetZ() - sp.getZ()) <= 1500, "it falls near the spawn");
        var saved = Satellite.load(a.save());
        h.assertTrue(saved.equals(a), "abandoning and the planned reentry are saved");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void aPadKeepsItsFuelWhenSaved(GameTestHelper h) {
        h.setBlock(PAD, CreateAerospace.LAUNCH_PAD.get().defaultBlockState());
        var pad = (LaunchPadBlockEntity) h.getBlockEntity(PAD);
        pad.tank.fill(new FluidStack(Fluids.LAVA, 3500), IFluidHandler.FluidAction.EXECUTE);
        var access = h.getLevel().registryAccess();
        var saved = pad.saveWithoutMetadata(access);
        pad.tank.setFluid(FluidStack.EMPTY);
        pad.loadWithComponents(saved, access);
        h.assertTrue(pad.tank.getFluidAmount() == 3500 && pad.tank.getFluid().getFluid() == Fluids.LAVA, "fuel survives saving");
        h.succeed();
    }

    private static final net.minecraft.resources.ResourceLocation TEST_PAYLOAD = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cae_test", "beacon");
    private static final java.util.List<java.util.UUID> REMOVED = new java.util.ArrayList<>();

    /** A compass stands in for the satellite item of another mod. Registered once per test server. */
    private static synchronized void registerTestPayload() {
        if (net.createaerospace.api.AerospaceApi.payload(TEST_PAYLOAD) != null) return;
        net.createaerospace.api.AerospaceApi.registerPayload(TEST_PAYLOAD, net.minecraft.world.item.Items.COMPASS, new net.createaerospace.api.AerospaceApi.Payload() {
            @Override
            public net.minecraft.network.chat.Component name() {
                return net.minecraft.network.chat.Component.literal("TEST");
            }

            @Override
            public int massKg(ItemStack stack) {
                return 300;
            }

            @Override
            public net.minecraft.nbt.CompoundTag deploy(net.minecraft.server.level.ServerLevel level, ItemStack stack,
                                                       net.createaerospace.api.AerospaceApi.SatelliteInfo satellite) {
                var t = new net.minecraft.nbt.CompoundTag();
                t.putString("from", stack.getHoverName().getString());
                return t;
            }

            @Override
            public void removed(net.minecraft.server.MinecraftServer server, net.createaerospace.api.AerospaceApi.SatelliteInfo satellite) {
                REMOVED.add(satellite.id());
            }
        });
    }

    private static final net.minecraft.resources.ResourceLocation TEST_ROCKET = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cae_test", "sounding_rocket");
    private static final java.util.List<String> HOOKS = new java.util.ArrayList<>();

    /** A spyglass stands in for the rocket item of another mod: small, wide, light and cheap. */
    private static synchronized void registerTestRocket() {
        if (net.createaerospace.api.RocketApi.rocket(TEST_ROCKET) != null) return;
        net.createaerospace.api.RocketApi.registerRocket(TEST_ROCKET, net.minecraft.world.item.Items.SPYGLASS,
                new net.createaerospace.api.RocketApi.RocketType(net.minecraft.network.chat.Component.literal("Sounder"), 300, 1000, 32, 0.1, 1.5, 2f, 3f),
                new net.createaerospace.api.RocketApi.Hooks() {
                    @Override
                    public void launched(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity rocket, java.util.List<ItemStack> payloads) {
                        HOOKS.add("launched:" + payloads.size());
                    }

                    @Override
                    public void deployed(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity rocket,
                                         java.util.List<net.createaerospace.api.AerospaceApi.SatelliteInfo> satellites) {
                        HOOKS.add("deployed:" + satellites.get(0).name());
                    }
                });
    }

    @GameTest(template = "test_empty", batch = "rocket_payload", timeoutTicks = 400)
    public static void aRocketCarriesThePayloadOfAnotherModToOrbit(GameTestHelper h) {
        registerTestPayload();
        registerTestRocket();
        noRandomFailures(h);
        HOOKS.clear();
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(PAD, CreateAerospace.LAUNCH_PAD.get().defaultBlockState());
        use(h, p, new ItemStack(net.minecraft.world.item.Items.SPYGLASS));
        h.assertTrue(rockets(h).size() == 1 && rockets(h).get(0).typeId().equals(TEST_ROCKET), "the item of another mod stands its own rocket up");
        h.assertTrue(rockets(h).get(0).getBbWidth() == 2f && rockets(h).get(0).getBbHeight() == 3f, "with its own size");
        use(h, p, new ItemStack(CreateAerospace.SATELLITE.get()));
        h.assertTrue(rockets(h).get(0).payloadCount() == 0, "a 500 kg satellite is too heavy for a 300 kg rocket");
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.COMPASS, 2);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Nav-1"));
        use(h, p, stack);
        use(h, p, stack);
        h.assertTrue(stack.getCount() == 1 && rockets(h).get(0).loadedKg() == 300, "one 300 kg payload fills it");
        h.assertTrue(rockets(h).get(0).payloads().get(0).is(net.minecraft.world.item.Items.COMPASS), "the rocket keeps the item itself");
        BlockPos abs = h.absolutePos(PAD);
        var tank = ((LaunchPadBlockEntity) h.getBlockEntity(PAD)).tank;
        tank.fill(new FluidStack(Fluids.LAVA, 4000), IFluidHandler.FluidAction.EXECUTE);
        h.setBlock(PAD.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.runAfterDelay(2, () -> h.assertTrue(tank.getFluidAmount() == 3000, "the launch burnt the rocket's own 1000 mB"));
        h.succeedWhen(() -> {
            h.assertTrue(HOOKS.equals(java.util.List.of("launched:1", "deployed:Nav-1")), "the rocket's hooks were called: " + HOOKS);
            var server = h.getLevel().getServer();
            var mine = net.createaerospace.api.AerospaceApi.satellites(server, TEST_PAYLOAD).stream().filter(x -> x.owner().equals(p.getUUID())).toList();
            h.assertTrue(rockets(h).isEmpty() && mine.size() == 1, "one satellite of the test payload in orbit");
            var s = mine.get(0);
            h.assertTrue(s.name().equals("Nav-1") && s.data().getString("from").equals("Nav-1"), "deploy() saw the stack and its data was kept");
            h.assertTrue(net.createaerospace.api.AerospaceApi.elevation(server, s.id(), abs).isPresent(), "elevation is known");
            var more = s.data();
            more.putInt("channel", 7);
            h.assertTrue(net.createaerospace.api.AerospaceApi.setData(server, s.id(), more), "data can be replaced");
            h.assertTrue(net.createaerospace.api.AerospaceApi.satellite(server, s.id()).get().data().getInt("channel") == 7, "and read back");
            SatelliteData.get(server).remove(server, s.id());
            h.assertTrue(REMOVED.contains(s.id()), "the payload heard that its satellite left orbit");
            h.assertTrue(SatelliteData.get(server).data(s.id()).isEmpty(), "its data went with it");
        });
    }
}
