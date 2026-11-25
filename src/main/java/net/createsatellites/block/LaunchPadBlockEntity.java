package net.createsatellites.block;

import net.createsatellites.CreateSatellites;
import net.createsatellites.entity.RocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class LaunchPadBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8000, FUEL_PER_LAUNCH = 4000;

    public final FluidTank tank = new FluidTank(CAPACITY, fs -> fs.getFluid().is(CreateSatellites.ROCKET_FUEL)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSatellites.LAUNCH_PAD_BE.get(), pos, state);
    }

    /** Burns the fuel and fires the loaded rocket. Returns false, changing nothing, if there is no rocket or too little fuel. */
    public boolean tryLaunch() {
        var rockets = level.getEntitiesOfClass(RocketEntity.class, LaunchPadBlock.rocketSpace(worldPosition), r -> !r.isAscending());
        if (rockets.isEmpty() || tank.getFluidAmount() < FUEL_PER_LAUNCH) return false;
        tank.drain(FUEL_PER_LAUNCH, IFluidHandler.FluidAction.EXECUTE);
        rockets.get(0).launch();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.put("tank", tank.writeToNBT(r, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        tank.readFromNBT(r, tag.getCompound("tank"));
    }
}
