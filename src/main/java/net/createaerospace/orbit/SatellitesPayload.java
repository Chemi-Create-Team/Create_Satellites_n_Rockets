package net.createaerospace.orbit;

import java.util.List;
import net.createaerospace.CreateAerospace;
import net.createaerospace.client.ClientSatellites;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The complete satellite list and the latest warnings, sent on login and whenever they change. */
public record SatellitesPayload(List<Satellite> satellites, List<Alert> alerts) implements CustomPacketPayload {
    public static final Type<SatellitesPayload> TYPE = new Type<>(CreateAerospace.id("satellites"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SatellitesPayload> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.satellites.size());
                p.satellites.forEach(s -> s.write(b));
                b.writeVarInt(p.alerts.size());
                p.alerts.forEach(a -> a.write(b));
            },
            b -> {
                int n = b.readVarInt();
                List<Satellite> l = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) l.add(Satellite.read(b));
                int m = b.readVarInt();
                List<Alert> a = new java.util.ArrayList<>();
                for (int i = 0; i < m; i++) a.add(Alert.read(b));
                return new SatellitesPayload(l, a);
            });

    @Override
    public Type<SatellitesPayload> type() {
        return TYPE;
    }

    public static void handle(SatellitesPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSatellites.set(p.satellites, p.alerts));
    }
}
