package net.createaerospace.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * How a rocket type of another mod looks. Register in client setup with the id of the rocket type; a type without a renderer is drawn
 * as CAE's rocket. The drawing is free: any model, texture, render type or animation through the pose and the buffers.
 */
public final class RocketRenderers {
    private static final Map<ResourceLocation, Renderer> RENDERERS = new ConcurrentHashMap<>();

    private RocketRenderers() {}

    public static void register(ResourceLocation rocketType, Renderer renderer) {
        Objects.requireNonNull(rocketType);
        Objects.requireNonNull(renderer);
        if (RENDERERS.putIfAbsent(rocketType, renderer) != null) throw new IllegalArgumentException("Duplicate CAE rocket renderer: " + rocketType);
    }

    public static Renderer get(ResourceLocation rocketType) {
        return rocketType == null ? null : RENDERERS.get(rocketType);
    }

    public interface Renderer {
        /** Every frame. The pose is at the middle of the rocket's base, y up, in blocks; the rocket is {@code height} tall. */
        void render(Context context);

        /**
         * Every client tick while the rocket climbs, at its base; the default is CAE's flame and smoke. Spawn particles with
         * {@code level.addParticle}, or nothing.
         */
        default void exhaust(ClientLevel level, double x, double y, double z, int ascentTicks, RandomSource random) {
            defaultExhaust(level, x, y, z, random);
        }

        /** Every client tick while a reusable first stage comes back to its pad (API 6); nothing by default. */
        default void returnExhaust(ClientLevel level, double x, double y, double z, int returnTicks, RandomSource random) {}
    }

    /**
     * {@code rocketItem} is the item the rocket was stood up with, with its components (colour, a name...), {@code ascentTicks} counts
     * from lift-off (0 while it stands), {@code loadedKg} and {@code payloads} are what is on board. {@code packedLight} is the light
     * at the rocket; a climbing rocket may want full brightness ({@code 0xF000F0}).
     * {@code returning} (API 6): a reusable first stage on its way back ({@code RocketApi.Recovery}); {@code returnTicks} counts from
     * the release of the payloads. The rocket item carries the flight count ({@code RocketApi.flights}).
     */
    public record Context(ResourceLocation type, ItemStack rocketItem, boolean ascending, int ascentTicks, int loadedKg, int maxPayloadKg,
                          int payloads, float width, float height, long gameTime, float partialTick, PoseStack pose,
                          MultiBufferSource buffers, int packedLight, boolean returning, int returnTicks) {}

    /** CAE's own exhaust, for a renderer that wants to add to it. */
    public static void defaultExhaust(ClientLevel level, double x, double y, double z, RandomSource random) {
        for (int i = 0; i < 4; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, x + (random.nextDouble() - .5) * .4, y - .2, z + (random.nextDouble() - .5) * .4, 0, -.35, 0);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x + (random.nextDouble() - .5) * .6, y - .5, z + (random.nextDouble() - .5) * .6, 0, -.05, 0);
        }
    }
}
