package net.createaerospace.block;

import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.orbit.SatelliteData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Selecting a satellite on the console's map screen, by clicking its dot or its line of the list: the same as stepping to it with the
 * console's arrow buttons, so anybody at the console may do it. With a hundred satellites the arrows alone would take a hundred clicks.
 */
public record ConsoleSelect(BlockPos console, UUID satellite) implements CustomPacketPayload {
    public static final Type<ConsoleSelect> TYPE = new Type<>(CreateAerospace.id("console_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleSelect> CODEC = StreamCodec.of(
            (b, p) -> b.writeBlockPos(p.console).writeUUID(p.satellite),
            b -> new ConsoleSelect(b.readBlockPos(), b.readUUID()));

    @Override
    public Type<ConsoleSelect> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) handle(player, p);
        });
    }

    static void handle(ServerPlayer player, ConsoleSelect p) {
        select(player.serverLevel(), player.position(), p);
    }

    /** Only at a loaded console within 8 blocks of the player at {@code from}, as for a photo, and only a satellite that exists. */
    public static boolean select(ServerLevel level, Vec3 from, ConsoleSelect p) {
        if (!level.dimension().equals(Level.OVERWORLD) || from.distanceToSqr(p.console.getCenter()) > 64 || !level.isLoaded(p.console)
                || !(level.getBlockEntity(p.console) instanceof GroundConsoleBlockEntity console)) return false;
        if (SatelliteData.get(level.getServer()).list().stream().noneMatch(s -> s.id().equals(p.satellite))) return false;
        console.select(p.satellite);
        return true;
    }
}
