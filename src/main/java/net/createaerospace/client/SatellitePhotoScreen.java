package net.createaerospace.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createaerospace.photo.OrbitalPhoto;
import net.createaerospace.photo.PhotoPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SatellitePhotoScreen extends Screen {
    private static final String KEY = "photo.create_aerospace_engineering.";
    private static final int OVERVIEW = OrbitalPhoto.OVERVIEW_STEP;
    private static final int MAX_TEXTURES = 42, MAX_PENDING = 4;
    private static final double MID = OrbitalPhoto.RANGE / 2.0;
    private final SatelliteMapScreen parent;
    private final PhotoPackets.Photo photo;
    private final Map<OrbitalPhoto.Tile, ResourceLocation> textures = new LinkedHashMap<>(16, .75f, true);
    private final Map<OrbitalPhoto.Tile, Integer> pending = new HashMap<>();
    private double centerX = MID, centerZ = MID;
    private int zoom;
    private int ticks;
    private int left, top, size;
    private Button zoomIn, zoomOut;

    private SatellitePhotoScreen(SatelliteMapScreen parent, PhotoPackets.Photo photo) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.photo = photo;
    }

    public static void receive(PhotoPackets.Photo photo) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof SatelliteMapScreen map && map.console().equals(photo.console())) {
            mc.setScreen(new SatellitePhotoScreen(map, photo));
        }
    }

    public static void receive(PhotoPackets.Image image) {
        if (Minecraft.getInstance().screen instanceof SatellitePhotoScreen screen && screen.photo.id().equals(image.id())) {
            screen.addImage(image);
        }
    }

    private void addImage(PhotoPackets.Image incoming) {
        if (pending.remove(incoming.tile()) == null || incoming.pixels().length != OrbitalPhoto.BYTES) return;
        int sides = OrbitalPhoto.SIZE;
        var image = new NativeImage(sides, sides, false);
        byte[] rgb = incoming.pixels();
        for (int y = 0; y < sides; y++) {
            for (int x = 0; x < sides; x++) {
                int i = (y * sides + x) * 3;
                image.setPixelRGBA(x, y, 0xFF000000 | (rgb[i + 2] & 255) << 16 | (rgb[i + 1] & 255) << 8 | rgb[i] & 255);
            }
        }
        var manager = minecraft.getTextureManager();
        var old = textures.put(incoming.tile(), manager.register("orbital_photo", new DynamicTexture(image)));
        if (old != null) manager.release(old);
        if (textures.size() > MAX_TEXTURES) {
            var oldest = textures.entrySet().iterator();
            while (oldest.hasNext()) {
                var tile = oldest.next();
                if (tile.getKey().step() == OVERVIEW) continue;
                manager.release(tile.getValue());
                oldest.remove();
                break;
            }
        }
    }

    @Override
    protected void init() {
        size = Math.max(1, Math.min(width - 24, height - 104));
        left = (width - size) / 2;
        top = 50 + Math.max(0, (height - 104 - size) / 2);
        int x = width / 2, y = height - 27;
        zoomOut = addRenderableWidget(Button.builder(Component.literal("−"), b -> setZoom(zoom - 1, .5, .5))
                .bounds(x - 96, y, 23, 20).build());
        zoomIn = addRenderableWidget(Button.builder(Component.literal("+"), b -> setZoom(zoom + 1, .5, .5))
                .bounds(x - 70, y, 23, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "overview"), b -> setZoom(0, .5, .5))
                .bounds(x - 42, y, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(x + 42, y, 62, 20).build());
        updateButtons();
    }

    @Override
    public void tick() {
        ticks++;
        pending.values().removeIf(deadline -> ticks >= deadline);
        var needed = tiles(step());
        // Load the overview in the background so a zoomed view has a coarse image while finer tiles are in flight.
        if (step() != OVERVIEW) needed.addAll(tiles(OVERVIEW));
        for (var tile : needed) {
            if (pending.size() >= MAX_PENDING) break;
            if (textures.containsKey(tile) || pending.containsKey(tile)) continue;
            pending.put(tile, ticks + 1800);
            PacketDistributor.sendToServer(new PhotoPackets.TileRequest(photo.id(), tile));
        }
    }

    private int step() { return zoom == 0 ? OVERVIEW : zoom == 1 ? 2 : 1; }
    private int span() { return OrbitalPhoto.RANGE >> zoom; }

    private List<OrbitalPhoto.Tile> tiles(int step) {
        int area = OrbitalPhoto.SIZE * step;
        int last = OrbitalPhoto.RANGE / area - 1;
        double half = span() / 2.0;
        int x0 = Math.max(0, (int) ((centerX - half) / area));
        int z0 = Math.max(0, (int) ((centerZ - half) / area));
        int x1 = Math.min(last, (int) Math.ceil((centerX + half) / area) - 1);
        int z1 = Math.min(last, (int) Math.ceil((centerZ + half) / area) - 1);
        List<OrbitalPhoto.Tile> tiles = new ArrayList<>();
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) tiles.add(new OrbitalPhoto.Tile(step, x, z));
        tiles.sort(Comparator.comparingDouble(tile -> {
            double dx = (tile.x() + .5) * area - centerX, dz = (tile.z() + .5) * area - centerZ;
            return dx * dx + dz * dz;
        }));
        return tiles;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF060B13);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFBF5C);
        g.drawCenteredString(font, Component.translatable(KEY + "coordinates", photo.name(),
                photo.console().getX(), photo.console().getZ(), OrbitalPhoto.RANGE), width / 2, 24, 0xFF9DBDD6);
        g.drawCenteredString(font, Component.translatable(KEY + "viewport", span(), step()), width / 2, 37, 0xFF9DBDD6);

        g.fill(left - 1, top - 1, left + size + 1, top + size + 1, 0xFF427BAC);
        g.fill(left, top, left + size, top + size, 0xFF172537);
        g.enableScissor(left, top, left + size, top + size);
        drawLayer(g, OVERVIEW);
        if (step() != OVERVIEW) drawLayer(g, step());
        g.disableScissor();
        var footer = Component.translatable(KEY + (pending.isEmpty() ? "controls" : "loading"));
        g.drawCenteredString(font, footer, width / 2, height - 40, 0xFF9DBDD6);
        super.render(g, mx, my, pt);
    }

    private void drawLayer(GuiGraphics g, int step) {
        double pixels = (double) size / span(), x0 = centerX - span() / 2.0, z0 = centerZ - span() / 2.0;
        for (var tile : tiles(step)) {
            var texture = textures.get(tile);
            if (texture == null) continue;
            int x = left + (int) Math.round((tile.x() * tile.span() - x0) * pixels);
            int y = top + (int) Math.round((tile.z() * tile.span() - z0) * pixels);
            int right = left + (int) Math.round(((tile.x() + 1) * tile.span() - x0) * pixels);
            int bottom = top + (int) Math.round(((tile.z() + 1) * tile.span() - z0) * pixels);
            g.blit(texture, x, y, right - x, bottom - y, 0f, 0f, OrbitalPhoto.SIZE, OrbitalPhoto.SIZE, OrbitalPhoto.SIZE, OrbitalPhoto.SIZE);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!overPhoto(mx, my) || vertical == 0) return super.mouseScrolled(mx, my, horizontal, vertical);
        setZoom(zoom + (vertical > 0 ? 1 : -1), (mx - left) / size, (my - top) / size);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0 || !overPhoto(mx, my)) return super.mouseDragged(mx, my, button, dx, dy);
        centerX -= dx * span() / size;
        centerZ -= dy * span() / size;
        clampCenter();
        return true;
    }

    private boolean overPhoto(double x, double y) {
        return x >= left && x < left + size && y >= top && y < top + size;
    }

    private void setZoom(int next, double ax, double az) {
        int old = span();
        zoom = Math.clamp(next, 0, 4);
        centerX += (ax - .5) * (old - span());
        centerZ += (az - .5) * (old - span());
        clampCenter();
        updateButtons();
    }

    private void clampCenter() {
        double half = span() / 2.0;
        centerX = Math.clamp(centerX, half, OrbitalPhoto.RANGE - half);
        centerZ = Math.clamp(centerZ, half, OrbitalPhoto.RANGE - half);
    }

    private void updateButtons() {
        zoomOut.active = zoom > 0;
        zoomIn.active = zoom < 4;
    }

    @Override
    public void removed() {
        textures.values().forEach(minecraft.getTextureManager()::release);
        textures.clear();
        pending.clear();
        if (minecraft.getConnection() != null) PacketDistributor.sendToServer(
                new PhotoPackets.TileRequest(photo.id(), new OrbitalPhoto.Tile(0, 0, 0)));
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
