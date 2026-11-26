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

    /** README: a new inclination keeps the satellite over the same spot whenever the new orbit passes over it, and its heading. */
    @GameTest(template = "test_empty")
    public static void changingInclinationKeepsThePointUnderTheSatellite(GameTestHelper h) {
        for (int from = 0; from < 4; from++) {
            for (int to = 0; to < 4; to++) {
                for (double phase0 = 0.1; phase0 < 6.3; phase0 += 0.37) {
                    Satellite s = sat(1, from, 0.9, phase0);
                    Satellite r = s.retuned(1, to, 1500);
                    double[] a = OrbitMath.subpoint(s, 1500), b = OrbitMath.subpoint(r, 1500);
                    double limit = Math.toRadians(OrbitMath.INCLINATION_DEG[to]);
                    String what = "incl " + from + " -> " + to + ", phase " + phase0;
                    near(h, OrbitMath.wrap(a[1] - b[1]), 0, 1e-9, "longitude kept, " + what);
                    near(h, b[0], Math.max(-limit, Math.min(limit, a[0])), 1e-9, "latitude kept or brought to the new inclination, " + what);
                    // away from where either orbit turns back towards the equator
                    if (from > 0 && to > 0 && Math.abs(a[0]) < Math.min(limit, Math.toRadians(OrbitMath.INCLINATION_DEG[from])) - 0.05) {
                        double da = OrbitMath.subpoint(s, 1510)[0] - a[0], db = OrbitMath.subpoint(r, 1510)[0] - b[0];
                        h.assertTrue(Math.signum(da) == Math.signum(db), "still heading the same way, " + what);
                    }
                }
            }
        }
        Satellite geo = Satellite.deployed("T", OWNER, "o", -70, 3333);
        double[] p = OrbitMath.subpoint(geo.retuned(3, 3, 9000), 9000);
        near(h, p[0], 0, 1e-9, "a tilted geostationary satellite starts from the equator");
        near(h, Math.toDegrees(p[1]), -70, 1e-6, "over its launch longitude");
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
    public static void aClickOnTheAppMapFindsTheDotTheRendererDrew(GameTestHelper h) {
        // a canvas of 3 by 1.68 blocks (an LCD of 3 by 2 in a window), the board at 35N 139E, turned one step
        float w = 3, hgt = 1.68f;
        var map = net.createaerospace.compat.cfs.CfsLayout.mapView(w, hgt, 35, 139, 1);
        java.util.List<Satellite> all = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) all.add(new Satellite(UUID.randomUUID(), "S" + i, UUID.randomUUID(), "", i % 4, i % 3, i * .5, i * .9, 0, Satellite.BEACON));
        long tick = 500;
        int found = 0;
        for (var spot : net.createaerospace.client.globe.Spots.of(map.view(), all, tick)) {
            if (spot.hidden() || !map.contains(spot.x(), spot.y())) continue;
            // the server gets the click as fractions of the canvas
            double x = spot.x() / w, y = spot.y() / hgt;
            var again = net.createaerospace.compat.cfs.CfsLayout.mapView(w, hgt, 35, 139, 1);
            var picked = net.createaerospace.client.globe.Spots.pick(again.view(), all, tick, x * w, y * hgt, again.reach());
            h.assertTrue(picked != null, "a click on a dot finds a satellite");
            float[] at = again.view().at(net.createaerospace.client.globe.Spots.position(picked, tick));
            h.assertTrue(Math.hypot(at[0] - spot.x(), at[1] - spot.y()) <= again.reach(), "the one under the click");
            found++;
        }
        h.assertTrue(found > 0, "some dots are in front");
        h.assertTrue(!map.contains(map.x0() - .01, map.y0()), "outside the globe square is not the map");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void aHundredSatellitesShareTheirOrbitsAndNamesDoNotOverlap(GameTestHelper h) {
        java.util.List<Satellite> all = new java.util.ArrayList<>();
        // 60 geostationary ones over the equator, and a constellation of 6 inclined planes of 10 low satellites each
        for (int i = 0; i < 60; i++) all.add(Satellite.deployed("G" + i, UUID.randomUUID(), "", i * 6 - 180, 0));
        for (int plane = 0; plane < 6; plane++) for (int i = 0; i < 10; i++) {
            all.add(new Satellite(UUID.randomUUID(), "L" + plane + "-" + i, UUID.randomUUID(), "", 0, 2, plane * Math.PI / 3, i * Math.PI / 5, 0, Satellite.BEACON));
        }
        var circles = net.createaerospace.client.globe.Crowd.circles(all, null, 0);
        h.assertTrue(circles.size() == 7, "120 satellites in 7 orbits draw 7 circles, got " + circles.size());
        h.assertTrue(circles.size() <= net.createaerospace.client.globe.Crowd.CIRCLES, "so every orbit is drawn whole");
        // a satellite whose node is a different degree has its own circle; debris has its own grey one
        all.add(new Satellite(UUID.randomUUID(), "odd", UUID.randomUUID(), "", 0, 2, Math.toRadians(17), 0, 0, Satellite.BEACON));
        all.add(all.get(0).abandon(Satellite.NO_REENTRY, 0, 0));
        h.assertTrue(net.createaerospace.client.globe.Crowd.circles(all, null, 0).size() == 9, "a new plane and debris add circles");
        h.assertTrue(net.createaerospace.client.globe.Crowd.circles(all, all.get(0), 0).size() == 9, "the selected one is left out only once");
        // names: boxes 10 wide in a row 4 apart keep every third, the first wanted first
        java.util.List<Integer> at = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) at.add(i);
        var kept = net.createaerospace.client.globe.Crowd.declutter(at, i -> new float[] {i * 4, 0, i * 4 + 10, 8}, 100);
        h.assertTrue(kept.get(0) == 0 && kept.get(1) == 3 && kept.size() == 10, "only names that do not cover another, got " + kept);
        h.assertTrue(net.createaerospace.client.globe.Crowd.declutter(at, i -> new float[] {i * 20, 0, i * 20 + 10, 8}, 5).size() == 5, "at most max");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void theCubeEarthShowsOnlyTheSideTowardsTheViewer(GameTestHelper h) {
        var sea = new net.createaerospace.client.globe.CubeEarth((lat, lon) -> false);
        var land = new net.createaerospace.client.globe.CubeEarth((lat, lon) -> true);
        int[] seaQuads = {0}, landQuads = {0};
        var view = new net.createaerospace.client.globe.CubeEarth.View(10, 20, 0, 0, 1);
        sea.drawEarth((a, b, c, d, e, f, g, i, j, k, l, m, argb) -> seaQuads[0]++, view);
        land.drawEarth((a, b, c, d, e, f, g, i, j, k, l, m, argb) -> landQuads[0]++, view);
        int n = net.createaerospace.client.globe.CubeEarth.N;
        float half = net.createaerospace.client.globe.CubeEarth.HALF;
        // seen from 10N 20E, the faces towards 0E, 90E and the north pole show: three faces of 8 by 8 tiles, land or sea alike
        h.assertTrue(seaQuads[0] == 3 * n * n, "three faces of tiles show, got " + seaQuads[0]);
        h.assertTrue(landQuads[0] == seaQuads[0], "land is as flat as the sea, got " + landQuads[0]);
        // the point in the middle of the view is on the near side; its opposite is behind the Earth
        var near = net.createaerospace.client.globe.CubeEarth.position(Math.toRadians(10), Math.toRadians(20), half * 1.5);
        var far = net.createaerospace.client.globe.CubeEarth.position(Math.toRadians(-10), Math.toRadians(200), half * 1.5);
        h.assertTrue(!view.hidden(near) && view.hidden(far), "a satellite behind the Earth is hidden");
        // a point far to the side of the cube is not behind it, even on the far half
        var beside = net.createaerospace.client.globe.CubeEarth.position(0, Math.toRadians(110), net.createaerospace.client.globe.CubeEarth.orbitRadius(3));
        h.assertTrue(!view.hidden(beside), "a geostationary satellite beside the Earth shows");
        // every orbit is a circle wider than the corners of the cube and its clouds, larger for each tier, and inside the view
        double last = net.createaerospace.client.globe.CubeEarth.CORNER * (half + net.createaerospace.client.globe.CubeEarth.CLOUD_LIFT) / half;
        for (int tier = 0; tier < OrbitMath.ALT_KM.length; tier++) {
            double r = net.createaerospace.client.globe.CubeEarth.orbitRadius(tier);
            h.assertTrue(r > last, "tier " + tier + " is outside the one under it");
            last = r;
        }
        h.assertTrue(last < net.createaerospace.client.globe.CubeEarth.FIT, "the geostationary circle fits the view");
        // the ground under a point is on a face of the cube
        var g = net.createaerospace.client.globe.CubeEarth.surface(Math.toRadians(33), Math.toRadians(-71), 0);
        h.assertTrue(Math.abs(Math.max(Math.abs(g[0]), Math.max(Math.abs(g[1]), Math.abs(g[2]))) - half) < 1e-9, "the ground is on a face");
        // clouds cover some of the sky, not none and not all, and move with time
        int cloudy = 0, cells = 0, changed = 0;
        for (int lat = -80; lat <= 80; lat += 10) for (int lon = -180; lon < 180; lon += 10) {
            double a = net.createaerospace.client.globe.CubeEarth.cloudiness(Math.toRadians(lat), Math.toRadians(lon), 1000);
            double b = net.createaerospace.client.globe.CubeEarth.cloudiness(Math.toRadians(lat), Math.toRadians(lon), 7000);
            boolean ca = a >= net.createaerospace.client.globe.CubeEarth.CLOUD_EDGE;
            cells++;
            if (ca) cloudy++;
            if (ca != b >= net.createaerospace.client.globe.CubeEarth.CLOUD_EDGE) changed++;
        }
        h.assertTrue(cloudy > cells / 6 && cloudy < cells / 2, "clouds cover part of the sky, got " + cloudy + " of " + cells);
        h.assertTrue(changed > cells / 20, "clouds move, " + changed + " cells changed");
        int[] cloudQuads = {0};
        land.drawClouds((a, b, c, d, e, f, gg, i, j, k, l, m, argb) -> cloudQuads[0]++, view, 1000);
        h.assertTrue(cloudQuads[0] > 0 && cloudQuads[0] < seaQuads[0], "some tiles in view are cloudy, got " + cloudQuads[0]);
        // the circle of an inclined orbit closes after one turn and starts at the satellite
        var s = new Satellite(UUID.randomUUID(), "ring", UUID.randomUUID(), "", 0, 2, 1.1, .4, 0, Satellite.BEACON);
        double[] at = OrbitMath.subpoint(s, 777), start = OrbitMath.orbitPoint(s, 777, 0), round = OrbitMath.orbitPoint(s, 777, 2 * Math.PI);
        h.assertTrue(Math.abs(at[0] - start[0]) < 1e-9 && Math.abs(at[1] - start[1]) < 1e-9, "the ring starts at the satellite");
        h.assertTrue(Math.abs(start[0] - round[0]) < 1e-9 && Math.abs(OrbitMath.wrap(start[1] - round[1])) < 1e-9, "the ring closes");
        h.succeed();
    }
}
