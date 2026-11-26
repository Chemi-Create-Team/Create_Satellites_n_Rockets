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
    public static final int MAX_WIDTH = 10, MAX_HEIGHT = 60;

    private static final Map<ResourceLocation, Registered> TYPES = new HashMap<>();
    private static final Map<Item, ResourceLocation> ITEMS = new HashMap<>();

    private RocketApi() {}

    /**
     * What a rocket can do. {@code maxPayloadKg} is the most the payloads on it may weigh together. {@code fuelMb} is burnt from the
     * pad at lift-off. The rocket climbs {@code climbBlocks} (or until the nose nears the build limit, with a minimum margin of 4 blocks from the base) with {@code acceleration} blocks
     * per tick per tick up to {@code topSpeed} blocks per tick, then releases its payloads. {@code width} and {@code height} are the
     * size of the entity in blocks; it stands on the pad, so the space above the pad must be free for it.
     */
    public record RocketType(Component name, int maxPayloadKg, int fuelMb, int climbBlocks, double acceleration, double topSpeed,
                             float width, float height) {
        public RocketType {
            Objects.requireNonNull(name);
            if (maxPayloadKg <= 0 || fuelMb < 0 || fuelMb > MAX_FUEL_MB || climbBlocks < 16 || climbBlocks > 1024
                    || !(acceleration > 0) || !(topSpeed > 0) || topSpeed > 10 || !(width > 0) || width > MAX_WIDTH || !(height > 0) || height > MAX_HEIGHT) {
                throw new IllegalArgumentException("Invalid CAE rocket type: " + name.getString());
            }
        }
    }

    /** Optional server callbacks. The entity is the CAE rocket; do not discard it or move it. */
    public interface Hooks {
        /** The pad has burnt the fuel and the rocket starts to climb. {@code payloads} are copies. */
        default void launched(ServerLevel level, Entity rocket, List<ItemStack> payloads) {}

        /** The rocket released its payloads; {@code satellites} are the ones now in orbit, in loading order. */
        default void deployed(ServerLevel level, Entity rocket, List<AerospaceApi.SatelliteInfo> satellites) {}
    }

    public record Registered(ResourceLocation id, Item item, RocketType type, Hooks hooks) {}

    /** Call during common setup (both sides: the client sizes the entity and shows the numbers), with an id in your own namespace. */
    public static synchronized void registerRocket(ResourceLocation id, Item item, RocketType type, Hooks hooks) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(item);
        Objects.requireNonNull(type);
        if (id.toString().length() > 128 || TYPES.containsKey(id) || ITEMS.containsKey(item) || AerospaceApi.payloadOf(new ItemStack(item)) != null) {
            throw new IllegalArgumentException("Invalid or duplicate CAE rocket: " + id + " / " + BuiltInRegistries.ITEM.getKey(item));
        }
        TYPES.put(id, new Registered(id, item, type, hooks == null ? new Hooks() {} : hooks));
        ITEMS.put(item, id);
    }

    public static void registerRocket(ResourceLocation id, Item item, RocketType type) {
        registerRocket(id, item, type, null);
    }

    /** The rocket type an item stands up, or null. */
    public static synchronized ResourceLocation rocketOf(ItemStack stack) {
        return stack.isEmpty() ? null : ITEMS.get(stack.getItem());
    }

    /** A registered type, or null (its mod is gone). */
    public static synchronized Registered rocket(ResourceLocation id) {
        return id == null ? null : TYPES.get(id);
    }

    /** A registered type, or CAE's own when it is unknown, so that a rocket of a removed mod still flies and drops its payloads. */
    public static Registered rocketOrStandard(ResourceLocation id) {
        Registered r = rocket(id);
        return r != null ? r : rocket(STANDARD);
    }
}
