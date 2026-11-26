package net.createaerospace.photo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.createaerospace.CreateAerospace;
import net.createaerospace.block.GroundConsoleBlockEntity;
import net.createaerospace.relay.RelayStorage;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PhotoPackets {
    private static final String KEY = "photo.create_aerospace_engineering.";
    private static final Map<MinecraftServer, Survey> SURVEYS = new WeakHashMap<>();
    private static final int ACTIVE_JOBS = 4, MAX_QUEUED = 32;
    private static final int SCAN_PER_JOB = 128, SAMPLE_PER_JOB = 8, MAX_READS_PER_JOB = 12;

    private PhotoPackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Request.TYPE, Request.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) open(player, p);
        });
        registrar.playToServer(TileRequest.TYPE, TileRequest.CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) queue(player, p);
        });
        registrar.playToClient(Photo.TYPE, Photo.CODEC, (p, context) -> net.createaerospace.client.SatellitePhotoScreen.receive(p));
        registrar.playToClient(Image.TYPE, Image.CODEC, (p, context) -> net.createaerospace.client.SatellitePhotoScreen.receive(p));
    }

    private static GroundConsoleBlockEntity console(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (level.dimension().equals(Level.OVERWORLD) && player.distanceToSqr(pos.getCenter()) <= 64 && level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof GroundConsoleBlockEntity console) return console;
        return null;
    }

    private static void open(ServerPlayer player, Request request) {
        var console = console(player, request.console);
        if (console == null) return;
        if (!player.serverLevel().getGameRules().getBoolean(CreateAerospace.SATELLITE_PHOTOS)) {
            player.displayClientMessage(Component.translatable(KEY + "disabled"), false);
            return;
        }
        var satellite = console.selected();
        if (satellite == null || !satellite.id().equals(request.satellite)
                || RelayStorage.signal(player.serverLevel(), satellite, request.console) == null) {
            player.displayClientMessage(Component.translatable(KEY + "unavailable"), false);
            return;
        }
        // the camera is the satellite owner's (or an operator's), like every other command to a satellite
        if (!satellite.controlledBy(player.getUUID(), player.hasPermissions(2))) {
            player.displayClientMessage(Component.translatable(KEY + "denied"), false);
            return;
        }
        var survey = SURVEYS.computeIfAbsent(player.server, s -> new Survey());
        int now = player.server.getTickCount();
        if (now < survey.nextOpen) {
            player.displayClientMessage(Component.translatable(KEY + "busy"), false);
            return;
        }
        survey.nextOpen = now + 20;
        var old = survey.sessions.remove(player.getUUID());
        if (old != null) survey.cancel(old);
        var path = player.server.getWorldPath(LevelResource.ROOT).resolve("region");
        var photo = new Photo(request.console, satellite.name(), UUID.randomUUID());
        var index = CompletableFuture.supplyAsync(() -> PhotoRegionIndex.read(path, request.console), SavedChunkReader.THREAD)
                .exceptionally(error -> new PhotoRegionIndex());
        survey.sessions.put(player.getUUID(), new Session(photo, index, path));
        PacketDistributor.sendToPlayer(player, photo);
    }

    private static void queue(ServerPlayer player, TileRequest request) {
        var survey = SURVEYS.get(player.server);
        var session = survey == null ? null : survey.sessions.get(player.getUUID());
        if (session == null || !session.photo.id.equals(request.id)) return;
        if (request.tile.step() == 0) {
            survey.sessions.remove(player.getUUID());
            survey.cancel(session);
            return;
        }
        if (!request.tile.valid() || survey.queued.size() >= MAX_QUEUED) return;
        if (console(player, session.photo.console) == null) return;
        if (survey.queued.stream().anyMatch(job -> job.same(session, request.tile))
                || survey.active.stream().anyMatch(job -> job.same(session, request.tile))) return;
        if (survey.queued.stream().filter(j -> j.session == session).count() >= 6) return;
        survey.queued.addLast(new Job(player.getUUID(), session, request.tile));
    }

    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var survey = SURVEYS.get(server);
        if (survey == null) return;
        survey.sessions.entrySet().removeIf(entry -> {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && console(player, entry.getValue().photo.console) != null) return false;
            survey.cancel(entry.getValue());
            return true;
        });
        while (survey.active.size() < ACTIVE_JOBS && !survey.queued.isEmpty()) survey.active.add(survey.queued.removeFirst());
        survey.active.removeIf(job -> {
            var player = server.getPlayerList().getPlayer(job.player);
            if (job.cancelled || player == null || console(player, job.session.photo.console) == null) return job.drained();
            var index = job.session.index.getNow(null);
            if (index == null) return false;
            byte[] image = job.image(player.serverLevel(), index);
            if (image == null) return false;
            PacketDistributor.sendToPlayer(player, new Image(job.session.photo.id, job.tile, image));
            return true;
        });
    }

    private static final class Survey {
        final Map<UUID, Session> sessions = new HashMap<>();
        final Deque<Job> queued = new ArrayDeque<>();
        final ArrayList<Job> active = new ArrayList<>();
        int nextOpen;

        void cancel(Session session) {
            queued.removeIf(job -> job.session == session);
            for (var job : active) if (job.session == session) job.cancelled = true;
        }
    }

    private static final class Session {
        final Photo photo;
        final CompletableFuture<PhotoRegionIndex> index;
        final java.nio.file.Path regions;

        Session(Photo photo, CompletableFuture<PhotoRegionIndex> index, java.nio.file.Path regions) {
            this.photo = photo;
            this.index = index;
            this.regions = regions;
        }
    }

    private static final class Job {
        final UUID player;
        final Session session;
        final OrbitalPhoto.Tile tile;
        OrbitalPhoto.Raster raster;
        final int firstX, firstZ, width, depth;
        final ArrayList<CompletableFuture<Void>> reads = new ArrayList<>();
        CompletableFuture<byte[]> image;
        int cursor;
        boolean cancelled;

        Job(UUID player, Session session, OrbitalPhoto.Tile tile) {
            this.player = player;
            this.session = session;
            this.tile = tile;
            int left = tile.left(session.photo.console), top = tile.top(session.photo.console);
            firstX = left >> 4;
            firstZ = top >> 4;
            width = ((left + tile.span() - 1) >> 4) - firstX + 1;
            depth = ((top + tile.span() - 1) >> 4) - firstZ + 1;
        }

        boolean same(Session other, OrbitalPhoto.Tile requested) {
            return session == other && tile.equals(requested);
        }

        boolean drained() {
            return reads.stream().allMatch(CompletableFuture::isDone) && (image == null || image.isDone());
        }

        byte[] image(ServerLevel level, PhotoRegionIndex index) {
            if (image == null) {
                if (!capture(level, index)) return null;
                image = CompletableFuture.supplyAsync(raster::pixels, Util.backgroundExecutor());
            }
            return image.getNow(null);
        }

        private boolean capture(ServerLevel level, PhotoRegionIndex index) {
            if (raster == null) raster = new OrbitalPhoto.Raster(tile.left(session.photo.console), tile.top(session.photo.console), tile.step());
            reads.removeIf(CompletableFuture::isDone);
            int sampled = 0;
            int budget = tile.step() == OrbitalPhoto.OVERVIEW_STEP ? 24 : SAMPLE_PER_JOB;
            var chunks = level.getChunkSource();
            for (int scanned = 0; cursor < width * depth && scanned < SCAN_PER_JOB && sampled < budget; scanned++) {
                int x = firstX + cursor % width, z = firstZ + cursor / width;
                var chunk = chunks.getChunkNow(x, z);
                if (chunk != null) {
                    raster.sample(chunk);
                    sampled++;
                } else if (index.has(x, z)) {
                    if (reads.size() >= MAX_READS_PER_JOB) break;
                    int min = level.getMinBuildHeight(), height = level.getHeight();
                    var regions = session.regions;
                    var target = raster;
                    // on the photo reader's own thread, not through the server's chunk IO (see SavedChunkReader)
                    var read = CompletableFuture.runAsync(() -> {
                        try {
                            var nbt = SavedChunkReader.read(regions, x, z);
                            if (nbt != null) target.sampleSaved(x, z, nbt, min, height);
                        } catch (java.io.IOException | RuntimeException e) {
                            // an unreadable chunk stays dark blue like one that was never generated
                        }
                    }, SavedChunkReader.THREAD);
                    reads.add(read);
                    sampled++;
                }
                cursor++;
            }
            return cursor == width * depth && reads.isEmpty();
        }
    }

    public record Request(BlockPos console, UUID satellite) implements CustomPacketPayload {
        private static final Type<Request> TYPE = new Type<>(CreateAerospace.id("request_photo"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (b, p) -> b.writeBlockPos(p.console).writeUUID(p.satellite),
                b -> new Request(b.readBlockPos(), b.readUUID()));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Photo(BlockPos console, String name, UUID id) implements CustomPacketPayload {
        private static final Type<Photo> TYPE = new Type<>(CreateAerospace.id("orbital_photo"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Photo> CODEC = StreamCodec.of(
                (b, p) -> b.writeBlockPos(p.console).writeUtf(p.name, 64).writeUUID(p.id),
                b -> new Photo(b.readBlockPos(), b.readUtf(64), b.readUUID()));
        @Override public Type<Photo> type() { return TYPE; }
    }

    private static void writeTile(RegistryFriendlyByteBuf b, OrbitalPhoto.Tile tile) {
        b.writeByte(tile.step()).writeByte(tile.x()).writeByte(tile.z());
    }

    private static OrbitalPhoto.Tile readTile(RegistryFriendlyByteBuf b) {
        return new OrbitalPhoto.Tile(b.readUnsignedByte(), b.readUnsignedByte(), b.readUnsignedByte());
    }

    public record TileRequest(UUID id, OrbitalPhoto.Tile tile) implements CustomPacketPayload {
        private static final Type<TileRequest> TYPE = new Type<>(CreateAerospace.id("request_photo_tile"));
        private static final StreamCodec<RegistryFriendlyByteBuf, TileRequest> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.id);
            writeTile(b, p.tile);
        }, b -> new TileRequest(b.readUUID(), readTile(b)));
        @Override public Type<TileRequest> type() { return TYPE; }
    }

    public record Image(UUID id, OrbitalPhoto.Tile tile, byte[] pixels) implements CustomPacketPayload {
        private static final Type<Image> TYPE = new Type<>(CreateAerospace.id("orbital_photo_tile"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Image> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.id);
            writeTile(b, p.tile);
            b.writeByteArray(p.pixels);
        }, b -> new Image(b.readUUID(), readTile(b), b.readByteArray(OrbitalPhoto.BYTES)));
        @Override public Type<Image> type() { return TYPE; }
    }
}
