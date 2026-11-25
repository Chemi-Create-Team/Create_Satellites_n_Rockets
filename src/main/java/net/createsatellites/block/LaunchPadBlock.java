package net.createsatellites.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createsatellites.CreateSatellites;
import net.createsatellites.entity.RocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The pad holds the fuel and fires the rocket standing on it when it gets a redstone signal. Use a satellite on it to stand a
 * rocket up, a fuel bucket or a pipe to fill it, and an empty hand to read the tank.
 */
public class LaunchPadBlock extends Block implements EntityBlock, IWrenchable {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 4, 16);

    public LaunchPadBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    public static AABB rocketSpace(BlockPos pos) {
        return new AABB(pos).expandTowards(0, 5, 0).deflate(0.1, 0, 0.1);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(CreateSatellites.SATELLITE.get())) {
            if (!level.isClientSide) {
                if (!level.getEntitiesOfClass(RocketEntity.class, rocketSpace(pos)).isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.create_satellites_n_rockets.pad_occupied"), true);
                } else {
                    RocketEntity r = CreateSatellites.ROCKET.get().create(level);
                    r.setPos(pos.getX() + .5, pos.getY() + .25, pos.getZ() + .5);
                    if (!level.noCollision(r.getBoundingBox())) {
                        player.displayClientMessage(Component.translatable("message.create_satellites_n_rockets.pad_blocked"), true);
                    } else {
                        r.setOwner(player);
                        r.setPayload(stack);
                        level.addFreshEntity(r);
                        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1f, .8f);
                        if (!player.getAbilities().instabuild) stack.shrink(1);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection()))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            if (!level.isClientSide) {
                boolean loaded = !level.getEntitiesOfClass(RocketEntity.class, rocketSpace(pos)).isEmpty();
                player.displayClientMessage(Component.translatable("message.create_satellites_n_rockets.pad_status",
                        pad.tank.getFluidAmount(), pad.tank.getCapacity(), LaunchPadBlockEntity.FUEL_PER_LAUNCH,
                        Component.translatable(loaded ? "message.create_satellites_n_rockets.loaded" : "message.create_satellites_n_rockets.empty")), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), 2);
        if (powered && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad && !pad.tryLaunch())
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1f, 1f);
    }
}
