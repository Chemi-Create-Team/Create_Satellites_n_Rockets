package net.createaerospace.rocket;

import java.util.List;
import net.createaerospace.CreateAerospace;
import net.createaerospace.api.RocketApi;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * SpaceX's Falcon 9 Block 5 at full size (1 block = 1 m), registered through {@link RocketApi} like any rocket of another mod and
 * drawn by {@code client.Falcon9Renderer}. Lengths here are real metres.
 * <p>
 * Payload: 5.5 t, the GTO capacity with the first stage recovered (CAE puts satellites in geostationary orbit; the real rocket lands
 * a GTO booster on a drone ship, here it flies back to its pad as the user asked). Fuel from the lift-off mass (549 t against the
 * 575 t of the H3-24W, which fills the pad's 8000 mB); acceleration from the lift-off thrust-to-weight of 1.4. Failure chance: the CAE default of 3%
 * (two lost flights in some 500). Recovery: 30% of first stages do not come back, 10% crash on landing (user's numbers).
 */
public final class Falcon9 {
    public static final ResourceLocation ID = CreateAerospace.id("falcon_9");
    /** Overall length, core and fairing diameters, first stage with the interstage, second stage top, fairing base. */
    public static final double LENGTH = 70, CORE_D = 3.66, FAIRING_D = 5.2, STAGE1_TOP = 41.2, STAGE2_TOP = 56.9, INTERSTAGE_BASE = 35.0;
    public static final double CLIMB = 256, ACCELERATION = 0.012, TOP_SPEED = 2.5;

    private Falcon9() {}

    // ---- the way back, by ticks since the release of the payloads (RocketApi.Recovery: hold 100, fall 240) ----

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double smooth(double v) {
        v = clamp01(v);
        return v * v * (3 - 2 * v);
    }

    /** Fraction of the fall, 0 to 1 (linear in time). */
    public static double fallTime(double t) {
        return clamp01((t - RocketApi.Recovery.HOLD_TICKS) / RocketApi.Recovery.FALL_TICKS);
    }

    /** Pitch of the stage in degrees: it flips to point its engines back for the boostback burn, then turns engines-down to fall. */
    public static double pitch(double t) {
        return 110 * (t < 75 ? smooth((t - 10) / 30) : 1 - smooth((t - 75) / 25));
    }

    public static boolean boostback(double t) {
        return t >= 40 && t < 75;
    }

    /** Three engines slow it high up so it survives the air; one engine brakes it to a stop over the pad. */
    public static boolean entryBurn(double t) {
        double f = fallTime(t);
        return t > RocketApi.Recovery.HOLD_TICKS && f >= 0.25 && f < 0.40;
    }

    public static boolean landingBurn(double t) {
        return fallTime(t) >= 0.72 && fallTime(t) < 1;
    }

    /** 0 stowed to 1 deployed. */
    public static double gridFins(double t) {
        return smooth((t - 20) / 20);
    }

    public static double legs(double t) {
        return smooth((fallTime(t) - 0.85) / 0.1);
    }

    public static RocketApi.RocketType type() {
        return new RocketApi.RocketType(Component.translatable("item.create_aerospace_engineering.falcon_9"), 5500, 7640, (int) CLIMB,
                ACCELERATION, TOP_SPEED, (float) FAIRING_D, (float) LENGTH, RocketApi.RocketType.DEFAULT_FAILURE_CHANCE, new RocketApi.Recovery(0.3, 0.1));
    }

    public static void register(Item item) {
        RocketApi.registerRocket(ID, item, type(), new RocketApi.Hooks() {
            @Override
            public void launched(ServerLevel level, Entity rocket, List<ItemStack> payloads) {
                // nine Merlins at full throttle
                level.playSound(null, rocket.getX(), rocket.getY(), rocket.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 10f, 0.5f);
            }
        });
    }
}
