package net.createsatellites;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.util.SableNBTUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/** The orbit dimension, and moving whole contraptions between it and the overworld. */
public final class Orbit {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, CreateSatellites.id("orbit"));
    /** Overworld height where the sky ends and the orbit begins. */
    public static final double ASCENT_Y = 512;
    /** Orbit height below which a contraption falls back toward the Earth. */
    public static final double REENTRY_Y = -200;
    /** Where the contraption appears in the overworld after re-entry. Stays under ASCENT_Y so it does not bounce back. */
    public static final double REENTRY_ARRIVAL_Y = 480;
    public static final double ORBIT_ARRIVAL_Y = 0;

    private Orbit() {}

    static void tick(ServerLevel level) {
        if (level.getGameTime() % 10 == 0 && level.dimension().equals(Level.OVERWORLD)) {
            ServerLevel orbit = level.getServer().getLevel(KEY);
            if (orbit != null) exchange(level, orbit);
        }
    }

    /** Ships above the sky line leave the Earth for orbit, ships below the bottom of orbit fall back to the Earth. */
    public static void exchange(ServerLevel earth, ServerLevel orbit) {
        for (ServerSubLevel s : new ArrayList<>(SubLevelContainer.getContainer(earth).getAllSubLevels())) {
            // ponytail: when the target plot is taken this just retries every half second; a fuller version would queue
            if (s.logicalPose().position().y > ASCENT_Y) transfer(s, orbit, ORBIT_ARRIVAL_Y);
        }
        for (ServerSubLevel s : new ArrayList<>(SubLevelContainer.getContainer(orbit).getAllSubLevels())) {
            if (s.logicalPose().position().y < REENTRY_Y) transfer(s, earth, REENTRY_ARRIVAL_Y);
        }
    }

    /**
     * Moves a sub-level and the players standing in it to another dimension, keeping x/z, orientation and velocity.
     * Block entity positions are saved as absolute plot coordinates, so the sub-level has to take the same plot slot
     * it had before; if that slot is taken in the target dimension nothing happens and null is returned.
     * Sections are saved by index, so both dimensions must have the same build height (the orbit type copies the overworld).
     */
    public static ServerSubLevel transfer(ServerSubLevel sub, ServerLevel to, double newY) {
        ServerLevel from = sub.getLevel();
        var fromC = SubLevelContainer.getContainer(from);
        var toC = SubLevelContainer.getContainer(to);
        int index = toC.getIndex(sub.getPlot().plotPos.x - fromC.getOrigin().x, sub.getPlot().plotPos.z - fromC.getOrigin().y);
        if (from == to || from.getMinBuildHeight() != to.getMinBuildHeight() || from.getHeight() != to.getHeight()
                || toC.getOccupancy().get(index)) return null;

        var handle = RigidBodyHandle.of(sub);
        Vector3d lin = handle.getLinearVelocity(new Vector3d());
        Vector3d ang = handle.getAngularVelocity(new Vector3d());
        var oldPose = new dev.ryanhcode.sable.companion.math.Pose3d(sub.logicalPose());
        List<ServerPlayer> riders = from.getEntitiesOfClass(ServerPlayer.class, new AABB(
                sub.boundingBox().minX(), sub.boundingBox().minY(), sub.boundingBox().minZ(),
                sub.boundingBox().maxX(), sub.boundingBox().maxY(), sub.boundingBox().maxZ()).inflate(1));
        List<Vector3d> local = riders.stream().map(p -> oldPose.transformPositionInverse(new Vector3d(p.getX(), p.getY(), p.getZ()))).toList();

        var data = SubLevelSerializer.toData(sub, List.of());
        var tag = data.fullTag().copy();
        var pose = SableNBTUtils.readPose3d(tag.getCompound("pose"));
        pose.position().y = newY + (pose.position().y - oldPose.position().y);
        tag.put("pose", SableNBTUtils.writePose3d(pose));
        // fullyLoad scales velocity by a config value; set it ourselves afterwards
        tag.remove("linear_velocity");
        tag.remove("angular_velocity");

        fromC.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
        ServerSubLevel moved = null;
        try {
            moved = SubLevelSerializer.fullyLoad(to, SubLevelSerializer.fromData(tag));
        } catch (RuntimeException e) {
            CreateSatellites.LOGGER.error("Could not load {} in {}", data.uuid(), to.dimension().location(), e);
        }
        if (moved == null) {
            // never lose a ship: drop the half-loaded copy and put the ship back where it was
            var half = toC.getSubLevel(data.uuid());
            if (half != null) toC.removeSubLevel(half, SubLevelRemovalReason.REMOVED);
            SubLevelSerializer.fullyLoad(from, data);
            return null;
        }
        toC.physicsSystem().getPipeline().addLinearAndAngularVelocity(moved, lin, ang);
        for (int i = 0; i < riders.size(); i++) {
            Vector3d p = moved.logicalPose().transformPosition(local.get(i));
            riders.get(i).teleportTo(to, p.x, p.y, p.z, riders.get(i).getYRot(), riders.get(i).getXRot());
        }
        return moved;
    }
}
