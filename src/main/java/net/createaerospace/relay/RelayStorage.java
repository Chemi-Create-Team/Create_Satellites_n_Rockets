package net.createaerospace.relay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.items.ItemStackHandler;

/** One server-side item inventory per communications satellite. Each ground station uses the same slots. */
public final class RelayStorage extends SavedData {
    public static final int SLOTS = 27;
    public static final String LEGACY_PAYLOAD = "create_frontier_science:me_relay_satellite";
    private final Map<UUID, Channel> channels = new LinkedHashMap<>();

    public static boolean isRelay(Satellite satellite) {
        return isRelay(satellite.payload());
    }

    public static boolean isRelay(String payload) {
        return CreateAerospace.COMMUNICATION_PAYLOAD.toString().equals(payload) || LEGACY_PAYLOAD.equals(payload);
    }

    public static boolean retired(Satellite satellite) {
        return satellite == null || satellite.abandoned() || satellite.reentryPlanned();
    }

    /** A relay transmits only while active and above this station's horizon on the CAE map. */
    public static OrbitMath.Look signal(ServerLevel level, Satellite satellite, BlockPos pos) {
        if (!level.dimension().equals(Level.OVERWORLD) || retired(satellite) || !isRelay(satellite) || satellite.mode() == Satellite.STANDBY
                || Math.abs(pos.getX() + .5) >= 180_000 || Math.abs(pos.getZ() + .5) >= 89_000) return null;
        var look = OrbitMath.lookFromBlock(satellite, level.getGameTime(), pos.getX() + .5, pos.getZ() + .5);
        return look.elevation() > 0 ? look : null;
    }

    public static RelayStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RelayStorage::new, RelayStorage::load, null), CreateAerospace.MOD_ID + "_relay_items");
    }

    public record Channel(UUID id, UUID owner, String name, ItemStackHandler items) {
        public boolean hasItems() {
            for (int slot = 0; slot < items.getSlots(); slot++) if (!items.getStackInSlot(slot).isEmpty()) return true;
            return false;
        }
    }

    public Channel find(UUID id) {
        return channels.get(id);
    }

    public List<Channel> retired(SatelliteData satellites, UUID owner, boolean operator) {
        return channels.values().stream().filter(c -> (operator || c.owner.equals(owner))
                && retired(satellites.find(c.id)) && c.hasItems()).toList();
    }

    public Channel activate(Satellite satellite) {
        return channels.computeIfAbsent(satellite.id(), id -> newChannel(id, satellite.owner(), satellite.name()));
    }

    private Channel newChannel(UUID id, UUID owner, String name) {
        return new Channel(id, owner, name, new ItemStackHandler(SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setDirty();
            }
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (Channel channel : channels.values()) {
            if (!channel.hasItems()) continue;
            var entry = new CompoundTag();
            entry.putUUID("id", channel.id);
            entry.putUUID("owner", channel.owner);
            entry.putString("name", channel.name);
            entry.put("items", channel.items.serializeNBT(registries));
            entries.add(entry);
        }
        tag.put("channels", entries);
        return tag;
    }

    public static RelayStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        var storage = new RelayStorage();
        for (var raw : tag.getList("channels", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("id") || !entry.hasUUID("owner")) continue;
            var channel = storage.newChannel(entry.getUUID("id"), entry.getUUID("owner"), entry.getString("name"));
            channel.items.deserializeNBT(registries, entry.getCompound("items"));
            if (channel.hasItems()) storage.channels.put(channel.id, channel);
        }
        return storage;
    }
}
