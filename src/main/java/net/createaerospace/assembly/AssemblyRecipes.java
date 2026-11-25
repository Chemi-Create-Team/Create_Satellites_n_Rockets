package net.createaerospace.assembly;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.createaerospace.CreateAerospace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Loads data/&lt;namespace&gt;/assembly/*.json. Not under recipe/, where vanilla would try to parse the files as crafting recipes. */
public class AssemblyRecipes extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation, AssemblyRecipe> all = Map.of();

    public AssemblyRecipes() {
        super(new Gson(), "assembly");
    }

    public static AssemblyRecipe get(ResourceLocation id) {
        return all.get(id);
    }

    /** All recipes in a fixed order, so cycling with a wrench is the same for everybody. */
    public static List<AssemblyRecipe> list() {
        return new ArrayList<>(all.values());
    }

    public static Collection<ResourceLocation> ids() {
        return all.keySet();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, AssemblyRecipe> loaded = new TreeMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, AssemblyRecipe.fromJson(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                CreateAerospace.LOGGER.error("Skipping assembly recipe {}: {}", id, e.getMessage());
            }
        });
        all = loaded;
    }
}
