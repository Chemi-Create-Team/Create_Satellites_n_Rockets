package net.createaerospace.client.globe;

import java.util.ArrayList;
import java.util.List;

/**
 * The Earth as Minecraft would build it: a sphere of cubes, the land one cube higher than the sea, white at the poles. Satellites are
 * small voxel models over it. Pure geometry with no Minecraft types: it hands coloured quads to a {@link Sink}, so the Control
 * Computer app, the map screen and a plain test program can all draw it.
 * <p>
 * Earth-fixed axes: x towards latitude 0 / longitude 0, y north, z towards longitude 90E. The view looks at one point of the Earth, with
 * east to the right and north up. Depth is larger towards the viewer.
 */
public final class VoxelGlobe {
    /** Radius of the sea in cubes. */
    public static final int N = 28;
    private static final float[] LIGHT = normalize(-.45f, .55f, .7f);
    private static final float AMBIENT = .52f, DIFFUSE = .48f;

    /** A coloured quad in screen units (x right, y down) with a depth for each corner. */
    public interface Sink {
        void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int argb);
    }

    public interface Land {
        boolean at(double latDeg, double lonDeg);
    }

    /** The cube faces on the surface, by direction: centre x, y, z of the cube and the colour, four numbers per face. */
    private final float[][] centers = new float[6][];
    private final int[][] colors = new int[6][];
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public VoxelGlobe(Land land) {
        int m = N + 3, size = 2 * m + 1;
        boolean[] solid = new boolean[size * size * size];
        boolean[] isLand = new boolean[size * size * size];
        for (int x = -m; x <= m; x++) for (int y = -m; y <= m; y++) for (int z = -m; z <= m; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d == 0) { solid[idx(x, y, z, m, size)] = true; continue; }
            double lat = Math.toDegrees(Math.asin(y / d)), lon = Math.toDegrees(Math.atan2(z, x));
            boolean l = land.at(lat, lon) || Math.abs(lat) > 72;
            isLand[idx(x, y, z, m, size)] = l;
            solid[idx(x, y, z, m, size)] = d <= N + (l ? 1.1 : 0);
        }
        for (int dir = 0; dir < 6; dir++) {
            List<float[]> c = new ArrayList<>();
            List<Integer> col = new ArrayList<>();
            int[] v = DIRS[dir];
            for (int x = -m + 1; x < m; x++) for (int y = -m + 1; y < m; y++) for (int z = -m + 1; z < m; z++) {
                if (!solid[idx(x, y, z, m, size)] || solid[idx(x + v[0], y + v[1], z + v[2], m, size)]) continue;
                c.add(new float[] {x, y, z});
                col.add(color(x, y, z, isLand[idx(x, y, z, m, size)]));
            }
            centers[dir] = new float[c.size() * 3];
            colors[dir] = new int[c.size()];
            for (int i = 0; i < c.size(); i++) {
                System.arraycopy(c.get(i), 0, centers[dir], i * 3, 3);
                colors[dir][i] = col.get(i);
            }
        }
    }

    private static int idx(int x, int y, int z, int m, int size) {
        return ((x + m) * size + (y + m)) * size + (z + m);
    }

    /** Sea blue, grass green with a little brown, snow at the poles; every cube a little different, like a texture of big pixels. */
    private static int color(int x, int y, int z, boolean land) {
        double d = Math.sqrt(x * x + y * y + z * z), lat = Math.toDegrees(Math.asin(y / d));
        int h = hash(x, y, z);
        float j = ((h & 255) / 255f - .5f) * .16f;
        int base;
        if (Math.abs(lat) > 72) base = 0xE8F0F8;
        else if (!land) base = (h >> 8 & 3) == 0 ? 0x2459B8 : 0x1F4FA6;
        else if (Math.abs(lat) > 62 || (h >> 8 & 7) == 0) base = 0x8A7A55;
        else base = (h >> 8 & 3) == 0 ? 0x3F8F3F : 0x4BA34A;
        return mul(0xFF000000 | base, 1 + j);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    private static int mul(int argb, float f) {
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

        /** {@code k} is screen units per cube. */
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
            float vx = (float) (q[0] * e[0] + q[1] * e[1] + q[2] * e[2]), vy = (float) (q[0] * n[0] + q[1] * n[1] + q[2] * n[2]);
            float vz = (float) (q[0] * p[0] + q[1] * p[1] + q[2] * p[2]);
            return vz < 0 && vx * vx + vy * vy < (N + 1.2f) * (N + 1.2f);
        }

        /** The same for a point given in cubes along the Earth-fixed axes; the screen position and the depth. */
        public float[] at(double[] earthFixed) {
            return project((float) earthFixed[0], (float) earthFixed[1], (float) earthFixed[2]);
        }
    }

    /** A point at a latitude and longitude (radians), {@code radius} cubes from the centre, along the Earth-fixed axes. */
    public static double[] position(double lat, double lon, double radius) {
        return new double[] {radius * Math.cos(lat) * Math.cos(lon), radius * Math.sin(lat), radius * Math.cos(lat) * Math.sin(lon)};
    }

    /** Orbit radius in cubes. Not to scale: a geostationary satellite would be 6.6 Earth radii away. */
    public static double orbitRadius(int tier) {
        return N * new double[] {1.16, 1.3, 1.52, 1.75}[Math.max(0, Math.min(3, tier))];
    }

    /** The cubes that face the viewer. */
    public void drawEarth(Sink sink, View v) {
        for (int dir = 0; dir < 6; dir++) {
            int[] d = DIRS[dir];
            float nx = d[0] * v.e[0] + d[1] * v.e[1] + d[2] * v.e[2], ny = d[0] * v.n[0] + d[1] * v.n[1] + d[2] * v.n[2];
            float nz = d[0] * v.p[0] + d[1] * v.p[1] + d[2] * v.p[2];
            if (nz < .02f) continue;
            float shade = AMBIENT + DIFFUSE * Math.max(0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]);
            // the two axes along the face
            int ax = d[0] != 0 ? 0 : d[1] != 0 ? 1 : 2, a = (ax + 1) % 3, b = (ax + 2) % 3;
            float[] c = centers[dir];
            for (int i = 0; i < colors[dir].length; i++) {
                float[] o = {c[i * 3], c[i * 3 + 1], c[i * 3 + 2]};
                o[0] += d[0] * .5f; o[1] += d[1] * .5f; o[2] += d[2] * .5f;
                float[][] q = new float[4][];
                int[][] s = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int j = 0; j < 4; j++) {
                    float[] pt = o.clone();
                    pt[a] += s[j][0] * .5f;
                    pt[b] += s[j][1] * .5f;
                    q[j] = v.project(pt[0], pt[1], pt[2]);
                }
                sink.quad(q[0][0], q[0][1], q[0][2], q[1][0], q[1][1], q[1][2], q[2][0], q[2][1], q[2][2], q[3][0], q[3][1], q[3][2],
                        mul(colors[dir][i], shade));
            }
        }
    }

    /** A star: a flat square far behind everything, at {@code depth}. */
    public static void star(Sink sink, float x, float y, float half, float depth, int argb) {
        sink.quad(x - half, y - half, depth, x + half, y - half, depth, x + half, y + half, depth, x - half, y + half, depth, argb);
    }

    /** A flat square facing the viewer, {@code half} cubes wide. For the dots of an orbit. */
    public static void dot(Sink sink, View v, double[] position, float half, int argb) {
        float[] p = v.at(position), h = {half * v.k, half * v.k};
        sink.quad(p[0] - h[0], p[1] - h[1], p[2], p[0] + h[0], p[1] - h[1], p[2], p[0] + h[0], p[1] + h[1], p[2], p[0] - h[0], p[1] + h[1], p[2], argb);
    }

    /** A satellite: a cube with two solar panels, turned a little so three sides show. {@code body} and {@code panel} are colours. */
    public static void satellite(Sink sink, View v, double[] position, float scale, int body, int panel) {
        float[] c = v.at(position);
        box(sink, v, c, 0, 0, 0, 1.1f * scale, 1.1f * scale, 1.1f * scale, body);
        box(sink, v, c, 3.1f * scale, 0, 0, 1.7f * scale, .14f * scale, .9f * scale, panel);
        box(sink, v, c, -3.1f * scale, 0, 0, 1.7f * scale, .14f * scale, .9f * scale, panel);
    }

    /** A gold-foil bus, long blue solar wings and two gold mesh reflectors. */
    public static void communicationsSatellite(Sink sink, View v, double[] position, float s) {
        float[] c = v.at(position);
        box(sink, v, c, 0, 0, 0, .9f * s, 1.3f * s, .75f * s, 0xFFCDA64D);
        for (int side : new int[] {-1, 1}) {
            box(sink, v, c, 0, side * 2.5f * s, 0, .76f * s, 1.15f * s, .12f * s, 0xFF314B83);
            box(sink, v, c, side * 1.3f * s, .38f * s, 0, .55f * s, .16f * s, .16f * s, 0xFF9A742B);
            for (int row = -2; row <= 2; row++) {
                float reach = Math.abs(row) == 2 ? .9f : 1.38f;
                box(sink, v, c, side * 3.05f * s, row * .5f * s, (.38f + .11f * row * row) * s,
                        reach * s, .24f * s, .105f * s, row % 2 == 0 ? 0xFFE8CD6F : 0xFFF7E392);
            }
        }
    }

    private static final double YAW = Math.toRadians(-32), PITCH = Math.toRadians(24);

    /** An axis-aligned box in model space (x right, y up, z towards the viewer), turned by a fixed yaw and pitch, shown at screen point {@code c}. */
    private static void box(Sink sink, View v, float[] c, float ox, float oy, float oz, float hx, float hy, float hz, int argb) {
        float sy = (float) Math.sin(YAW), cyw = (float) Math.cos(YAW), sp = (float) Math.sin(PITCH), cp = (float) Math.cos(PITCH);
        float[] half = {hx, hy, hz};
        // the faces that show after the turn: +z (front), +x (right) and +y (top), shaded as a cube is by the light
        float[] shades = {.72f, 1.1f, .95f};
        int[] axes = {0, 1, 2};
        for (int f = 0; f < 3; f++) {
            int ax = axes[f], a = (ax + 1) % 3, b = (ax + 2) % 3;
            float[][] q = new float[4][];
            int[][] s = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int j = 0; j < 4; j++) {
                float[] pt = {ox, oy, oz};
                pt[ax] += half[ax];
                pt[a] += s[j][0] * half[a];
                pt[b] += s[j][1] * half[b];
                float x = pt[0] * cyw + pt[2] * sy, z = -pt[0] * sy + pt[2] * cyw;
                float y = pt[1] * cp - z * sp, z2 = pt[1] * sp + z * cp;
                q[j] = new float[] {c[0] + x * v.k, c[1] - y * v.k, c[2] + z2 * v.k};
            }
            sink.quad(q[0][0], q[0][1], q[0][2], q[1][0], q[1][1], q[1][2], q[2][0], q[2][1], q[2][2], q[3][0], q[3][1], q[3][2], mul(argb, shades[f]));
        }
    }
}
