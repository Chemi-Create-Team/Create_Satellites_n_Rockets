package net.createaerospace.block;

import net.createaerospace.CreateAerospace;
import net.createaerospace.entity.RocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class LaunchPadBlockEntity extends BlockEntity {
    public static final int CAPACITY = net.createaerospace.api.RocketApi.MAX_FUEL_MB;

    public final FluidTank tank = new FluidTank(CAPACITY, fs -> fs.getFluid().is(CreateAerospace.ROCKET_FUEL)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(CreateAerospace.LAUNCH_PAD_BE.get(), pos, state);
    }

    /** Burns the fuel and fires the loaded rocket. Returns false, changing nothing, if there is no rocket or too little fuel. */
    public boolean tryLaunch() {
        RocketEntity r = LaunchPadBlock.standing(level, worldPosition);
        if (r == null || r.payloadCount() == 0) return false;
        int fuel = r.type().type().fuelMb();
        if (tank.getFluidAmount() < fuel) return false;
        if (fuel > 0) tank.drain(fuel, IFluidHandler.FluidAction.EXECUTE);
        r.launch();
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
