package net.createaerospace.orbit;

/**
 * Circular orbits as closed-form functions of game time, plus the mapping between world positions and latitude and longitude.
 * The Earth turns once per Minecraft day, so a 24000-tick orbit at 0 degrees inclination hangs over one spot.
 * Orbital periods are game-scaled, not Kepler's: a low orbit takes under two minutes.
 */
public final class OrbitMath {
    public static final double EARTH_KM = 6371;
    public static final double DAY_TICKS = 24000;
    /** Altitude tiers: low, high low, medium, geostationary. */
    public static final int[] ALT_KM = {500, 2000, 20200, 35786};
    public static final int[] PERIOD_TICKS = {2400, 3600, 12000, 24000};
    public static final int[] INCLINATION_DEG = {0, 30, 55, 90};
    /** One degree of latitude or longitude is this many blocks, so a low pass is visible from some 25000 blocks away. */
    public static final double BLOCKS_PER_DEGREE = 1000;
    public static final double MAX_LAT_DEG = 89;
    /**
     * A reentry: a minute of descent from the orbit to {@link #FIRE_TOP_KM} on the great circle towards the target, then a fireball
     * from {@link #FIRE_TOP_KM} down to {@link #FIRE_END_KM} over the last {@link #FIRE_ARC_BEFORE_DEG} degrees before the target and
     * {@link #FIRE_ARC_AFTER_DEG} past it. The path runs {@link #FIRE_SIDE_DEG} to the side of the target, so seen from there the
     * fireball streaks across the sky, high up, instead of coming straight at the watcher. ponytail:
     * game-scaled like the orbits (a real fireball lasts a minute or two and the descent much longer).
     */
    public static final int REENTRY_DESCENT_TICKS = 1200, REENTRY_FIRE_TICKS = 300;
    public static final double FIRE_TOP_KM = 120, FIRE_END_KM = 25, FIRE_ARC_BEFORE_DEG = 4, FIRE_ARC_AFTER_DEG = 0.5, FIRE_SIDE_DEG = 0.7;

    private OrbitMath() {}

    public record Look(double elevation, double azimuth, double rangeKm) {}

    /** Sub-satellite point in radians, {latitude, longitude}; on the way down during a reentry. */
    public static double[] subpoint(Satellite s, double tick) {
        if (s.reentering(tick)) return reentryPoint(s, tick);
        return orbitSubpoint(s, tick);
    }

    /** Height over the ground in km at a time: the orbit's, or falling during a reentry. */
    public static double altKm(Satellite s, double tick) {
        if (!s.reentering(tick)) return s.altKm();
        double t = tick - s.reentryStart();
        if (t < REENTRY_DESCENT_TICKS) {
            double left = 1 - t / REENTRY_DESCENT_TICKS;
            return FIRE_TOP_KM + (s.altKm() - FIRE_TOP_KM) * left * left;
        }
        double f = Math.min(1, (t - REENTRY_DESCENT_TICKS) / REENTRY_FIRE_TICKS);
        return FIRE_TOP_KM - (FIRE_TOP_KM - FIRE_END_KM) * f;
    }

    /** 0 to 1 through the fireball; negative before it. */
    public static double fireProgress(Satellite s, double tick) {
        return (tick - s.reentryStart() - REENTRY_DESCENT_TICKS) / REENTRY_FIRE_TICKS;
    }

    private static double[] reentryPoint(Satellite s, double tick) {
        double[] from = orbitSubpoint(s, s.reentryStart());
        double[] to = {Math.toRadians(latOf(s.targetZ())), Math.toRadians(lonOf(s.targetX()))};
        double[] a = unit(from), b = unit(to);
        double total = Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])));
        double before = Math.toRadians(FIRE_ARC_BEFORE_DEG), after = Math.toRadians(FIRE_ARC_AFTER_DEG);
        if (total < before + Math.toRadians(1) || total > Math.PI - 1e-3) {
            // already over the target (or exactly opposite it): come in from 30 degrees west along its parallel
            a = unit(new double[] {to[0], to[1] - Math.toRadians(30)});
            total = Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])));
        }
        // aim beside the target: tip it towards the pole of the path's great circle
        double px = a[1] * b[2] - a[2] * b[1], py = a[2] * b[0] - a[0] * b[2], pz = a[0] * b[1] - a[1] * b[0];
        double pl = Math.sqrt(px * px + py * py + pz * pz), side = Math.toRadians(FIRE_SIDE_DEG);
        b = new double[] {Math.cos(side) * b[0] + Math.sin(side) * px / pl, Math.cos(side) * b[1] + Math.sin(side) * py / pl,
                Math.cos(side) * b[2] + Math.sin(side) * pz / pl};
        total = Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])));
        double t = tick - s.reentryStart(), angle;
        if (t < REENTRY_DESCENT_TICKS) angle = (total - before) * t / REENTRY_DESCENT_TICKS;
        else angle = total - before + (before + after) * Math.min(1, (t - REENTRY_DESCENT_TICKS) / REENTRY_FIRE_TICKS);
        // slerp, which also runs on past the target
        double st = Math.sin(total), wa = Math.sin(total - angle) / st, wb = Math.sin(angle) / st;
        double x = wa * a[0] + wb * b[0], y = wa * a[1] + wb * b[1], z = wa * a[2] + wb * b[2];
        return new double[] {Math.asin(Math.max(-1, Math.min(1, z / Math.sqrt(x * x + y * y + z * z)))), Math.atan2(y, x)};
    }

    private static double[] unit(double[] latLon) {
        return new double[] {Math.cos(latLon[0]) * Math.cos(latLon[1]), Math.cos(latLon[0]) * Math.sin(latLon[1]), Math.sin(latLon[0])};
    }

    private static double[] orbitSubpoint(Satellite s, double tick) {
        return orbitPoint(s, tick, 0);
    }

    /**
     * The point under the orbit {@code ahead} radians further along it than the satellite is, with the Earth turned as it is at
     * {@code tick}: going once round (0 to 2 pi) draws the orbit as a closed ring, where the satellite's own track over the turning
     * Earth would not close.
     */
    public static double[] orbitPoint(Satellite s, double tick, double ahead) {
        double u = s.phase(tick) + ahead, i = Math.toRadians(INCLINATION_DEG[s.incl()]);
        double cu = Math.cos(u), su = Math.sin(u), cr = Math.cos(s.raan()), sr = Math.sin(s.raan()), ci = Math.cos(i);
        double x = cr * cu - sr * ci * su, y = sr * cu + cr * ci * su, z = Math.sin(i) * su;
        double lon = Math.atan2(y, x) - 2 * Math.PI * tick / DAY_TICKS;
        return new double[] {Math.asin(Math.max(-1, Math.min(1, z))), wrap(lon)};
    }

    /** Where the satellite is seen from a point on the ground: elevation, azimuth clockwise from north, slant range. */
    public static Look look(Satellite s, double tick, double latRad, double lonRad) {
        double[] p = subpoint(s, tick);
        double a = EARTH_KM + altKm(s, tick);
        double sx = a * Math.cos(p[0]) * Math.cos(p[1]), sy = a * Math.cos(p[0]) * Math.sin(p[1]), sz = a * Math.sin(p[0]);
        double ox = EARTH_KM * Math.cos(latRad) * Math.cos(lonRad), oy = EARTH_KM * Math.cos(latRad) * Math.sin(lonRad), oz = EARTH_KM * Math.sin(latRad);
        double dx = sx - ox, dy = sy - oy, dz = sz - oz;
        double e = -Math.sin(lonRad) * dx + Math.cos(lonRad) * dy;
        double n = -Math.sin(latRad) * Math.cos(lonRad) * dx - Math.sin(latRad) * Math.sin(lonRad) * dy + Math.cos(latRad) * dz;
        double up = Math.cos(latRad) * Math.cos(lonRad) * dx + Math.cos(latRad) * Math.sin(lonRad) * dy + Math.sin(latRad) * dz;
        return new Look(Math.atan2(up, Math.hypot(e, n)), Math.atan2(e, n), Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** World direction of a look: east is +x, south is +z. */
    public static double[] direction(Look l) {
        double h = Math.cos(l.elevation());
        return new double[] {Math.sin(l.azimuth()) * h, Math.sin(l.elevation()), -Math.cos(l.azimuth()) * h};
    }

    public static double latOf(double blockZ) {
        return Math.max(-MAX_LAT_DEG, Math.min(MAX_LAT_DEG, -blockZ / BLOCKS_PER_DEGREE));
    }

    public static double lonOf(double blockX) {
        return Math.toDegrees(wrap(Math.toRadians(blockX / BLOCKS_PER_DEGREE)));
    }

    public static double wrap(double rad) {
        rad = (rad + Math.PI) % (2 * Math.PI);
        return (rad < 0 ? rad + 2 * Math.PI : rad) - Math.PI;
    }

    public static String fmtLat(double rad) {
        return String.format(java.util.Locale.ROOT, "%.1f%s", Math.abs(Math.toDegrees(rad)), rad >= 0 ? "N" : "S");
    }

    public static String fmtLon(double rad) {
        return String.format(java.util.Locale.ROOT, "%.1f%s", Math.abs(Math.toDegrees(rad)), rad >= 0 ? "E" : "W");
    }

    /** The world block under the satellite: one degree is {@link #BLOCKS_PER_DEGREE} blocks, north is -z. */
    public static int[] groundBlock(double[] subpoint) {
        return new int[] {(int) Math.round(Math.toDegrees(subpoint[1]) * BLOCKS_PER_DEGREE), (int) Math.round(-Math.toDegrees(subpoint[0]) * BLOCKS_PER_DEGREE)};
    }

    /** The satellite as seen from a block position. */
    public static Look lookFromBlock(Satellite s, double tick, double blockX, double blockZ) {
        return look(s, tick, Math.toRadians(latOf(blockZ)), Math.toRadians(lonOf(blockX)));
    }
}
