package net.createsatellites;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Lifts every block connected to it off the ground as one Sable contraption and lights its engines; on a flying ship it switches the ignition. */
public class LaunchControllerBlock extends Block {
    public static final String IGNITED = "csr_ignited";
    public static final int MAX_BLOCKS = 4096;
    /** Ground, stone and the launch pad stay behind. */
    public static final TagKey<Block> STAYS_BEHIND = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, CreateSatellites.id("stays_behind"));

    public LaunchControllerBlock(Properties p) {
        super(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl) {
            String key;
            if (Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel flying) {
                // already a ship: the controller is the ignition switch
                boolean on = !(flying.getUserDataTag() != null && flying.getUserDataTag().getBoolean(IGNITED));
                setIgnited(flying, on);
                key = on ? "ignite" : "cutoff";
            } else {
                key = assemble(sl, pos, true) == null ? "failed" : "ok";
            }
            player.displayClientMessage(Component.translatable("message." + CreateSatellites.MOD_ID + ".launch." + key), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** @return the new sub-level, or null when the connected structure is too big or empty */
    public static ServerSubLevel assemble(ServerLevel level, BlockPos from, boolean ignite) {
        var g = SubLevelAssemblyHelper.gatherConnectedBlocks(from, level, MAX_BLOCKS,
                (fp, fs, tp, ts, d) -> !ts.is(STAYS_BEHIND));
        if (g.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            CreateSatellites.LOGGER.info("Launch assembly at {} refused: {} after {} blocks", from, g.assemblyState(), g.checkedBlocks());
            return null;
        }
        var sub = SubLevelAssemblyHelper.assembleBlocks(level, from, g.blocks(), g.boundingBox());
        if (ignite) setIgnited(sub, true);
        return sub;
    }

    public static void setIgnited(ServerSubLevel sub, boolean on) {
        CompoundTag t = sub.getUserDataTag() == null ? new CompoundTag() : sub.getUserDataTag();
        t.putBoolean(IGNITED, on);
        sub.setUserDataTag(t);
    }
}
