package net.createsatellites;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Reaction wheels and thrusters that hold a station still. Only works where there is no gravity. */
public class StationCoreBlock extends Block implements EntityBlock {
    public StationCoreBlock(Properties p) {
        super(p);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationCoreBlockEntity(pos, state);
    }
}
