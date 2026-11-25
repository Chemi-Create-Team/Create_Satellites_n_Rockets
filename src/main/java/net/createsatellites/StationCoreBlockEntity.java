package net.createsatellites;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public class StationCoreBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    /** Fraction of the spin and drift removed per second. */
    public static final double DAMPING = 1.5;

    public StationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSatellites.STATION_CORE_BE.get(), pos, state);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double dt) {
        if (DimensionPhysicsData.getGravity(subLevel.getLevel()).lengthSquared() > 0) return;
        double k = -Math.min(1, DAMPING * dt);
        handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).mul(k), handle.getAngularVelocity(new Vector3d()).mul(k));
    }
}
