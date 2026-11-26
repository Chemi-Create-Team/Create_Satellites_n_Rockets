package net.createaerospace.block;

import net.createaerospace.CreateAerospace;
import net.createaerospace.entity.RocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Services a landed first stage standing on a launch pad near it: with the parts in it (put in by hand, a funnel or a hopper), it
 * works for {@link #SERVICE_TICKS} and then uses them up and clears the stage for flight. Parts cannot be taken out again.
 */
public class ServiceGantryBlockEntity extends BlockEntity {
    /** Iron sheets and precision mechanisms per service: the engines, the heat shield, a new second stage. */
    public static final int SHEETS = 8, MECHANISMS = 2, SERVICE_TICKS = 2400, RANGE = 8;

    public final ItemStackHandler parts = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(slot == 0 ? com.simibubi.create.AllItems.IRON_SHEET.get() : com.simibubi.create.AllItems.PRECISION_MECHANISM.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? SHEETS : MECHANISMS;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int progress;

    public ServiceGantryBlockEntity(BlockPos pos, BlockState state) {
        super(CreateAerospace.SERVICE_GANTRY_BE.get(), pos, state);
    }

    public int progress() {
        return progress;
    }

    public boolean stocked() {
        return parts.getStackInSlot(0).getCount() >= SHEETS && parts.getStackInSlot(1).getCount() >= MECHANISMS;
    }

    /** A landed first stage on a pad within {@link #RANGE} blocks and 3 up or down, waiting for a service. */
    public RocketEntity target() {
        var list = level.getEntitiesOfClass(RocketEntity.class, new AABB(worldPosition).inflate(RANGE, 3, RANGE),
                r -> r.needsService() && !r.isAscending() && !r.isReturning() && Math.abs(r.getY() - worldPosition.getY()) <= 3
                        && level.getBlockState(r.blockPosition()).is(CreateAerospace.LAUNCH_PAD.get()));
        return list.isEmpty() ? null : list.get(0);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, ServiceGantryBlockEntity be) {
        if (level.isClientSide || level.getGameTime() % 5 != 0) return;
        RocketEntity r = be.stocked() ? be.target() : null;
        if (r == null) {
            if (be.progress != 0) {
                be.progress = 0;
                be.setChanged();
            }
            return;
        }
        be.progress += 5;
        if (be.progress % 40 == 0) {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, .5f, 1.4f);
            ((ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK, r.getX(), r.getY() + 1 + level.random.nextDouble() * 6, r.getZ(),
                    12, 1.5, 1, 1.5, .1);
        }
        if (be.progress >= SERVICE_TICKS) {
            be.parts.getStackInSlot(0).shrink(SHEETS);
            be.parts.getStackInSlot(1).shrink(MECHANISMS);
            be.progress = 0;
            r.serviced();
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, .8f);
        }
        be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.put("parts", parts.serializeNBT(r));
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        parts.deserializeNBT(r, tag.getCompound("parts"));
        progress = tag.getInt("progress");
    }
}
