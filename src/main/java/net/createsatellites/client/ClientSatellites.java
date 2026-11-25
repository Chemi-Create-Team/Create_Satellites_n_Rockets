package net.createsatellites.client;

import java.util.List;
import net.createsatellites.orbit.Satellite;

/** The satellites as last sent by the server. */
public final class ClientSatellites {
    private static volatile List<Satellite> all = List.of();

    private ClientSatellites() {}

    public static List<Satellite> get() {
        return all;
    }

    public static void set(List<Satellite> list) {
        all = List.copyOf(list);
    }
}
