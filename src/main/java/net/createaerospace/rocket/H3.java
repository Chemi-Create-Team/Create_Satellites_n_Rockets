package net.createaerospace.rocket;

import java.util.List;
import java.util.function.Supplier;
import net.createaerospace.CreateAerospace;
import net.createaerospace.api.AerospaceApi;
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
 * JAXA / MHI's H3 in its three flown configurations, at half scale (1 block = 2 m). Registered through {@link RocketApi} like any
 * rocket of another mod; drawn by {@code client.H3Renderer}. All lengths here are real metres.
 * <p>
 * Payload: GTO capacity (CAE puts satellites in geostationary orbit) for 22S (4.8 t, flight 5) and 24W (6.5 t, planned long-coast
 * GTO); 30S has no published GTO figure, so its 4 t to 500 km SSO is used. Fuel scales with lift-off mass (575 t of 24W = the full
 * pad), acceleration with lift-off thrust over weight.
 */
public final class H3 {
    public static final double SCALE = 0.5;
    /** Diameter of the core and of the S fairing; W fairing; SRB-3. */
    public static final double CORE_D = 5.2, WIDE_D = 5.4, SRB_D = 2.5;
    /** Overall length with the short and the long (W) fairing. */
    public static final double LENGTH_S = 57, LENGTH_W = 63;
    /** Fraction of the climb at which the SRB-3s and the fairing come off. */
    public static final double SRB_SEP = 0.35, FAIRING_SEP = 0.7;
    public static final int CLIMB = 256;
    public static final double TOP_SPEED = 2.5;

    public enum Variant {
        H3_30S("h3_30s", 3, 0, false, 4000, 3760, 0.015),
        H3_22S("h3_22s", 2, 2, false, 4800, 5830, 0.024),
        H3_24W("h3_24w", 2, 4, true, 6500, 8000, 0.034);

        public final String path;
        public final int engines, srbs, maxPayloadKg, fuelMb;
        public final boolean wide;
        public final double acceleration;

        Variant(String path, int engines, int srbs, boolean wide, int maxPayloadKg, int fuelMb, double acceleration) {
            this.path = path;
            this.engines = engines;
            this.srbs = srbs;
            this.wide = wide;
            this.maxPayloadKg = maxPayloadKg;
            this.fuelMb = fuelMb;
            this.acceleration = acceleration;
        }

        public ResourceLocation id() {
            return CreateAerospace.id(path);
        }

        public double lengthM() {
            return wide ? LENGTH_W : LENGTH_S;
        }

        /** Core and boosters side by side; the bounding box is square, so a 22 is as wide as a 24. */
        public double widthM() {
            return srbs == 0 ? CORE_D : CORE_D + 2 * (SRB_D + 0.1);
        }

        public RocketApi.RocketType type() {
            return new RocketApi.RocketType(Component.translatable("item.create_aerospace_engineering." + path), maxPayloadKg, fuelMb, CLIMB,
                    acceleration, TOP_SPEED, (float) (widthM() * SCALE), (float) (lengthM() * SCALE));
        }
    }

    private H3() {}

    /** Blocks climbed after {@code ticks} of ascent, the same sum {@code RocketEntity} moves by. */
    public static double climbed(double acceleration, double ticks) {
        int full = (int) Math.floor(ticks);
        int knee = (int) Math.min(full, Math.floor(TOP_SPEED / acceleration));
        double h = acceleration * knee * (knee + 1) / 2 + (full - knee) * TOP_SPEED;
        return h + (ticks - full) * Math.min(TOP_SPEED, acceleration * (full + 1));
    }

    /** First ascent tick at which the rocket has climbed {@code fraction} of its climb. */
    public static int tickAt(double acceleration, double fraction) {
        int t = 0;
        while (climbed(acceleration, t) < fraction * CLIMB) t++;
        return t;
    }

    public static void register(List<? extends Supplier<? extends Item>> items) {
        Variant[] v = Variant.values();
        for (int i = 0; i < v.length; i++) {
            Variant variant = v[i];
            RocketApi.registerRocket(variant.id(), items.get(i).get(), variant.type(), new RocketApi.Hooks() {
                @Override
                public void launched(ServerLevel level, Entity rocket, List<ItemStack> payloads) {
                    // the roar of the SRB-3s (or the softer start of a 30 on its LE-9s alone), heard far off
                    level.playSound(null, rocket.getX(), rocket.getY(), rocket.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                            variant.srbs == 0 ? 6f : 12f, variant.srbs == 0 ? 0.6f : 0.45f);
                }

                @Override
                public void deployed(ServerLevel level, Entity rocket, List<AerospaceApi.SatelliteInfo> satellites) {}
            });
        }
    }
}
