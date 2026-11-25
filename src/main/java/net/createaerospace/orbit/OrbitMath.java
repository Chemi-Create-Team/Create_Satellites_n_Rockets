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

    private OrbitMath() {}

    public record Look(double elevation, double azimuth, double rangeKm) {}

    /** Sub-satellite point in radians, {latitude, longitude}. */
    public static double[] subpoint(Satellite s, double tick) {
        double u = s.phase(tick), i = Math.toRadians(INCLINATION_DEG[s.incl()]);
        double cu = Math.cos(u), su = Math.sin(u), cr = Math.cos(s.raan()), sr = Math.sin(s.raan()), ci = Math.cos(i);
        double x = cr * cu - sr * ci * su, y = sr * cu + cr * ci * su, z = Math.sin(i) * su;
        double lon = Math.atan2(y, x) - 2 * Math.PI * tick / DAY_TICKS;
        return new double[] {Math.asin(Math.max(-1, Math.min(1, z))), wrap(lon)};
    }

    /** Where the satellite is seen from a point on the ground: elevation, azimuth clockwise from north, slant range. */
    public static Look look(Satellite s, double tick, double latRad, double lonRad) {
        double[] p = subpoint(s, tick);
        double a = EARTH_KM + s.altKm();
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
