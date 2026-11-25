package net.createaerospace.orbit;

import java.util.ArrayList;
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

    public static SatelliteData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SatelliteData::new, SatelliteData::load, null), CreateAerospace.MOD_ID + "_satellites");
    }

    public List<Satellite> list() {
        return new ArrayList<>(all.values());
    }

    public Satellite find(UUID id) {
        return all.get(id);
    }

    public void put(MinecraftServer server, Satellite s) {
        all.put(s.id(), s);
        changed(server);
    }

    public void remove(MinecraftServer server, UUID id) {
        if (all.remove(id) != null) changed(server);
    }

    private void changed(MinecraftServer server) {
        setDirty();
        PacketDistributor.sendToAllPlayers(new SatellitesPayload(list()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        all.values().forEach(s -> l.add(s.save()));
        tag.put("satellites", l);
        return tag;
    }

    public static SatelliteData load(CompoundTag tag, HolderLookup.Provider registries) {
        SatelliteData d = new SatelliteData();
        for (Tag t : tag.getList("satellites", Tag.TAG_COMPOUND)) {
            Satellite s = Satellite.load((CompoundTag) t);
            d.all.put(s.id(), s);
        }
        return d;
    }
}
