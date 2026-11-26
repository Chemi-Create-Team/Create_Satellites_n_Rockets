package net.createaerospace.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.createaerospace.photo.OrbitalPhoto;
import net.createaerospace.photo.PhotoPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SatellitePhotoScreen extends Screen {
    private static final String KEY = "photo.create_aerospace_engineering.";
    private final SatelliteMapScreen parent;
    private final PhotoPackets.Photo photo;
    private ResourceLocation texture;

    private SatellitePhotoScreen(SatelliteMapScreen parent, PhotoPackets.Photo photo) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.photo = photo;
    }

    public static void receive(PhotoPackets.Photo photo) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof SatelliteMapScreen map && map.console().equals(photo.console())
                && photo.pixels().length == OrbitalPhoto.BYTES) {
            mc.setScreen(new SatellitePhotoScreen(map, photo));
        }
    }

    @Override
    protected void init() {
        if (texture == null) {
            int sides = OrbitalPhoto.SIZE;
            var image = new NativeImage(sides, sides, false);
            byte[] rgb = photo.pixels();
            for (int y = 0; y < sides; y++) {
                for (int x = 0; x < sides; x++) {
                    int i = (y * sides + x) * 3;
                    image.setPixelRGBA(x, y, 0xFF000000 | (rgb[i + 2] & 255) << 16 | (rgb[i + 1] & 255) << 8 | rgb[i] & 255);
                }
            }
            texture = minecraft.getTextureManager().register("orbital_photo", new DynamicTexture(image));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 44, height - 25, 88, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF060B13);
        int size = Math.min(width - 24, height - 88);
        int x = (width - size) / 2, y = 48;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFBF5C);
        g.drawCenteredString(font, Component.translatable(KEY + "coordinates", photo.name(),
                photo.console().getX(), photo.console().getZ(), OrbitalPhoto.SIZE * OrbitalPhoto.BLOCKS_PER_PIXEL),
                width / 2, 25, 0xFF9DBDD6);
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF427BAC);
        g.blit(texture, x, y, size, size, 0f, 0f, OrbitalPhoto.SIZE, OrbitalPhoto.SIZE, OrbitalPhoto.SIZE, OrbitalPhoto.SIZE);
        super.render(g, mx, my, pt);
    }

    @Override
    public void removed() {
        if (texture != null) {
            minecraft.getTextureManager().release(texture);
            texture = null;
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
