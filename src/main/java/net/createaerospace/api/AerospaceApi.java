package net.createaerospace.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Satellites of other mods on CAE rockets. Another mod registers an item as a payload; a player uses that item on a launch pad, the
 * CAE rocket carries it to orbit, and the mod gets a satellite it can read and keep data on (a GPS, a relay for a channel network...).
 * <p>
 * All of it runs on the server thread. Signatures use only Minecraft and Java types, so another mod compiles against the API jar
 * ({@code dist/create-aerospace-engineering-api-*.jar}) and keeps CAE an optional dependency: only call this class after checking
 * {@code ModList.get().isLoaded("create_aerospace_engineering")}. See {@code docs/api.md}.
 */
public final class AerospaceApi {
    public static final int API_VERSION = 1;
    /** The largest payload data kept on one satellite, in bytes of NBT. */
    public static final int MAX_DATA_BYTES = 16_384;
    /** The payload type of a plain CAE satellite. */
    public static final ResourceLocation STANDARD = ResourceLocation.fromNamespaceAndPath("create_aerospace_engineering", "satellite");

    private static final Map<ResourceLocation, Payload> PAYLOADS = new HashMap<>();
    private static final Map<Item, ResourceLocation> ITEMS = new HashMap<>();

    private AerospaceApi() {}

    /** What a payload does. Every method has a default, so an empty implementation is a satellite that only has a name. */
    public interface Payload {
        /** Shown on the ground console and the map, e.g. "GPS". Translated on each client, so use a translatable component. */
        Component name();

        /**
         * The rocket has reached orbit; returns the first data of the new satellite. {@code stack} is a copy of the item that was
         * loaded on the pad, with all its components. {@code level} is where the pad is.
         */
        default CompoundTag deploy(ServerLevel level, ItemStack stack, SatelliteInfo satellite) {
            return new CompoundTag();
        }

        /** The satellite is leaving orbit (deorbited from a console or removed by a command). Its data is still readable. */
        default void removed(MinecraftServer server, SatelliteInfo satellite) {}
    }

    /**
     * A satellite as it is now. Positions are computed at the overworld game time of the call and change every tick; ask again
     * instead of keeping them. {@code payload} is {@link #STANDARD} for a plain CAE satellite. {@code mode} is 0 standby, 1 beacon,
     * 2 flash. {@code data} is a copy.
     */
    public record SatelliteInfo(UUID id, String name, UUID owner, String ownerName, ResourceLocation payload, CompoundTag data,
                                int altitudeKm, int inclinationDeg, int periodTicks, int mode, double latitudeDeg, double longitudeDeg,
                                int blockX, int blockZ) {}

    /** Call during common setup (both sides, so clients can name the payload), with an id in your own namespace. */
    public static synchronized void registerPayload(ResourceLocation id, Item item, Payload payload) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(item);
        Objects.requireNonNull(payload);
        if (id.equals(STANDARD) || id.toString().length() > 128 || PAYLOADS.containsKey(id) || ITEMS.containsKey(item)
                || item == CreateAerospace.SATELLITE.get()) {
            throw new IllegalArgumentException("Invalid or duplicate CAE payload: " + id + " / " + BuiltInRegistries.ITEM.getKey(item));
        }
        PAYLOADS.put(id, payload);
        ITEMS.put(item, id);
    }

    /** The payload type an item launches as, or null when it is not a payload. CAE's own satellite item gives {@link #STANDARD}. */
    public static synchronized ResourceLocation payloadOf(ItemStack stack) {
        if (stack.is(CreateAerospace.SATELLITE.get())) return STANDARD;
        return ITEMS.get(stack.getItem());
    }

    public static synchronized Payload payload(ResourceLocation id) {
        return PAYLOADS.get(id);
    }

    /** The name of a payload type for a display; the plain satellite and an unknown type (its mod was removed) have none. */
    public static Optional<Component> payloadName(ResourceLocation id) {
        Payload p = id == null ? null : payload(id);
        return p == null ? Optional.empty() : Optional.of(p.name());
    }

    /** Every satellite in orbit, in the order they were launched. */
    public static List<SatelliteInfo> satellites(MinecraftServer server) {
        SatelliteData data = SatelliteData.get(server);
        long now = server.overworld().getGameTime();
        return data.list().stream().map(s -> info(s, data, now)).toList();
    }

    /** Only the satellites of one payload type. */
    public static List<SatelliteInfo> satellites(MinecraftServer server, ResourceLocation payload) {
        return satellites(server).stream().filter(s -> s.payload().equals(payload)).toList();
    }

    public static Optional<SatelliteInfo> satellite(MinecraftServer server, UUID id) {
        SatelliteData data = SatelliteData.get(server);
        Satellite s = data.find(id);
        return s == null ? Optional.empty() : Optional.of(info(s, data, server.overworld().getGameTime()));
    }

    /** Replaces the payload data of a satellite. False when there is no such satellite or the data is over {@link #MAX_DATA_BYTES}. */
    public static boolean setData(MinecraftServer server, UUID id, CompoundTag data) {
        SatelliteData d = SatelliteData.get(server);
        if (d.find(id) == null || data.sizeInBytes() > MAX_DATA_BYTES) return false;
        d.setData(id, data);
        return true;
    }

    /**
     * How high the satellite stands over the horizon of a block, in degrees, at the current game time; below 0 it is behind the
     * Earth. A GPS needs 4 satellites above 0, a relay 1. Empty when there is no such satellite. One degree is 1000 blocks.
     */
    public static Optional<Double> elevation(MinecraftServer server, UUID id, BlockPos from) {
        Satellite s = SatelliteData.get(server).find(id);
        if (s == null) return Optional.empty();
        var look = OrbitMath.lookFromBlock(s, server.overworld().getGameTime(), from.getX() + .5, from.getZ() + .5);
        return Optional.of(Math.toDegrees(look.elevation()));
    }

    /** The satellites above the horizon of a block, highest first. */
    public static List<SatelliteInfo> visibleFrom(MinecraftServer server, BlockPos from) {
        return satellites(server).stream().filter(s -> elevation(server, s.id(), from).orElse(-90.0) > 0)
                .sorted((a, b) -> Double.compare(elevation(server, b.id(), from).orElse(-90.0), elevation(server, a.id(), from).orElse(-90.0)))
                .toList();
    }

    /** The payload type of a satellite as an id; a plain one, or one launched before its type existed, is {@link #STANDARD}. */
    public static ResourceLocation typeOf(Satellite s) {
        ResourceLocation id = s.payload().isEmpty() ? null : ResourceLocation.tryParse(s.payload());
        return id == null ? STANDARD : id;
    }

    static SatelliteInfo info(Satellite s, SatelliteData data, long now) {
        double[] p = OrbitMath.subpoint(s, now);
        int[] b = OrbitMath.groundBlock(p);
        return new SatelliteInfo(s.id(), s.name(), s.owner(), s.ownerName(), typeOf(s), data.data(s.id()), s.altKm(),
                OrbitMath.INCLINATION_DEG[s.incl()], s.periodTicks(), s.mode(), Math.toDegrees(p[0]), Math.toDegrees(p[1]), b[0], b[1]);
    }

    /** CAE only: a satellite is leaving orbit; tells the mod of its payload. A failing mod does not keep the satellite up. */
    public static void removing(MinecraftServer server, Satellite s) {
        Payload p = payload(typeOf(s));
        if (p == null) return;
        try {
            p.removed(server, info(s, SatelliteData.get(server), server.overworld().getGameTime()));
        } catch (RuntimeException e) {
            CreateAerospace.LOGGER.error("Payload {} failed while satellite {} left orbit", s.payload(), s.name(), e);
        }
    }

    /** CAE only: a rocket reached orbit with this stack; puts the satellite up and asks the payload for its first data. */
    public static Satellite deploy(ServerLevel level, ItemStack stack, Satellite s) {
        MinecraftServer server = level.getServer();
        SatelliteData data = SatelliteData.get(server);
        data.put(server, s);
        Payload p = payload(typeOf(s));
        if (p != null) {
            try {
                CompoundTag first = p.deploy(level, stack.copy(), info(s, data, server.overworld().getGameTime()));
                if (first != null && first.sizeInBytes() <= MAX_DATA_BYTES) data.setData(s.id(), first);
            } catch (RuntimeException e) {
                CreateAerospace.LOGGER.error("Payload {} failed to deploy satellite {}", s.payload(), s.name(), e);
            }
        }
        return s;
    }
}
