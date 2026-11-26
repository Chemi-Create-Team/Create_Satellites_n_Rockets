package net.createaerospace.block;

import net.createaerospace.CreateAerospace;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The service gantry: put parts in with a click (or a funnel), read it with an empty hand. See {@link ServiceGantryBlockEntity}. */
public class ServiceGantryBlock extends Block implements EntityBlock {
    public ServiceGantryBlock(Properties p) {
        super(p);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServiceGantryBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CreateAerospace.SERVICE_GANTRY_BE.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<ServiceGantryBlockEntity>) ServiceGantryBlockEntity::tick : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ServiceGantryBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        for (int slot = 0; slot < 2; slot++) {
            if (!be.parts.isItemValid(slot, stack)) continue;
            if (!level.isClientSide) {
                ItemStack left = be.parts.insertItem(slot, stack.copy(), false);
                if (!player.getAbilities().instabuild) stack.setCount(left.getCount());
                status(be, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ServiceGantryBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide) status(be, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void status(ServiceGantryBlockEntity be, Player player) {
        var r = be.target();
        Component target = r == null ? Component.translatable("message.create_aerospace_engineering.gantry_idle")
                : Component.translatable("message.create_aerospace_engineering.gantry_target", r.type().type().name(),
                        be.progress() * 100 / ServiceGantryBlockEntity.SERVICE_TICKS);
        player.displayClientMessage(Component.translatable("message.create_aerospace_engineering.gantry_status",
                be.parts.getStackInSlot(0).getCount(), ServiceGantryBlockEntity.SHEETS,
                be.parts.getStackInSlot(1).getCount(), ServiceGantryBlockEntity.MECHANISMS, target), true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ServiceGantryBlockEntity be) {
            for (int i = 0; i < 2; i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.parts.getStackInSlot(i).copy());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
