package net.createaerospace.compat.cfs;

import net.createaerospace.CreateAerospace;
import net.neoforged.fml.ModList;

/**
 * Create: Frontier Science is optional. This class never names a CFS type; {@link CfsComputerApp}, the only class that does, is
 * touched only after CFS and its app API are known to be there.
 */
public final class CfsCompat {
    public static final String CFS = "create_frontier_science";

    private CfsCompat() {}

    public static void register() {
        if (!ModList.get().isLoaded(CFS)) return;
        try {
            // older CFS builds (0.22.x) have no app API, though their version number sorts above 0.3.17
            Class.forName("net.createfrontier.api.computer.ComputerApps", false, CfsCompat.class.getClassLoader());
            CfsComputerApp.register();
        } catch (ClassNotFoundException | LinkageError e) {
            CreateAerospace.LOGGER.warn("Create: Frontier Science has no Control Computer app API, the satellite app is not added", e);
        }
    }
}
