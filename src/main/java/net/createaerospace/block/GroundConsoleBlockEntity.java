package net.createaerospace.block;

import java.util.List;
import java.util.UUID;
import net.createaerospace.CreateAerospace;
import net.createaerospace.client.ClientSatellites;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteControl;
import net.createaerospace.orbit.SatelliteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Which satellite the console has selected, and the cover over the deorbit button. Everything else is read from the satellites. */
public class GroundConsoleBlockEntity extends BlockEntity {
    public static final int GUARD_TICKS = 200;

    private UUID selected;
    private long guardOpenedAt = Long.MIN_VALUE / 2;

    public GroundConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(CreateAerospace.GROUND_CONSOLE_BE.get(), pos, state);
    }

    public UUID selectedId() {
        return selected;
    }

    public boolean guardOpen() {
        return level != null && level.getGameTime() - guardOpenedAt < GUARD_TICKS;
    }

    private List<Satellite> all() {
        return level.isClientSide ? ClientSatellites.get() : SatelliteData.get(level.getServer()).list();
    }

    /** The selected satellite; the first one while nothing valid is selected. */
    public Satellite selected() {
        return SatelliteControl.pick(all(), selected);
    }

    public int selectedIndex() {
        Satellite s = selected();
        return s == null ? -1 : all().indexOf(s);
    }

    public int count() {
        return all().size();
    }

    /** One line for a Display Link. */
    public MutableComponent summary() {
        Satellite s = selected();
        if (s == null) return Component.translatable("console.create_aerospace_engineering.none");
        double[] p = OrbitMath.subpoint(s, level.getGameTime());
        return Component.literal(String.format("%s %s %s %dkm", s.name(), OrbitMath.fmtLat(p[0]), OrbitMath.fmtLon(p[1]), s.altKm()));
    }

    public void press(net.minecraft.world.entity.player.Player player, ConsoleButton button, boolean rightHalf) {
        SatelliteData data = SatelliteData.get(level.getServer());
        List<Satellite> all = data.list();
        if (all.isEmpty()) {
            say(player, "console.create_aerospace_engineering.none");
            return;
        }
        Satellite s = selected();
        int i = all.indexOf(s);
        long now = level.getGameTime();
        switch (button) {
            case PREVIOUS -> selected = all.get(Math.floorMod(i - 1, all.size())).id();
            case NEXT -> selected = all.get(Math.floorMod(i + 1, all.size())).id();
            case MAP -> {}
            default -> {
                if (!s.controlledBy(player.getUUID(), player.hasPermissions(2))) {
                    say(player, "console.create_aerospace_engineering.denied");
                    return;
                }
                if (button == ConsoleButton.DEORBIT) {
                    if (!guardOpen()) {
                        guardOpenedAt = now;
                        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1f, 1.2f);
                    } else {
                        guardOpenedAt = Long.MIN_VALUE / 2;
                        SatelliteControl.apply(level.getServer(), s, button, rightHalf, now);
                        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1f, 1f);
                    }
                } else {
                    SatelliteControl.apply(level.getServer(), s, button, rightHalf, now);
                }
            }
        }
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, .6f, 1.4f);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static void say(net.minecraft.world.entity.player.Player p, String key) {
        p.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        if (selected != null) tag.putUUID("selected", selected);
        tag.putLong("guard", guardOpenedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        selected = tag.hasUUID("selected") ? tag.getUUID("selected") : null;
        guardOpenedAt = tag.contains("guard") ? tag.getLong("guard") : Long.MIN_VALUE / 2;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, r);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
