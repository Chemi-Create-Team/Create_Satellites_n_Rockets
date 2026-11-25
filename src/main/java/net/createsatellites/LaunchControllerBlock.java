package net.createsatellites;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
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

/** Lifts every block connected to it off the ground as one Sable contraption and lights its engines. */
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
            var r = assemble(sl, pos, true);
            player.displayClientMessage(Component.translatable(r == null ? "message." + CreateSatellites.MOD_ID + ".launch.failed"
                    : "message." + CreateSatellites.MOD_ID + ".launch.ok"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** @return the new sub-level, or null when the connected structure is too big or empty */
    public static dev.ryanhcode.sable.sublevel.ServerSubLevel assemble(ServerLevel level, BlockPos from, boolean ignite) {
        var g = SubLevelAssemblyHelper.gatherConnectedBlocks(from, level, MAX_BLOCKS,
                (fp, fs, tp, ts, d) -> !ts.is(STAYS_BEHIND));
        if (g.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            CreateSatellites.LOGGER.info("Launch assembly at {} refused: {} after {} blocks", from, g.assemblyState(), g.checkedBlocks());
            return null;
        }
        var sub = SubLevelAssemblyHelper.assembleBlocks(level, from, g.blocks(), g.boundingBox());
        if (ignite) {
            CompoundTag t = sub.getUserDataTag() == null ? new CompoundTag() : sub.getUserDataTag();
            t.putBoolean(IGNITED, true);
            sub.setUserDataTag(t);
        }
        return sub;
    }
}
