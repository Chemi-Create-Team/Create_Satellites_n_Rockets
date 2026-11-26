package net.createaerospace.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.api.AerospaceApi;
import net.createaerospace.api.RocketApi;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A rocket of any registered type ({@link RocketApi}). It stands on a launch pad and takes payloads up to the type's mass limit
 * until the pad fires it; it climbs, releases every payload as a satellite and disappears.
 */
public class RocketEntity extends Entity {
    private static final EntityDataAccessor<Boolean> ASCENDING = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> LOADED_KG = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAYLOADS = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);

    private UUID owner = new UUID(0, 0);
    private String ownerName = "";
    /** Each one item, with all its components (the name, and whatever the mod of a payload put on it), in loading order. */
    private final List<ItemStack> payloads = new ArrayList<>();
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

    /** Make this a rocket of the type {@code stack} stands up; the stack itself is kept and given back when the rocket is taken down. */
    public void setRocket(ResourceLocation type, ItemStack stack) {
        entityData.set(TYPE, type.toString());
        entityData.set(ITEM, stack.copyWithCount(1));
        refreshDimensions();
    }

    public ResourceLocation typeId() {
        ResourceLocation id = ResourceLocation.tryParse(entityData.get(TYPE));
        return id == null ? RocketApi.STANDARD : id;
    }

    public RocketApi.Registered type() {
        return RocketApi.rocketOrStandard(typeId());
    }

    public ItemStack rocketItem() {
        return entityData.get(ITEM).copy();
    }

    public int loadedKg() {
        return entityData.get(LOADED_KG);
    }

    public int payloadCount() {
        return entityData.get(PAYLOADS);
    }

    public int ascentTicks() {
        return ascent;
    }

    /** Load one payload if it fits under the mass limit. */
    public boolean addPayload(ItemStack stack) {
        int kg = AerospaceApi.massKg(stack);
        if (kg <= 0 || isAscending() || loadedKg() + kg > type().type().maxPayloadKg()) return false;
        payloads.add(stack.copyWithCount(1));
        syncLoad();
        return true;
    }

    public List<ItemStack> payloads() {
        return payloads.stream().map(ItemStack::copy).toList();
    }

    /** The rocket item and its payloads, to give back or drop when the rocket comes down without flying. */
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>(payloads());
        if (!entityData.get(ITEM).isEmpty()) out.add(0, rocketItem());
        return out;
    }

    private void syncLoad() {
        entityData.set(LOADED_KG, payloads.stream().mapToInt(AerospaceApi::massKg).sum());
        entityData.set(PAYLOADS, payloads.size());
    }

    public boolean isAscending() {
        return entityData.get(ASCENDING);
    }

    public void launch() {
        entityData.set(ASCENDING, true);
        startY = getY();
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 6f, 0.5f);
        var t = type();
        try {
            t.hooks().launched((ServerLevel) level(), this, payloads());
        } catch (RuntimeException e) {
            CreateAerospace.LOGGER.error("Rocket {} failed in launched()", t.id(), e);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ASCENDING, false);
        builder.define(TYPE, RocketApi.STANDARD.toString());
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(LOADED_KG, 0);
        builder.define(PAYLOADS, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TYPE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        var t = RocketApi.rocket(typeId());
        return t == null ? super.getDimensions(pose) : EntityDimensions.fixed(t.type().width(), t.type().height());
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
            if (!level().isClientSide && tickCount % 10 == 0 && !level().getBlockState(blockPosition()).is(CreateAerospace.LAUNCH_PAD.get())) {
                contents().forEach(this::spawnAtLocation);
                discard();
            }
            return;
        }
        ascent++;
        var t = type().type();
        double speed = Math.min(t.topSpeed(), t.acceleration() * ascent);
        double ceiling = level().getMaxBuildHeight() - Math.max(4, getBbHeight());
        setPos(getX(), Math.max(getY(), Math.min(getY() + speed, ceiling)), getZ());
        if (level().isClientSide) {
            net.createaerospace.client.RocketRenderer.exhaust(this);
        } else {
            if (Double.isNaN(startY)) startY = getY() - speed * ascent / 2;
            if (getY() - startY >= t.climbBlocks() || getY() >= ceiling) deploy();
        }
    }

    private void deploy() {
        var level = (ServerLevel) level();
        var server = level.getServer();
        long now = server.overworld().getGameTime();
        var player = server.getPlayerList().getPlayer(owner);
        List<AerospaceApi.SatelliteInfo> deployed = new ArrayList<>();
        for (ItemStack stack : payloads) {
            var custom = stack.get(DataComponents.CUSTOM_NAME);
            String name = custom == null ? String.format("SAT-%04X", random.nextInt(0x10000)) : custom.getString();
            var kind = AerospaceApi.payloadOf(stack);
            Satellite s = Satellite.deployed(UUID.randomUUID(), name, owner, ownerName, OrbitMath.lonOf(getX()), now,
                    kind == null || kind.equals(AerospaceApi.STANDARD) ? "" : kind.toString());
            AerospaceApi.deploy(level, stack, s);
            AerospaceApi.satellite(server, s.id()).ifPresent(deployed::add);
            if (player instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("message.create_aerospace_engineering.deployed", name));
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.AMBIENT, 8f, 1f);
        var t = type();
        try {
            t.hooks().deployed(level, this, List.copyOf(deployed));
        } catch (RuntimeException e) {
            CreateAerospace.LOGGER.error("Rocket {} failed in deployed()", t.id(), e);
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.hasUUID("owner")) owner = t.getUUID("owner");
        ownerName = t.getString("ownerName");
        payloads.clear();
        for (Tag p : t.getList("payloads", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.parseOptional(registryAccess(), (CompoundTag) p);
            if (!s.isEmpty()) payloads.add(s);
        }
        // before 0.1.7 a rocket had one payload and no item of its own; before 0.1.6 it kept only the satellite's name
        if (t.contains("payload")) {
            ItemStack s = ItemStack.parseOptional(registryAccess(), t.getCompound("payload"));
            if (!s.isEmpty()) payloads.add(s);
        } else if (!t.getString("payloadName").isEmpty()) {
            ItemStack s = new ItemStack(CreateAerospace.SATELLITE.get());
            s.set(DataComponents.CUSTOM_NAME, Component.literal(t.getString("payloadName")));
            payloads.add(s);
        }
        ResourceLocation type = ResourceLocation.tryParse(t.getString("rocketType"));
        setRocket(type == null ? RocketApi.STANDARD : type,
                t.contains("rocketItem") ? ItemStack.parseOptional(registryAccess(), t.getCompound("rocketItem")) : ItemStack.EMPTY);
        syncLoad();
        entityData.set(ASCENDING, t.getBoolean("ascending"));
        ascent = t.getInt("ascent");
        if (t.contains("startY")) startY = t.getDouble("startY");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putUUID("owner", owner);
        t.putString("ownerName", ownerName);
        // the id is kept even when its mod is gone, so the rocket comes back as itself when the mod does
        t.putString("rocketType", entityData.get(TYPE));
        if (!entityData.get(ITEM).isEmpty()) t.put("rocketItem", entityData.get(ITEM).save(registryAccess()));
        ListTag list = new ListTag();
        payloads.forEach(p -> list.add(p.save(registryAccess())));
        t.put("payloads", list);
        t.putBoolean("ascending", isAscending());
        t.putInt("ascent", ascent);
        if (!Double.isNaN(startY)) t.putDouble("startY", startY);
    }
}
