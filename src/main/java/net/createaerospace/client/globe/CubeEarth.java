package net.createaerospace.client.globe;

/**
 * The Earth as an abstract cube: six flat faces of 16 by 16 tiles, sea, land and ice, like a map of big pixels. Satellites are dots
 * over it and their orbits are lines. Orbits are shells of the same cube shape, so a satellite keeps the same height over the face it
 * is over and an orbit never cuts through the Earth. Pure geometry with no Minecraft types: it hands coloured quads to a {@link Sink},
 * so the Control Computer app, the map screen and a plain test program can all draw it.
 * <p>
 * Earth-fixed axes: x towards latitude 0 / longitude 0, y north, z towards longitude 90E. The view looks at one point of the Earth, with
 * east to the right and north up. Depth is larger towards the viewer.
 */
public final class CubeEarth {
    /** Tiles along each edge of a face. */
    public static final int N = 16;
    /** Half the edge of the cube, in tiles: the faces are at plus and minus this. */
    public static final float HALF = N / 2f;
    /**
     * Half the width of the view in tiles. A geostationary orbit is a square ring {@code 2 HALF} out, so its corners reach
     * {@code 2 HALF} times the square root of 2. ponytail: an inclined high orbit through a corner of its shell can reach
     * {@code 2 HALF} times the square root of 3 and pokes a little out of the view; the map screen clips it, the computer app does not.
     */
    public static final float FIT = 2.9f * HALF;
    /** Further from the viewer than anything but the stars, in tiles. */
    public static final float DEPTH = 30;
    private static final float[] LIGHT = normalize(-.45f, .55f, .7f);
    private static final float AMBIENT = .58f, DIFFUSE = .42f;
    /** Drawn heights of the four orbit tiers, in Earth half-widths. Not to scale: a geostationary satellite would be 6.6 Earth radii away. */
    private static final double[] SHELLS = {1.2, 1.4, 1.7, 2.0};

    /** A coloured quad in screen units (x right, y down) with a depth for each corner. */
    public interface Sink {
        void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int argb);
    }

    public interface Land {
        boolean at(double latDeg, double lonDeg);
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** For each face, the corners of its tiles (twelve numbers per tile) and their colours. */
    private final float[][] corners = new float[6][N * N * 12];
    private final int[][] colors = new int[6][N * N];

    public CubeEarth(Land land) {
        for (int dir = 0; dir < 6; dir++) {
            int[] d = DIRS[dir];
            int ax = axis(d), a = (ax + 1) % 3, b = (ax + 2) % 3;
            for (int i = 0; i < N; i++) for (int j = 0; j < N; j++) {
                int t = i * N + j;
                // a tile is land when most of it is: four by four looks into the mask
                int count = 0;
                for (int si = 0; si < 4; si++) for (int sj = 0; sj < 4; sj++) {
                    double[] ll = latLon(d, ax, a, b, -HALF + i + (si + .5) / 4, -HALF + j + (sj + .5) / 4);
                    if (land.at(ll[0], ll[1])) count++;
                }
                double lat = latLon(d, ax, a, b, -HALF + i + .5, -HALF + j + .5)[0];
                colors[dir][t] = color(lat, count >= 8, (i + j & 1) == 0, hash(dir, i, j));
                float[][] s = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int k = 0; k < 4; k++) {
                    float[] pt = new float[3];
                    pt[ax] = d[ax] * HALF;
                    pt[a] = -HALF + s[k][0];
                    pt[b] = -HALF + s[k][1];
                    System.arraycopy(pt, 0, corners[dir], t * 12 + k * 3, 3);
                }
            }
        }
    }

    private static int axis(int[] d) {
        return d[0] != 0 ? 0 : d[1] != 0 ? 1 : 2;
    }

    private static double[] latLon(int[] d, int ax, int a, int b, double u, double v) {
        double[] p = new double[3];
        p[ax] = d[ax] * HALF;
        p[a] = u;
        p[b] = v;
        double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new double[] {Math.toDegrees(Math.asin(p[1] / r)), Math.toDegrees(Math.atan2(p[2], p[0]))};
    }

    /** Flat colours: sea blue, grass green, tundra brown, ice white; every other tile a little darker so the grid shows. */
    private static int color(double lat, boolean land, boolean even, int h) {
        int base;
        if (Math.abs(lat) > 72 || land && lat < -60) base = 0xE8F0F8;
        else if (!land) base = 0x2157B0;
        else if (Math.abs(lat) > 58) base = 0x8A7A55;
        else base = (h & 7) == 0 ? 0x8A7A55 : 0x46A045;
        return mul(0xFF000000 | base, (even ? 1 : .9f) + ((h >> 4 & 15) / 15f - .5f) * .05f);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    public static int mul(int argb, float f) {
        int r = Math.min(255, Math.max(0, Math.round((argb >> 16 & 255) * f)));
        int g = Math.min(255, Math.max(0, Math.round((argb >> 8 & 255) * f)));
        int b = Math.min(255, Math.max(0, Math.round((argb & 255) * f)));
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static float[] normalize(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / l, y / l, z / l};
    }

    /** The point of the Earth in the middle of the view, and where it goes on the screen. */
    public static final class View {
        final float[] e, n, p;
        final float cx, cy, k;

        /** {@code k} is screen units per tile. */
        public View(double latDeg, double lonDeg, float cx, float cy, float k) {
            double la = Math.toRadians(latDeg), lo = Math.toRadians(lonDeg);
            p = new float[] {(float) (Math.cos(la) * Math.cos(lo)), (float) Math.sin(la), (float) (Math.cos(la) * Math.sin(lo))};
            e = new float[] {(float) -Math.sin(lo), 0, (float) Math.cos(lo)};
            n = new float[] {(float) (-Math.sin(la) * Math.cos(lo)), (float) Math.cos(la), (float) (-Math.sin(la) * Math.sin(lo))};
            this.cx = cx;
            this.cy = cy;
            this.k = k;
        }

        float[] project(float x, float y, float z) {
            float vx = x * e[0] + y * e[1] + z * e[2], vy = x * n[0] + y * n[1] + z * n[2], vz = x * p[0] + y * p[1] + z * p[2];
            return new float[] {cx + k * vx, cy - k * vy, k * vz};
        }

        /** Whether the Earth is between the viewer and a point (given along the Earth-fixed axes), so that a label for it would float on the wrong side. */
        public boolean hidden(double[] q) {
            // the view is straight along p, so the point is hidden when the ray from it towards the viewer goes through the cube
            double near = 0, far = Double.MAX_VALUE, h = HALF + .3;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(p[i]) < 1e-6) {
                    if (Math.abs(q[i]) > h) return false;
                    continue;
                }
                double t1 = (-h - q[i]) / p[i], t2 = (h - q[i]) / p[i];
                near = Math.max(near, Math.min(t1, t2));
                far = Math.min(far, Math.max(t1, t2));
                if (near > far) return false;
            }
            return true;
        }

        /** Where a point given in tiles along the Earth-fixed axes goes: the screen position and the depth. */
        public float[] at(double[] earthFixed) {
            return project((float) earthFixed[0], (float) earthFixed[1], (float) earthFixed[2]);
        }

        /** Screen units per tile. */
        public float scale() {
            return k;
        }
    }

    /**
     * A point over a latitude and longitude (radians), on the cube-shaped shell {@code shell} tiles out from the centre (the faces of
     * the Earth are the shell {@link #HALF}), along the Earth-fixed axes.
     */
    public static double[] position(double lat, double lon, double shell) {
        double x = Math.cos(lat) * Math.cos(lon), y = Math.sin(lat), z = Math.cos(lat) * Math.sin(lon);
        double f = shell / Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        return new double[] {x * f, y * f, z * f};
    }

    /** The shell of an orbit tier, in tiles. */
    public static double orbitRadius(int tier) {
        return HALF * SHELLS[Math.max(0, Math.min(SHELLS.length - 1, tier))];
    }

    /** The faces that look towards the viewer, tile by tile, each face lit by a fixed light from the upper left. */
    public void drawEarth(Sink sink, View v) {
        for (int dir = 0; dir < 6; dir++) {
            int[] d = DIRS[dir];
            float nx = d[0] * v.e[0] + d[1] * v.e[1] + d[2] * v.e[2], ny = d[0] * v.n[0] + d[1] * v.n[1] + d[2] * v.n[2];
            float nz = d[0] * v.p[0] + d[1] * v.p[1] + d[2] * v.p[2];
            if (nz < .02f) continue;
            float shade = AMBIENT + DIFFUSE * Math.max(0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]);
            float[] c = corners[dir];
            for (int t = 0; t < N * N; t++) {
                int o = t * 12;
                float[] q0 = v.project(c[o], c[o + 1], c[o + 2]), q1 = v.project(c[o + 3], c[o + 4], c[o + 5]);
                float[] q2 = v.project(c[o + 6], c[o + 7], c[o + 8]), q3 = v.project(c[o + 9], c[o + 10], c[o + 11]);
                sink.quad(q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], q2[0], q2[1], q2[2], q3[0], q3[1], q3[2], mul(colors[dir][t], shade));
            }
        }
    }

    /** A star: a flat square far behind everything, at {@code depth}. */
    public static void star(Sink sink, float x, float y, float half, float depth, int argb) {
        sink.quad(x - half, y - half, depth, x + half, y - half, depth, x + half, y + half, depth, x - half, y + half, depth, argb);
    }

    /** A flat square facing the viewer, {@code half} tiles wide, {@code lift} tiles nearer the viewer than the point. */
    public static void dot(Sink sink, View v, double[] position, float half, float lift, int argb) {
        float[] p = v.at(position);
        float h = half * v.k, z = p[2] + lift * v.k;
        sink.quad(p[0] - h, p[1] - h, z, p[0] + h, p[1] - h, z, p[0] + h, p[1] + h, z, p[0] - h, p[1] + h, z, argb);
    }

    /** A square standing on a corner, for a communications satellite. */
    public static void diamond(Sink sink, View v, double[] position, float half, float lift, int argb) {
        float[] p = v.at(position);
        float h = half * v.k * 1.3f, z = p[2] + lift * v.k;
        sink.quad(p[0], p[1] - h, z, p[0] + h, p[1], z, p[0], p[1] + h, z, p[0] - h, p[1], z, argb);
    }

    /** A straight line between two points, {@code half} tiles to each side, flat towards the viewer. */
    public static void line(Sink sink, View v, double[] from, double[] to, float half, int argb) {
        float[] a = v.at(from), b = v.at(to);
        float dx = b[0] - a[0], dy = b[1] - a[1], l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        // a little past each end, so that the segments of a ring meet without gaps
        float ux = dx / l, uy = dy / l, w = half * v.k, ex = ux * w * .5f, ey = uy * w * .5f, nx = -uy * w, ny = ux * w;
        sink.quad(a[0] - ex + nx, a[1] - ey + ny, a[2], b[0] + ex + nx, b[1] + ey + ny, b[2],
                b[0] + ex - nx, b[1] + ey - ny, b[2], a[0] - ex - nx, a[1] - ey - ny, a[2], argb);
    }
}
