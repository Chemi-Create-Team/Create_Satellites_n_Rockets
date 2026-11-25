package net.createsatellites.orbit;

import java.util.List;
import net.createsatellites.CreateSatellites;
import net.createsatellites.client.ClientSatellites;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The complete satellite list, sent on login and whenever it changes. */
public record SatellitesPayload(List<Satellite> satellites) implements CustomPacketPayload {
    public static final Type<SatellitesPayload> TYPE = new Type<>(CreateSatellites.id("satellites"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SatellitesPayload> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.satellites.size());
                p.satellites.forEach(s -> s.write(b));
            },
            b -> {
                int n = b.readVarInt();
                List<Satellite> l = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) l.add(Satellite.read(b));
                return new SatellitesPayload(l);
            });

    @Override
    public Type<SatellitesPayload> type() {
        return TYPE;
    }

    public static void handle(SatellitesPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSatellites.set(p.satellites));
    }
}
