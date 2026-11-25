package net.createsatellites;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class RocketEngineBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    public static final TagKey<Fluid> FUEL = TagKey.create(Registries.FLUID, CreateSatellites.id("rocket_fuel"));
    public static final TagKey<Fluid> STRONG_FUEL = TagKey.create(Registries.FLUID, CreateSatellites.id("rocket_fuel_strong"));
    public static final int CAPACITY = 8000;
    /** mB burned per tick. */
    public static final int BURN = 4;
    /** Newtons. Sable blocks weigh 1 kg and gravity is 11 m/s^2, so one engine carries about 100 blocks. */
    public static final double THRUST = 1200;

    public final FluidTank tank = new FluidTank(CAPACITY, s -> s.is(FUEL)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    /** Written on the server thread, read by the physics step. */
    private volatile double thrust;

    public RocketEngineBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSatellites.ROCKET_ENGINE_BE.get(), pos, state);
    }

    /** Burning is ordered by a redstone signal or by the launch controller, which writes "ignited" into the sub-level. */
    boolean ordered() {
        if (level.hasNeighborSignal(worldPosition)) return true;
        return Sable.HELPER.getContaining(this) instanceof ServerSubLevel s
                && s.getUserDataTag() != null && s.getUserDataTag().getBoolean(LaunchControllerBlock.IGNITED);
    }

    void serverTick() {
        double t = 0;
        if (ordered() && tank.drain(BURN, IFluidHandler.FluidAction.SIMULATE).getAmount() == BURN) {
            FluidStack burnt = tank.drain(BURN, IFluidHandler.FluidAction.EXECUTE);
            t = burnt.getFluid().is(STRONG_FUEL) ? THRUST * 2 : THRUST;
        }
        thrust = t;
        boolean lit = t > 0;
        if (lit != getBlockState().getValue(RocketEngineBlock.LIT)) {
            level.setBlock(worldPosition, getBlockState().setValue(RocketEngineBlock.LIT, lit), 3);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double dt) {
        double t = thrust;
        if (t <= 0) return;
        var dir = getBlockState().getValue(RocketEngineBlock.FACING).getOpposite().getNormal();
        // plot-local point and direction, like the Aeronautics potato cannon recoil
        handle.applyImpulseAtPoint(Vec3.atCenterOf(worldPosition), new Vec3(dir.getX(), dir.getY(), dir.getZ()).scale(t * dt));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tank.writeToNBT(r, tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        tank.readFromNBT(r, tag);
    }
}
