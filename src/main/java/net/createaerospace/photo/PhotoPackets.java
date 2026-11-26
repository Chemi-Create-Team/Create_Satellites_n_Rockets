package net.createaerospace.photo;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.relay.RelayStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PhotoPackets {
    private static final String KEY = "photo.create_aerospace_engineering.";
    private static final Map<MinecraftServer, Long> NEXT_CAPTURE = new WeakHashMap<>();

    private PhotoPackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Request.TYPE, Request.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) takePhoto(player, p.console, p.satellite);
        });
        registrar.playToClient(Photo.TYPE, Photo.CODEC, (p, context) -> net.createaerospace.client.SatellitePhotoScreen.receive(p));
    }

    private static void takePhoto(ServerPlayer player, BlockPos pos, UUID id) {
        var level = player.serverLevel();
        if (player.distanceToSqr(pos.getCenter()) > 64 || !level.isLoaded(pos)
                || !(level.getBlockEntity(pos) instanceof GroundConsoleBlockEntity console)) return;
        var satellite = console.selected();
        if (satellite == null || !satellite.id().equals(id) || RelayStorage.signal(level, satellite, pos) == null) {
            player.displayClientMessage(Component.translatable(KEY + "unavailable"), false);
            return;
        }
        long now = level.getGameTime();
        if (now < NEXT_CAPTURE.getOrDefault(player.server, Long.MIN_VALUE)) {
            player.displayClientMessage(Component.translatable(KEY + "busy"), false);
            return;
        }
        NEXT_CAPTURE.put(player.server, now + 20);
        PacketDistributor.sendToPlayer(player, new Photo(pos, satellite.name(), OrbitalPhoto.capture(level, pos)));
    }

    public record Request(BlockPos console, UUID satellite) implements CustomPacketPayload {
        private static final Type<Request> TYPE = new Type<>(CreateAerospace.id("request_photo"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (b, p) -> b.writeBlockPos(p.console).writeUUID(p.satellite),
                b -> new Request(b.readBlockPos(), b.readUUID()));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Photo(BlockPos console, String name, byte[] pixels) implements CustomPacketPayload {
        private static final Type<Photo> TYPE = new Type<>(CreateAerospace.id("orbital_photo"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Photo> CODEC = StreamCodec.of(
                (b, p) -> b.writeBlockPos(p.console).writeUtf(p.name, 64).writeByteArray(p.pixels),
                b -> new Photo(b.readBlockPos(), b.readUtf(64), b.readByteArray(OrbitalPhoto.BYTES)));
        @Override public Type<Photo> type() { return TYPE; }
    }
}
