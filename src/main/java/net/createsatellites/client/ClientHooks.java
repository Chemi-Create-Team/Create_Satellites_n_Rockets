package net.createsatellites.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Calls from common code that only make sense on the client. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openMap(BlockPos console) {
        Minecraft.getInstance().setScreen(new SatelliteMapScreen(console));
    }
}
