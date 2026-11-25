package net.createsatellites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Gives a redstone signal for as long as the contraption it is part of floats in orbit. */
public class SatelliteCoreBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SatelliteCoreBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWERED);
    }

    @Override
    protected boolean isSignalSource(BlockState s) {
        return true;
    }

    @Override
    protected int getSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) {
        return s.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        if (!l.isClientSide) l.scheduleTick(p, this, 20);
    }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        boolean inOrbit = l.dimension().equals(Orbit.KEY);
        if (inOrbit != s.getValue(POWERED)) l.setBlockAndUpdate(p, s.setValue(POWERED, inOrbit));
        l.scheduleTick(p, this, 20);
    }
}
