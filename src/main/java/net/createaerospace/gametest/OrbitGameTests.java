package net.createaerospace.gametest;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
import net.createaerospace.orbit.SatellitesPayload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The orbit maths is pure, so these only need a server to run in. */
@GameTestHolder(CreateAerospace.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrbitGameTests {
    private static final UUID OWNER = new UUID(1, 2);

    static Satellite sat(int tier, int incl, double raan, double phase0) {
        return new Satellite(UUID.randomUUID(), "T", OWNER, "o", tier, incl, raan, phase0, 0, Satellite.BEACON);
    }

    private static void near(GameTestHelper h, double a, double b, double eps, String what) {
        h.assertTrue(Math.abs(a - b) <= eps, what + ": " + a + " vs " + b);
    }

    @GameTest(template = "test_empty")
    public static void geostationaryHangsOverOnePoint(GameTestHelper h) {
        Satellite s = sat(3, 0, 1.0, 0.5);
        double[] p0 = OrbitMath.subpoint(s, 0);
        for (long t : new long[] {1, 5000, 12345, 24000 * 7 + 17}) {
            double[] p = OrbitMath.subpoint(s, t);
            near(h, p[0], 0, 1e-9, "stays on the equator");
            near(h, p[1], p0[1], 1e-9, "stays over its longitude at tick " + t);
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void latitudeRepeatsEveryPeriodAndNeverExceedsInclination(GameTestHelper h) {
        for (int tier = 0; tier < 4; tier++) {
            for (int incl = 0; incl < 4; incl++) {
                Satellite s = sat(tier, incl, 0.7, 2.1);
                double max = 0;
                for (int t = 0; t < s.periodTicks(); t += 7) {
                    double lat = OrbitMath.subpoint(s, t)[0];
                    near(h, lat, OrbitMath.subpoint(s, t + s.periodTicks())[0], 1e-9, "latitude repeats after a period");
                    max = Math.max(max, Math.abs(lat));
                }
                double limit = Math.toRadians(OrbitMath.INCLINATION_DEG[incl]);
                h.assertTrue(max <= limit + 1e-9, "latitude stays within the inclination, tier " + tier + " incl " + incl);
                h.assertTrue(max >= limit - 0.05, "latitude reaches the inclination, tier " + tier + " incl " + incl + ": " + max);
            }
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void satelliteIsOverheadAboveItsSubpointAndBelowTheHorizonOnTheOtherSide(GameTestHelper h) {
        Satellite s = sat(0, 2, 0.3, 1.0);
        for (long t : new long[] {0, 100, 777, 5000}) {
            double[] p = OrbitMath.subpoint(s, t);
            near(h, OrbitMath.look(s, t, p[0], p[1]).elevation(), Math.PI / 2, 1e-6, "zenith at the subpoint");
            h.assertTrue(OrbitMath.look(s, t, -p[0], OrbitMath.wrap(p[1] + Math.PI)).elevation() < 0, "below the horizon at the antipode");
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void compassDirectionsMatchTheWorld(GameTestHelper h) {
        Satellite s = sat(1, 2, 0.3, 1.0);
        double[] p = OrbitMath.subpoint(s, 321);
        double d = Math.toRadians(1);
        // observer one degree south of the satellite: it is to the north, which is -z
        var north = OrbitMath.look(s, 321, p[0] - d, p[1]);
        near(h, Math.sin(north.azimuth()), 0, 1e-3, "north has azimuth 0");
        h.assertTrue(Math.cos(north.azimuth()) > 0, "north has azimuth 0, not 180");
        h.assertTrue(OrbitMath.direction(north)[2] < 0, "north is -z");
        // observer one degree west of it: it is to the east, which is +x
        var east = OrbitMath.look(s, 321, p[0], p[1] - d);
        near(h, east.azimuth(), Math.PI / 2, 1e-2, "east has azimuth 90");
        h.assertTrue(OrbitMath.direction(east)[0] > 0, "east is +x");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void worldPositionsMapToLatitudeAndLongitude(GameTestHelper h) {
        near(h, OrbitMath.latOf(-1000), 1, 1e-9, "1000 blocks north is one degree north");
        near(h, OrbitMath.latOf(1e9), -OrbitMath.MAX_LAT_DEG, 1e-9, "latitude is clamped");
        near(h, OrbitMath.lonOf(181000), -179, 1e-9, "longitude wraps");
        int[] b = OrbitMath.groundBlock(new double[] {Math.toRadians(2), Math.toRadians(-3)});
        h.assertTrue(b[0] == -3000 && b[1] == -2000, "ground block of 2N 3W is x -3000, z -2000: " + b[0] + "," + b[1]);
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void changingAltitudeKeepsThePosition(GameTestHelper h) {
        Satellite s = sat(0, 2, 0.3, 1.0);
        for (int tier = 1; tier < 4; tier++) {
            Satellite r = s.retuned(tier, 2, 1500);
            double[] a = OrbitMath.subpoint(s, 1500), b = OrbitMath.subpoint(r, 1500);
            near(h, a[0], b[0], 1e-9, "latitude is continuous across a burn");
            near(h, a[1], b[1], 1e-9, "longitude is continuous across a burn");
            h.assertTrue(r.altKm() == OrbitMath.ALT_KM[tier], "new altitude");
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void satellitesSurviveSavingAndTheNetwork(GameTestHelper h) {
        Satellite s = sat(2, 3, 1.25, -0.5).withMode(Satellite.FLASH);
        h.assertTrue(Satellite.load(s.save()).equals(s), "NBT round trip");
        var access = h.getLevel().registryAccess();
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        Satellite down = s.abandon(Satellite.NO_REENTRY, 0, 0).reenter(500, 1234, -5678);
        h.assertTrue(Satellite.load(down.save()).equals(down), "NBT round trip of an abandoned satellite coming down");
        var alert = new net.createaerospace.orbit.Alert(net.createaerospace.orbit.Alert.LAUNCH_FAILURE, "create_aerospace_engineering:rocket", "o", 2, 10, -20, 99);
        SatellitesPayload.CODEC.encode(buf, new SatellitesPayload(java.util.List.of(s, s.withMode(0), down), java.util.List.of(alert)));
        var decoded = SatellitesPayload.CODEC.decode(buf);
        var back = decoded.satellites();
        h.assertTrue(back.size() == 3 && back.get(0).equals(s) && back.get(1).mode() == 0 && back.get(2).equals(down), "payload round trip");
        h.assertTrue(decoded.alerts().equals(java.util.List.of(alert)), "warnings go over the network");
        Satellite longName = new Satellite(UUID.randomUUID(), "x".repeat(60), OWNER, "y".repeat(60), 0, 0, 0, 0, 0, Satellite.BEACON);
        h.assertTrue(longName.name().length() == Satellite.MAX_NAME, "long names are clamped");
        var buf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        SatellitesPayload.CODEC.encode(buf2, new SatellitesPayload(java.util.List.of(longName), java.util.List.of()));
        h.assertTrue(SatellitesPayload.CODEC.decode(buf2).satellites().get(0).equals(longName), "a long name still goes over the network");
        var data = SatelliteData.get(h.getLevel().getServer());
        data.put(h.getLevel().getServer(), s);
        CompoundTag saved = data.save(new CompoundTag(), access);
        h.assertTrue(SatelliteData.load(saved, access).find(s.id()).equals(s), "saved data round trip");
        data.remove(h.getLevel().getServer(), s.id());
        h.assertTrue(data.find(s.id()) == null, "removed");
        h.succeed();
    }

    /** A launched satellite is geostationary: fixed over the launch longitude. Stepped down to a low tilted orbit it crosses the sky. */
    @GameTest(template = "test_empty")
    public static void aLaunchedSatelliteHangsOverItsLaunchSiteAndCrossesTheSkyWhenLowered(GameTestHelper h) {
        Satellite geo = Satellite.deployed("T", OWNER, "o", 40, 777);
        for (long t : new long[] {777, 5000, 24000 * 3 + 5}) {
            double[] p = OrbitMath.subpoint(geo, t);
            near(h, p[0], 0, 1e-9, "on the equator");
            near(h, Math.toDegrees(p[1]), 40, 1e-6, "over the launch longitude at tick " + t);
            h.assertTrue(OrbitMath.lookFromBlock(geo, t, 40000, 0).elevation() > Math.toRadians(89), "overhead from the launch site");
        }
        h.assertTrue(OrbitMath.lookFromBlock(geo, 0, 40000 + 150000, 0).elevation() < 0, "below the horizon on the far side of the world");
        Satellite s = geo.retuned(0, 2, 0);
        int passes = 0, longest = 0, run = 0;
        for (int t = 0; t <= 2 * 24000; t += 4) {
            boolean up = OrbitMath.lookFromBlock(s, t, 40000, 0).elevation() > Math.toRadians(5);
            if (up) run += 4;
            else {
                if (run > 0) passes++;
                longest = Math.max(longest, run);
                run = 0;
            }
        }
        CreateAerospace.LOGGER.info("Low-orbit passes in two days: {}, longest {} ticks", passes, longest);
        h.assertTrue(passes >= 2, "a lowered satellite passes over at least twice in two days, got " + passes);
        h.assertTrue(longest >= 60, "a pass lasts at least three seconds, got " + longest);
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void onlyTheOwnerOrAnOperatorMayControlASatellite(GameTestHelper h) {
        Satellite s = sat(0, 0, 0, 0);
        h.assertTrue(s.controlledBy(OWNER, false), "owner");
        h.assertTrue(!s.controlledBy(UUID.randomUUID(), false), "stranger");
        h.assertTrue(s.controlledBy(UUID.randomUUID(), true), "operator");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void everyBlockOfTheModCanBeMinedWithAPickaxe(GameTestHelper h) {
        for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(CreateAerospace.MOD_ID)) continue;
            h.assertTrue(b.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), b + " is mineable with a pickaxe");
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void lavaIsRocketFuelAndWaterIsNot(GameTestHelper h) {
        h.assertTrue(net.minecraft.world.level.material.Fluids.LAVA.is(CreateAerospace.ROCKET_FUEL), "lava burns");
        h.assertTrue(!net.minecraft.world.level.material.Fluids.WATER.is(CreateAerospace.ROCKET_FUEL), "water does not");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void theControlComputerAppHasEveryConsoleButtonExceptTheMap(GameTestHelper h) {
        var ids = net.createaerospace.orbit.SatelliteControl.appButtons();
        h.assertTrue(ids.size() <= 12 && ids.stream().distinct().count() == ids.size(), "ids fit the app API and are unique");
        for (var b : net.createaerospace.block.ConsoleButton.values()) {
            if (b == net.createaerospace.block.ConsoleButton.MAP) continue;
            for (boolean up : new boolean[] {false, true}) {
                String id = b.dial() ? b.label() + (up ? "_up" : "_down") : b.label();
                h.assertTrue(ids.contains(id), "app button " + id);
                var p = net.createaerospace.orbit.SatelliteControl.parse(id);
                h.assertTrue(p != null && p.button() == b && (!b.dial() || p.up() == up), "id " + id + " maps back");
            }
        }
        h.assertTrue(net.createaerospace.orbit.SatelliteControl.parse("map") == null, "the map is not an app button");
        net.createaerospace.compat.cfs.CfsCompat.register(); // without Create: Frontier Science it must do nothing
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void theControlComputerLayoutHasEveryButtonOnceAndInsideTheCanvas(GameTestHelper h) {
        for (var v : net.createaerospace.compat.cfs.CfsLayout.View.values()) {
            var rects = net.createaerospace.compat.cfs.CfsLayout.all(v);
            for (int i = 0; i < rects.size(); i++) {
                var r = rects.get(i);
                h.assertTrue(r.l() >= 0 && r.t() >= 0 && r.r() <= 1 && r.b() <= 1, v + " rectangle " + i + " is inside the canvas");
                for (int j = i + 1; j < rects.size(); j++) h.assertTrue(!r.overlaps(rects.get(j)), v + " rectangles " + i + " and " + j + " do not overlap");
            }
            for (String id : net.createaerospace.compat.cfs.CfsLayout.buttons(v)) {
                var r = net.createaerospace.compat.cfs.CfsLayout.button(v, id);
                h.assertTrue(id.equals(net.createaerospace.compat.cfs.CfsLayout.hit(v, (r.l() + r.r()) / 2, (r.t() + r.b()) / 2)), v + " finds " + id);
            }
        }
        h.assertTrue(net.createaerospace.compat.cfs.CfsLayout.buttons(net.createaerospace.compat.cfs.CfsLayout.View.CONTROL)
                .equals(net.createaerospace.orbit.SatelliteControl.appButtons()), "the control view has every console button");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void theVoxelEarthShowsOnlyTheSideTowardsTheViewer(GameTestHelper h) {
        var sea = new net.createaerospace.client.globe.VoxelGlobe((lat, lon) -> false);
        var land = new net.createaerospace.client.globe.VoxelGlobe((lat, lon) -> true);
        int[] seaQuads = {0}, landQuads = {0};
        var view = new net.createaerospace.client.globe.VoxelGlobe.View(10, 20, 0, 0, 1);
        sea.drawEarth((a, b, c, d, e, f, g, i, j, k, l, m, argb) -> seaQuads[0]++, view);
        land.drawEarth((a, b, c, d, e, f, g, i, j, k, l, m, argb) -> landQuads[0]++, view);
        int n = net.createaerospace.client.globe.VoxelGlobe.N;
        h.assertTrue(seaQuads[0] > n * n && seaQuads[0] < 12 * n * n, "a sphere of cubes shows a few thousand faces, got " + seaQuads[0]);
        h.assertTrue(landQuads[0] > seaQuads[0] / 2, "land is a sphere of cubes too, got " + landQuads[0]);
        // the point in the middle of the view is on the near side; its opposite is behind the Earth
        var near = net.createaerospace.client.globe.VoxelGlobe.position(Math.toRadians(10), Math.toRadians(20), n * 1.5);
        var far = net.createaerospace.client.globe.VoxelGlobe.position(Math.toRadians(-10), Math.toRadians(200), n * 1.5);
        h.assertTrue(!view.hidden(near) && view.hidden(far), "a satellite behind the Earth is hidden");
        h.succeed();
    }
}
