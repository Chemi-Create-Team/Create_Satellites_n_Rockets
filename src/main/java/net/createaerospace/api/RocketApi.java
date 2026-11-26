package net.createaerospace.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Rockets of other mods. A mod registers an item as a rocket type with its numbers (payload limit, fuel, flight) and, on the client,
 * how it is drawn ({@code api.client.RocketRenderers}). A player uses the item on a launch pad to stand the rocket up, loads payloads
 * onto it up to its mass limit, and fires it with redstone; every payload becomes a satellite.
 * <p>
 * Same rules as {@link AerospaceApi}: Minecraft and Java types only, server thread, check that CAE is loaded first. See {@code docs/api.md}.
 */
public final class RocketApi {
    /** CAE's own rocket. */
    public static final ResourceLocation STANDARD = ResourceLocation.fromNamespaceAndPath("create_aerospace_engineering", "rocket");
    /** The pad holds this much fuel, so no rocket can burn more in one launch. */
    public static final int MAX_FUEL_MB = 8000;
    public static final int MAX_WIDTH = 16, MAX_HEIGHT = 100;

    private static final Map<ResourceLocation, Registered> TYPES = new HashMap<>();
    private static final Map<Item, ResourceLocation> ITEMS = new HashMap<>();

    private RocketApi() {}

    /**
     * What a rocket can do. {@code maxPayloadKg} is the most the payloads on it may weigh together. {@code fuelMb} is burnt from the
     * pad at lift-off. The rocket climbs {@code climbBlocks} (or until the nose nears the build limit, with a minimum margin of 4 blocks from the base) with {@code acceleration} blocks
     * per tick per tick up to {@code topSpeed} blocks per tick, then releases its payloads. {@code width} and {@code height} are the
     * size of the entity in blocks; it stands on the pad, so the space above the pad must be free for it. {@code failureChance} (0 to
     * 1, API 5) is how often a launch fails: the rocket explodes on the way up and its payloads are lost. The game rule
     * {@code caeLaunchFailures} turns failures off for a world. {@code recovery} (API 6, null for an expendable rocket) makes the
     * first stage fly back to its pad after releasing the payloads.
     */
    public record RocketType(Component name, int maxPayloadKg, int fuelMb, int climbBlocks, double acceleration, double topSpeed,
                             float width, float height, double failureChance, Recovery recovery) {
        /** The chance of a failure when a type does not give one (API 4 and older). */
        public static final double DEFAULT_FAILURE_CHANCE = 0.03;

        public RocketType {
            Objects.requireNonNull(name);
            if (maxPayloadKg <= 0 || fuelMb < 0 || fuelMb > MAX_FUEL_MB || climbBlocks < 16 || climbBlocks > 1024
                    || !(acceleration > 0) || !(topSpeed > 0) || topSpeed > 10 || !(width > 0) || width > MAX_WIDTH || !(height > 0) || height > MAX_HEIGHT
                    || !(failureChance >= 0) || failureChance > 1
                    || recovery != null && (!(recovery.noReturnChance() >= 0) || !(recovery.landingFailureChance() >= 0)
                        || recovery.noReturnChance() + recovery.landingFailureChance() > 1)) {
                throw new IllegalArgumentException("Invalid CAE rocket type: " + name.getString());
            }
        }

        /** API 4 and older: the default failure chance. */
        public RocketType(Component name, int maxPayloadKg, int fuelMb, int climbBlocks, double acceleration, double topSpeed, float width, float height) {
            this(name, maxPayloadKg, fuelMb, climbBlocks, acceleration, topSpeed, width, height, DEFAULT_FAILURE_CHANCE, null);
        }

        /** API 5: expendable. */
        public RocketType(Component name, int maxPayloadKg, int fuelMb, int climbBlocks, double acceleration, double topSpeed, float width, float height,
                          double failureChance) {
            this(name, maxPayloadKg, fuelMb, climbBlocks, acceleration, topSpeed, width, height, failureChance, null);
        }

        /** Ticks from lift-off to the release of the payloads, if the build limit does not stop the climb first. */
        public int ticksToOrbit() {
            int t = 0;
            double h = 0;
            while (h < climbBlocks) h += Math.min(topSpeed, acceleration * ++t);
            return t;
        }
    }

    /**
     * A reusable first stage (API 6). After releasing the payloads it holds at the top of the climb for {@link #HOLD_TICKS} (flip and
     * boostback), falls back for {@link #FALL_TICKS} and touches down on its pad. Of all flights, {@code noReturnChance} never come
     * back and {@code landingFailureChance} explode on touchdown; the rest land. A landed stage needs a service (the service gantry)
     * before it takes payloads again, and fuel in the pad as always. The game rule {@code caeLaunchFailures} off lands every one.
     */
    public record Recovery(double noReturnChance, double landingFailureChance) {
        public static final int HOLD_TICKS = 100, FALL_TICKS = 240;

        /** 0 to 1 through the fall back to the pad, at {@code returnTicks} after the release: smooth, still at both ends. */
        public static double fall(double returnTicks) {
            double s = Math.max(0, Math.min(1, (returnTicks - HOLD_TICKS) / FALL_TICKS));
            return s * s * (3 - 2 * s);
        }
    }

    /** How many flights a reusable rocket item has made. */
    public static int flights(ItemStack rocket) {
        return rocket.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                .copyTag().getInt("CaeFlights");
    }

    /** Whether a landed rocket item must be serviced before it flies again. */
    public static boolean needsService(ItemStack rocket) {
        return rocket.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                .copyTag().getBoolean("CaeNeedsService");
    }

    /** A copy of the item with its flight count and service flag. */
    public static ItemStack withService(ItemStack rocket, int flights, boolean needsService) {
        ItemStack out = rocket.copy();
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, out, t -> {
            t.putInt("CaeFlights", flights);
            if (needsService) t.putBoolean("CaeNeedsService", true);
            else t.remove("CaeNeedsService");
        });
        return out;
    }

    /** Optional server callbacks. The entity is the CAE rocket; do not discard it or move it. */
    public interface Hooks {
        /** The pad has burnt the fuel and the rocket starts to climb. {@code payloads} are copies. */
        default void launched(ServerLevel level, Entity rocket, List<ItemStack> payloads) {}

        /** The rocket released its payloads; {@code satellites} are the ones now in orbit, in loading order. */
        default void deployed(ServerLevel level, Entity rocket, List<AerospaceApi.SatelliteInfo> satellites) {}

        /** The launch failed: the rocket is exploding and {@code payloads} (copies) are lost. API 5. */
        default void failed(ServerLevel level, Entity rocket, List<ItemStack> payloads) {}
    }

    public record Registered(ResourceLocation id, Item item, RocketType type, Hooks hooks) {}

    /** Call during common setup (both sides: the client sizes the entity and shows the numbers), with an id in your own namespace. */
    public static void registerRocket(ResourceLocation id, Item item, RocketType type, Hooks hooks) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(item);
        Objects.requireNonNull(type);
        synchronized (AerospaceApi.LOCK) {
            if (id.toString().length() > 128 || TYPES.containsKey(id) || ITEMS.containsKey(item) || AerospaceApi.payloadOf(new ItemStack(item)) != null) {
                throw new IllegalArgumentException("Invalid or duplicate CAE rocket: " + id + " / " + BuiltInRegistries.ITEM.getKey(item));
            }
            TYPES.put(id, new Registered(id, item, type, hooks == null ? new Hooks() {} : hooks));
            ITEMS.put(item, id);
        }
    }

    public static void registerRocket(ResourceLocation id, Item item, RocketType type) {
        registerRocket(id, item, type, null);
    }

    /** The rocket type an item stands up, or null. */
    public static ResourceLocation rocketOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        synchronized (AerospaceApi.LOCK) {
            return ITEMS.get(stack.getItem());
        }
    }

    /** A registered type, or null (its mod is gone). */
    public static Registered rocket(ResourceLocation id) {
        if (id == null) return null;
        synchronized (AerospaceApi.LOCK) {
            return TYPES.get(id);
        }
    }

    /** A registered type, or CAE's own when it is unknown, so that a rocket of a removed mod still flies and drops its payloads. */
    public static Registered rocketOrStandard(ResourceLocation id) {
        Registered r = rocket(id);
        return r != null ? r : rocket(STANDARD);
    }
}
