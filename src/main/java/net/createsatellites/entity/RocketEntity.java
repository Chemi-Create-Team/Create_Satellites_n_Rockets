package net.createsatellites.entity;

import java.util.UUID;
import net.createsatellites.CreateSatellites;
import net.createsatellites.orbit.OrbitMath;
import net.createsatellites.orbit.Satellite;
import net.createsatellites.orbit.SatelliteData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An ordinary entity with a 3D model. It stands on a launch pad until the pad fires it, climbs for a few seconds, then releases
 * its satellite into orbit and disappears.
 */
public class RocketEntity extends Entity {
    private static final EntityDataAccessor<Boolean> ASCENDING = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
    public static final double CLIMB = 200, ACCEL = 0.03, TOP_SPEED = 2.5;

    private UUID owner = new UUID(0, 0);
    private String ownerName = "";
    private String payloadName = "";
    private int ascent;
    private double startY = Double.NaN;

    public RocketEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setOwner(Player p) {
        owner = p.getUUID();
        ownerName = p.getGameProfile().getName();
    }

    public void setPayload(ItemStack stack) {
        var n = stack.get(DataComponents.CUSTOM_NAME);
        payloadName = n == null ? "" : n.getString();
    }

    public boolean isAscending() {
        return entityData.get(ASCENDING);
    }

    public void launch() {
        entityData.set(ASCENDING, true);
        startY = getY();
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 6f, 0.5f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ASCENDING, false);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAscending()) {
            if (!level().isClientSide && tickCount % 10 == 0 && !level().getBlockState(blockPosition()).is(CreateSatellites.LAUNCH_PAD.get())) {
                spawnAtLocation(payloadItem());
                discard();
            }
            return;
        }
        ascent++;
        double speed = Math.min(TOP_SPEED, ACCEL * ascent);
        setPos(getX(), getY() + speed, getZ());
        if (level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - .5) * .4, getY() - .2, getZ() + (random.nextDouble() - .5) * .4, 0, -.35, 0);
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + (random.nextDouble() - .5) * .6, getY() - .5, getZ() + (random.nextDouble() - .5) * .6, 0, -.05, 0);
            }
        } else {
            if (Double.isNaN(startY)) startY = getY() - speed * ascent / 2;
            if (getY() - startY >= CLIMB || getY() >= level().getMaxBuildHeight() - 4) deploy();
        }
    }

    private ItemStack payloadItem() {
        ItemStack s = new ItemStack(CreateSatellites.SATELLITE.get());
        if (!payloadName.isEmpty()) s.set(DataComponents.CUSTOM_NAME, Component.literal(payloadName));
        return s;
    }

    private void deploy() {
        var server = level().getServer();
        long now = server.overworld().getGameTime();
        String name = payloadName.isEmpty() ? String.format("SAT-%04X", random.nextInt(0x10000)) : payloadName;
        Satellite s = Satellite.deployed(name, owner, ownerName, OrbitMath.lonOf(getX()), now);
        SatelliteData.get(server).put(server, s);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.AMBIENT, 8f, 1f);
        if (server.getPlayerList().getPlayer(owner) instanceof ServerPlayer p)
            p.sendSystemMessage(Component.translatable("message.create_satellites_n_rockets.deployed", name));
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.hasUUID("owner")) owner = t.getUUID("owner");
        ownerName = t.getString("ownerName");
        payloadName = t.getString("payloadName");
        entityData.set(ASCENDING, t.getBoolean("ascending"));
        if (t.contains("startY")) startY = t.getDouble("startY");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putUUID("owner", owner);
        t.putString("ownerName", ownerName);
        t.putString("payloadName", payloadName);
        t.putBoolean("ascending", isAscending());
        if (!Double.isNaN(startY)) t.putDouble("startY", startY);
    }
}
