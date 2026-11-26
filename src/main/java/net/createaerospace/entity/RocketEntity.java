package net.createaerospace.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.api.AerospaceApi;
import net.createaerospace.api.RocketApi;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Alert;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
import net.minecraft.core.BlockPos;
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
    /** The game tick of lift-off, so that a client that sees the rocket only mid-flight still knows how far into the ascent it is. */
    private static final EntityDataAccessor<Long> LAUNCHED_AT = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.LONG);
    /** The game tick a reusable first stage released its payloads and turned back; 0 while it is not coming back. */
    private static final EntityDataAccessor<Long> RETURNING_SINCE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.LONG);
    /** How a recovery ends, decided when the payloads are released. */
    public static final int LAND = 0, CRASH = 1, LOST = 2;

    /** Who stood the rocket up: may take it down, hears about a failure, and owns payloads loaded before 0.1.15. */
    private UUID owner = new UUID(0, 0);
    private String ownerName = "";
    /** Each one item, with all its components (the name, and whatever the mod of a payload put on it), in loading order. */
    private final List<ItemStack> payloads = new ArrayList<>();
    /** Who loaded each payload, in the same order: each satellite belongs to whoever loaded it. */
    private final List<Loader> loaders = new ArrayList<>();

    private record Loader(UUID id, String name) {}
    private int ascent;
    private double startY = Double.NaN;
    /** The ascent tick at which this flight fails; 0 for a good flight. Decided at lift-off. */
    private int failAt;
    private int returnTicks;
    /** Where the first stage turned back. */
    private double topY;
    /** LAND, CRASH or LOST; -1 until decided (a gametest may set it beforehand). */
    private int recoveryOutcome = -1;

    public RocketEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setOwner(Player p) {
        owner = p.getUUID();
        ownerName = p.getGameProfile().getName();
    }

    /** Whether a player may take the rocket down with everything on it: whoever stood it up, or an operator. */
    public boolean ownedBy(Player p) {
        return p.getUUID().equals(owner) || p.hasPermissions(2);
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

    /** Load one payload if it fits under the mass limit; its satellite will belong to {@code loader}. */
    public boolean addPayload(ItemStack stack, Player loader) {
        int kg = AerospaceApi.massKg(stack);
        if (kg <= 0 || isAscending() || isReturning() || needsService() || loadedKg() + kg > type().type().maxPayloadKg()) return false;
        payloads.add(stack.copyWithCount(1));
        loaders.add(new Loader(loader.getUUID(), loader.getGameProfile().getName()));
        syncLoad();
        return true;
    }

    private Loader loader(int i) {
        return i < loaders.size() ? loaders.get(i) : new Loader(owner, ownerName);
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

    /** A reusable first stage on its way back to the pad. */
    public boolean isReturning() {
        return entityData.get(RETURNING_SINCE) != 0;
    }

    public int returnTicks() {
        return returnTicks;
    }

    /** A landed first stage that must be serviced before it takes payloads again. */
    public boolean needsService() {
        return RocketApi.needsService(entityData.get(ITEM));
    }

    /** The service gantry is done with it. */
    public void serviced() {
        ItemStack item = entityData.get(ITEM);
        entityData.set(ITEM, RocketApi.withService(item, RocketApi.flights(item), false));
    }

    /** Gametests only: how the next recovery ends, whatever the chances say. */
    public void setRecoveryOutcome(int outcome) {
        recoveryOutcome = outcome;
    }

    public void launch() {
        entityData.set(ASCENDING, true);
        entityData.set(LAUNCHED_AT, level().getGameTime());
        startY = getY();
        var rocketType = type().type();
        if (failAt == 0 && level().getGameRules().getBoolean(CreateAerospace.LAUNCH_FAILURES) && random.nextDouble() < rocketType.failureChance()) {
            // somewhere between clearing the pad and the release of the payloads
            failAt = 40 + random.nextInt(Math.max(1, (int) (rocketType.ticksToOrbit() * 0.85) - 40));
        }
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
        builder.define(LAUNCHED_AT, 0L);
        builder.define(RETURNING_SINCE, 0L);
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
        if (isReturning()) {
            returnFlight();
            return;
        }
        if (!isAscending()) {
            if (!level().isClientSide && tickCount % 10 == 0 && !level().getBlockState(blockPosition()).is(CreateAerospace.LAUNCH_PAD.get())) {
                contents().forEach(this::spawnAtLocation);
                discard();
            }
            return;
        }
        ascent++;
        // a client that started watching mid-flight catches up with the server's count
        if (level().isClientSide && entityData.get(LAUNCHED_AT) > 0) ascent = (int) Math.max(1, level().getGameTime() - entityData.get(LAUNCHED_AT));
        var t = type().type();
        double speed = Math.min(t.topSpeed(), t.acceleration() * ascent);
        double ceiling = level().getMaxBuildHeight() - Math.max(4, getBbHeight());
        setPos(getX(), Math.max(getY(), Math.min(getY() + speed, ceiling)), getZ());
        if (level().isClientSide) {
            net.createaerospace.client.RocketRenderer.exhaust(this);
        } else {
            if (Double.isNaN(startY)) startY = getY() - speed * ascent / 2;
            if (failAt > 0 && ascent >= failAt) explode();
            else if (getY() - startY >= t.climbBlocks() || getY() >= ceiling) deploy();
        }
    }

    /** Gametests only: make this flight fail at an ascent tick (or, with -1, never), whatever the chance and the game rule say. */
    public void setFailAt(int tick) {
        failAt = tick;
    }

    /**
     * The flight fails: a fireball along the body, burning debris raining down, a bang heard far off, and the payloads gone. The
     * explosion hurts what is near but breaks no blocks. Every console, map and Control Computer shows the warning for a while.
     */
    private void explode() {
        var level = (ServerLevel) level();
        var server = level.getServer();
        double x = getX(), z = getZ();
        // the explosion packet only reaches players near it; boom() sends to everyone who can see the rocket
        boom(level, x, getY(), z, getBbWidth(), getBbHeight());
        var type = type();
        SatelliteData.get(server).alert(server, new Alert(Alert.LAUNCH_FAILURE, type.id().toString(), ownerName, payloads.size(),
                (int) Math.floor(x), (int) Math.floor(z), server.overworld().getGameTime()));
        // the one who stood it up and everyone who had a payload on it
        var told = new java.util.HashSet<UUID>();
        told.add(owner);
        for (int i = 0; i < payloads.size(); i++) told.add(loader(i).id());
        for (UUID id : told)
            if (server.getPlayerList().getPlayer(id) instanceof ServerPlayer p)
                p.sendSystemMessage(Component.translatable("message.create_aerospace_engineering.launch_failed", type.type().name(), payloads.size()));
        try {
            type.hooks().failed(level, this, payloads());
        } catch (RuntimeException e) {
            CreateAerospace.LOGGER.error("Rocket {} failed in failed()", type.id(), e);
        }
        discard();
    }

    private void deploy() {
        var level = (ServerLevel) level();
        var server = level.getServer();
        long now = server.overworld().getGameTime();
        List<AerospaceApi.SatelliteInfo> deployed = new ArrayList<>();
        for (int i = 0; i < payloads.size(); i++) {
            ItemStack stack = payloads.get(i);
            Loader loader = loader(i);
            var custom = stack.get(DataComponents.CUSTOM_NAME);
            String name = custom == null ? String.format("SAT-%04X", random.nextInt(0x10000)) : custom.getString();
            var kind = AerospaceApi.payloadOf(stack);
            Satellite s = Satellite.deployed(UUID.randomUUID(), name, loader.id(), loader.name(), OrbitMath.lonOf(getX()), now,
                    kind == null || kind.equals(AerospaceApi.STANDARD) ? "" : kind.toString());
            AerospaceApi.deploy(level, stack, s);
            AerospaceApi.satellite(server, s.id()).ifPresent(deployed::add);
            if (server.getPlayerList().getPlayer(loader.id()) instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("message.create_aerospace_engineering.deployed", name));
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.AMBIENT, 8f, 1f);
        var t = type();
        try {
            t.hooks().deployed(level, this, List.copyOf(deployed));
        } catch (RuntimeException e) {
            CreateAerospace.LOGGER.error("Rocket {} failed in deployed()", t.id(), e);
        }
        var recovery = t.type().recovery();
        if (recovery == null) {
            discard();
            return;
        }
        // a reusable first stage: decide now how its way back ends
        if (recoveryOutcome < 0) {
            double roll = random.nextDouble();
            boolean failures = level.getGameRules().getBoolean(CreateAerospace.LAUNCH_FAILURES);
            recoveryOutcome = !failures ? LAND : roll < recovery.noReturnChance() ? LOST
                    : roll < recovery.noReturnChance() + recovery.landingFailureChance() ? CRASH : LAND;
        }
        payloads.clear();
        loaders.clear();
        syncLoad();
        if (recoveryOutcome == LOST) {
            tell("message.create_aerospace_engineering.booster_lost", t.type().name());
            discard();
            return;
        }
        topY = getY();
        returnTicks = 0;
        entityData.set(ASCENDING, false);
        entityData.set(RETURNING_SINCE, level.getGameTime());
    }

    private void tell(String key, Object... args) {
        if (level().getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable(key, args));
    }

    /** Hold and turn back at the top, fall, brake and touch down on the pad it left; the position follows from the tick alone. */
    private void returnFlight() {
        returnTicks++;
        if (level().isClientSide) {
            returnTicks = (int) Math.max(1, level().getGameTime() - entityData.get(RETURNING_SINCE));
            net.createaerospace.client.RocketRenderer.returnExhaust(this);
            return;
        }
        if (Double.isNaN(startY)) startY = topY - 1;
        setPos(getX(), startY + (topY - startY) * (1 - RocketApi.Recovery.fall(returnTicks)), getZ());
        if (returnTicks < RocketApi.Recovery.HOLD_TICKS + RocketApi.Recovery.FALL_TICKS) return;
        var level = (ServerLevel) level();
        BlockPos pad = BlockPos.containing(getX(), startY, getZ());
        boolean clear = level.getBlockState(pad).is(CreateAerospace.LAUNCH_PAD.get())
                && level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(pad), r -> r != this && !r.isReturning()).isEmpty();
        if (recoveryOutcome != LAND || !clear) {
            crashLanding();
            return;
        }
        // landed: standing on its pad again, empty and sooty, waiting for the gantry
        setPos(getX(), startY, getZ());
        ItemStack item = entityData.get(ITEM);
        entityData.set(ITEM, RocketApi.withService(item, RocketApi.flights(item) + 1, true));
        entityData.set(RETURNING_SINCE, 0L);
        ascent = 0;
        failAt = 0;
        recoveryOutcome = -1;
        startY = Double.NaN;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4f, 1.6f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, getX(), getY() + .5, getZ(), 120, 3, .2, 3, .25);
        tell("message.create_aerospace_engineering.booster_landed", type().type().name(), RocketApi.flights(entityData.get(ITEM)));
    }

    /** The landing burn does not save it: it hits the pad and explodes. The pad survives; the rocket item is lost. */
    private void crashLanding() {
        var level = (ServerLevel) level();
        var server = level.getServer();
        boom(level, getX(), getY(), getZ(), getBbWidth(), Math.min(getBbHeight(), 20));
        SatelliteData.get(server).alert(server, new Alert(Alert.LANDING_FAILURE, type().id().toString(), ownerName, 0,
                (int) Math.floor(getX()), (int) Math.floor(getZ()), server.overworld().getGameTime()));
        tell("message.create_aerospace_engineering.booster_crashed", type().type().name());
        discard();
    }

    private static void boom(ServerLevel level, double x, double y, double z, double w, double h) {
        level.explode(null, x, y + h * .35, z, 6f, Level.ExplosionInteraction.NONE);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y + h / 2, z) > 512 * 512) continue;
            for (int k = 0; k <= 4; k++)
                level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, true, x, y + h * k / 4, z, 2, w / 3, 1, w / 3, 0);
            level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.FLAME, true, x, y + h / 2, z, 220, w / 2, h / 2, w / 2, .25);
            level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, true, x, y + h / 2, z, 160, w / 2, h / 2, w / 2, .08);
            level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.LAVA, true, x, y + h / 2, z, 80, w, h / 2, w, 0);
        }
        level.playSound(null, x, y + h / 2, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 16f, 0.55f);
        level.playSound(null, x, y + h / 2, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.BLOCKS, 16f, 0.5f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.hasUUID("owner")) owner = t.getUUID("owner");
        ownerName = t.getString("ownerName");
        payloads.clear();
        loaders.clear();
        // loaders before 0.1.15 were not kept: those payloads go to whoever stood the rocket up
        var who = t.getList("loaders", Tag.TAG_COMPOUND);
        var all = t.getList("payloads", Tag.TAG_COMPOUND);
        for (int i = 0; i < all.size(); i++) {
            ItemStack s = ItemStack.parseOptional(registryAccess(), all.getCompound(i));
            if (s.isEmpty()) continue;
            payloads.add(s);
            CompoundTag l = i < who.size() ? who.getCompound(i) : new CompoundTag();
            loaders.add(l.hasUUID("id") ? new Loader(l.getUUID("id"), l.getString("name")) : new Loader(owner, ownerName));
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
        failAt = t.getInt("failAt");
        if (t.contains("startY")) startY = t.getDouble("startY");
        if (isAscending()) entityData.set(LAUNCHED_AT, Math.max(1, level().getGameTime() - ascent));
        recoveryOutcome = t.contains("recovery") ? t.getInt("recovery") : -1;
        if (t.contains("returnTicks")) {
            returnTicks = t.getInt("returnTicks");
            topY = t.getDouble("topY");
            entityData.set(RETURNING_SINCE, Math.max(1, level().getGameTime() - returnTicks));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putUUID("owner", owner);
        t.putString("ownerName", ownerName);
        // the id is kept even when its mod is gone, so the rocket comes back as itself when the mod does
        t.putString("rocketType", entityData.get(TYPE));
        if (!entityData.get(ITEM).isEmpty()) t.put("rocketItem", entityData.get(ITEM).save(registryAccess()));
        ListTag list = new ListTag();
        ListTag who = new ListTag();
        for (int i = 0; i < payloads.size(); i++) {
            list.add(payloads.get(i).save(registryAccess()));
            CompoundTag l = new CompoundTag();
            l.putUUID("id", loader(i).id());
            l.putString("name", loader(i).name());
            who.add(l);
        }
        t.put("payloads", list);
        t.put("loaders", who);
        t.putBoolean("ascending", isAscending());
        t.putInt("ascent", ascent);
        if (failAt != 0) t.putInt("failAt", failAt);
        if (!Double.isNaN(startY)) t.putDouble("startY", startY);
        if (recoveryOutcome >= 0) t.putInt("recovery", recoveryOutcome);
        if (isReturning()) {
            t.putInt("returnTicks", returnTicks);
            t.putDouble("topY", topY);
        }
    }
}
