package net.createsatellites.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createsatellites.CreateSatellites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The ground control console. Like the reactor control console of Create: Refinery 'n' Reactors, the controls are painted on the
 * top and found from where the operator clicks it, so everything is read in the operator's frame: u across from their left,
 * v from their near edge. FACING points at the operator.
 */
public class GroundConsoleBlock extends Block implements EntityBlock, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final double TOP = 12 / 16.0;
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 12, 16);

    public GroundConsoleBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GroundConsoleBlockEntity(pos, state);
    }

    /** Which control a hit on the console is on, or null. {u, v} is in the operator's frame. */
    public static ConsoleButton buttonAt(BlockState state, BlockPos pos, net.minecraft.world.phys.Vec3 hit) {
        double[] uv = uv(state.getValue(FACING), hit.x - pos.getX() - .5, hit.z - pos.getZ() - .5);
        return ConsoleButton.at(uv[0], uv[1]);
    }

    public static double[] uv(Direction facing, double dx, double dz) {
        Direction right = facing.getCounterClockWise();
        return new double[] {.5 + dx * right.getStepX() + dz * right.getStepZ(), .5 - dx * facing.getStepX() - dz * facing.getStepZ()};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        ConsoleButton button = buttonAt(state, pos, hit.getLocation());
        if (button == null || !(level.getBlockEntity(pos) instanceof GroundConsoleBlockEntity console)) return InteractionResult.PASS;
        double[] uv = uv(state.getValue(FACING), hit.getLocation().x - pos.getX() - .5, hit.getLocation().z - pos.getZ() - .5);
        if (level.isClientSide) {
            if (button == ConsoleButton.MAP) net.createsatellites.client.ClientHooks.openMap(pos);
        } else {
            console.press(player, button, uv[0] >= button.u);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
