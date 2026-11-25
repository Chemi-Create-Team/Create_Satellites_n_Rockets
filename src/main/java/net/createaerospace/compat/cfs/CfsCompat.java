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
            // older CFS builds (0.22.x, and 0.3.17 to 0.3.25) have no canvas API, though 0.22.x sorts above 0.3.26
            Class.forName("net.createfrontier.api.computer.ComputerCanvas", false, CfsCompat.class.getClassLoader());
            CfsComputerApp.register();
        } catch (ClassNotFoundException | LinkageError e) {
            CreateAerospace.LOGGER.warn("Create: Frontier Science has no Control Computer canvas API, the satellite app is not added", e);
        }
    }

    /** Client setup: the drawing of the app. Only called on the client. */
    public static void registerClient() {
        if (!ModList.get().isLoaded(CFS)) return;
        try {
            Class.forName("net.createfrontier.api.computer.client.ComputerCanvasRenderers", false, CfsCompat.class.getClassLoader());
            net.createaerospace.client.CfsCanvasRenderer.register();
        } catch (ClassNotFoundException | LinkageError e) {
            CreateAerospace.LOGGER.warn("Create: Frontier Science has no Control Computer canvas renderer API, the satellite app is not drawn", e);
        }
    }
}
