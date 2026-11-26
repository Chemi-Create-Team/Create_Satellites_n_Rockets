package net.createaerospace.orbit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Every satellite of the world, kept on the overworld. Positions are never stored: they follow from the orbit and the game time. */
public class SatelliteData extends SavedData {
    private final Map<UUID, Satellite> all = new LinkedHashMap<>();
    /** What the mod of a payload keeps on its satellite (see {@code api.AerospaceApi}). Server only, never sent to clients. */
    private final Map<UUID, CompoundTag> payloadData = new HashMap<>();
    /** The latest warnings, oldest first. */
    private final List<Alert> alerts = new ArrayList<>();
    /** The last tick {@link #tick} looked at; not saved, so after a restart it starts from the next check. */
    private long checkedUpTo = Long.MIN_VALUE;

    public static SatelliteData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SatelliteData::new, SatelliteData::load, null), CreateAerospace.MOD_ID + "_satellites");
    }

    public List<Satellite> list() {
        return new ArrayList<>(all.values());
    }

    public List<Alert> alerts() {
        return List.copyOf(alerts);
    }

    public void alert(MinecraftServer server, Alert a) {
        alerts.add(a);
        while (alerts.size() > Alert.KEPT) alerts.remove(0);
        changed(server);
    }

    /**
     * Every second: a reentry that has begun (a commanded one, or the orbit of an abandoned satellite giving out) raises a warning, and
     * a satellite that has burnt up leaves the list.
     */
    public void tick(MinecraftServer server, long now) {
        long from = checkedUpTo == Long.MIN_VALUE ? now - 1 : checkedUpTo;
        checkedUpTo = now;
        for (Satellite s : list()) {
            if (!s.reentryPlanned()) continue;
            if (s.reentryStart() > from && s.reentryStart() <= now)
                alert(server, new Alert(Alert.REENTRY, s.name(), s.ownerName(), 0, s.targetX(), s.targetZ(), s.reentryStart()));
            if (now >= s.reentryEnd()) remove(server, s.id());
        }
    }

    public Satellite find(UUID id) {
        return all.get(id);
    }

    public void put(MinecraftServer server, Satellite s) {
        all.put(s.id(), s);
        changed(server);
    }

    /** The payload data of a satellite, a copy; empty when there is none. */
    public CompoundTag data(UUID id) {
        CompoundTag t = payloadData.get(id);
        return t == null ? new CompoundTag() : t.copy();
    }

    public void setData(UUID id, CompoundTag data) {
        if (!all.containsKey(id)) return;
        if (data.isEmpty()) payloadData.remove(id);
        else payloadData.put(id, data.copy());
        setDirty();
    }

    /** Takes a satellite out of orbit. The mod of its payload hears about it first, while its data is still there. */
    public void remove(MinecraftServer server, UUID id) {
        Satellite s = all.get(id);
        if (s == null) return;
        net.createaerospace.api.AerospaceApi.removing(server, s);
        all.remove(id);
        payloadData.remove(id);
        changed(server);
    }

    private void changed(MinecraftServer server) {
        setDirty();
        PacketDistributor.sendToAllPlayers(new SatellitesPayload(list(), alerts()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        all.values().forEach(s -> {
            CompoundTag t = s.save();
            if (payloadData.containsKey(s.id())) t.put("data", payloadData.get(s.id()));
            l.add(t);
        });
        tag.put("satellites", l);
        ListTag a = new ListTag();
        alerts.forEach(x -> a.add(x.save()));
        tag.put("alerts", a);
        return tag;
    }

    public static SatelliteData load(CompoundTag tag, HolderLookup.Provider registries) {
        SatelliteData d = new SatelliteData();
        for (Tag t : tag.getList("satellites", Tag.TAG_COMPOUND)) {
            Satellite s = Satellite.load((CompoundTag) t);
            d.all.put(s.id(), s);
            if (((CompoundTag) t).contains("data", Tag.TAG_COMPOUND)) d.payloadData.put(s.id(), ((CompoundTag) t).getCompound("data"));
        }
        for (Tag t : tag.getList("alerts", Tag.TAG_COMPOUND)) d.alerts.add(Alert.load((CompoundTag) t));
        return d;
    }
}
