package net.createaerospace.relay;

import java.util.ArrayList;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class OrbitalDishEntity extends BlockEntity {
    private static final String KEY = "relay.create_aerospace_engineering.";
    private static final ItemStackHandler EMPTY = new ItemStackHandler(RelayStorage.SLOTS);
    private UUID owner;
    private UUID target;
    private float bearing;
    private float elevation = 30;

    public OrbitalDishEntity(BlockPos pos, BlockState state) {
        super(CreateAerospace.DISH_BE.get(), pos, state);
        bearing = state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public float bearing() {
        return bearing;
    }

    public float elevation() {
        return elevation;
    }

    public void nextSatellite(Player player) {
        if (!(level instanceof ServerLevel world)) return;
        boolean op = player.hasPermissions(2);
        if (player.isSpectator() || !player.getAbilities().mayBuild || !world.mayInteract(player, worldPosition)
                || owner != null && !owner.equals(player.getUUID()) && !op) {
            tell(player, "denied");
            return;
        }
        var satellites = SatelliteData.get(world.getServer());
        var candidates = new ArrayList<>(satellites.list().stream().filter(RelayStorage::isRelay)
                .filter(s -> !RelayStorage.retired(s) && s.controlledBy(player.getUUID(), op)).map(Satellite::id).toList());
        RelayStorage.get(world.getServer()).retired(satellites, player.getUUID(), op).forEach(c -> candidates.add(c.id()));
        if (candidates.isEmpty()) {
            tell(player, "none");
            return;
        }
        if (owner == null) owner = player.getUUID();
        target = candidates.get((candidates.indexOf(target) + 1) % candidates.size());
        setChanged();
        refresh(world);
        world.invalidateCapabilities(worldPosition);
        showStatus(player);
    }

    public void showStatus(Player player) {
        if (!(level instanceof ServerLevel world) || target == null) {
            tell(player, "unbound");
            return;
        }
        var satellite = SatelliteData.get(world.getServer()).find(target);
        if (!RelayStorage.retired(satellite)) {
            tell(player, RelayStorage.signal(world, satellite, worldPosition) != null ? "linked" : "out_of_view", satellite.name());
        } else {
            var retired = RelayStorage.get(world.getServer()).find(target);
            if (retired != null && retired.hasItems()) tell(player, "recovery", retired.name());
            else tell(player, "unbound");
        }
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(KEY + key, args), true);
    }

    void tick() {
        if (level instanceof ServerLevel world && (world.getGameTime() + worldPosition.asLong()) % 20 == 0) refresh(world);
    }

    private void refresh(ServerLevel world) {
        var satellite = target == null ? null : SatelliteData.get(world.getServer()).find(target);
        var aim = satellite == null ? null : RelayStorage.signal(world, satellite, worldPosition);
        boolean online = aim != null;
        var state = getBlockState();
        if (state.getValue(BlockStateProperties.LIT) != online) {
            world.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, online), Block.UPDATE_CLIENTS);
            level.invalidateCapabilities(worldPosition);
        }
        if (aim == null) return;
        float nextBearing = (float) Math.toDegrees(aim.azimuth()), nextElevation = (float) Math.toDegrees(aim.elevation());
        if (Math.abs(bearing - nextBearing) > .15f || Math.abs(elevation - nextElevation) > .15f) {
            bearing = nextBearing;
            elevation = nextElevation;
            world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public final IItemHandler items = new IItemHandler() {
        @Override public int getSlots() { return RelayStorage.SLOTS; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var inventory = inventory(false, false);
            return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var storage = inventory(true, !simulate && !stack.isEmpty());
            return storage == null ? stack : storage.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var inventory = inventory(false, false);
            return inventory == null ? ItemStack.EMPTY : inventory.extractItem(slot, amount, simulate);
        }
    };

    private ItemStackHandler inventory(boolean inserting, boolean create) {
        if (!(level instanceof ServerLevel world) || target == null || isRemoved()) return null;
        var storage = RelayStorage.get(world.getServer());
        var satellite = SatelliteData.get(world.getServer()).find(target);
        var channel = storage.find(target);
        if (RelayStorage.retired(satellite)) return inserting || channel == null ? null : channel.items();
        if (RelayStorage.signal(world, satellite, worldPosition) == null) return null;
        if (channel != null) return channel.items();
        return create ? storage.activate(satellite).items() : EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        if (target != null) tag.putUUID("RelayTarget", target);
        tag.putFloat("DishBearing", bearing);
        tag.putFloat("DishElevation", elevation);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        target = tag.hasUUID("RelayTarget") ? tag.getUUID("RelayTarget") : null;
        if (tag.contains("DishBearing")) bearing = tag.getFloat("DishBearing");
        if (tag.contains("DishElevation")) elevation = tag.getFloat("DishElevation");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putFloat("DishBearing", bearing);
        tag.putFloat("DishElevation", elevation);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
