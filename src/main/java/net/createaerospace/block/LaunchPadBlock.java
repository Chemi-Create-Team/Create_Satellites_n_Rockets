package net.createaerospace.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createaerospace.CreateAerospace;
import net.createaerospace.entity.RocketEntity;
import net.createaerospace.api.AerospaceApi;
import net.createaerospace.api.RocketApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The pad holds the fuel and fires the rocket standing on it when it gets a redstone signal. Use a rocket item on it to stand a
 * rocket up, payloads to load it up to its mass limit, a fuel bucket or a pipe to fill it, an empty hand to read it, and sneak with
 * an empty hand to take the rocket back.
 */
public class LaunchPadBlock extends Block implements EntityBlock, IWrenchable {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 4, 16);

    public LaunchPadBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    public static AABB rocketSpace(BlockPos pos) {
        return new AABB(pos).expandTowards(0, 5, 0).deflate(0.1, 0, 0.1);
    }

    /** The rocket standing on the pad (not yet climbing), or null. */
    public static RocketEntity standing(Level level, BlockPos pos) {
        var list = level.getEntitiesOfClass(RocketEntity.class, rocketSpace(pos), r -> !r.isAscending());
        return list.isEmpty() ? null : list.get(0);
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.create_aerospace_engineering." + key, args), true);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // a rocket item (CAE's or one registered through api.RocketApi) stands a rocket up; a payload (api.AerospaceApi) is loaded onto it
        ResourceLocation rocketType = RocketApi.rocketOf(stack);
        boolean payload = AerospaceApi.payloadOf(stack) != null;
        if (rocketType == null && !payload) {
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection()))
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        RocketEntity standing = standing(level, pos);
        if (rocketType != null) {
            if (standing != null || !level.getEntitiesOfClass(RocketEntity.class, rocketSpace(pos)).isEmpty()) {
                say(player, "pad_occupied");
                return ItemInteractionResult.SUCCESS;
            }
            RocketEntity r = CreateAerospace.ROCKET.get().create(level);
            r.setRocket(rocketType, stack);
            r.setPos(pos.getX() + .5, pos.getY() + .25, pos.getZ() + .5);
            if (!level.noCollision(r.getBoundingBox())) {
                say(player, "pad_blocked");
                return ItemInteractionResult.SUCCESS;
            }
            r.setOwner(player);
            level.addFreshEntity(r);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1f, .8f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            say(player, "rocket_ready", RocketApi.rocketOrStandard(rocketType).type().name(), 0, RocketApi.rocketOrStandard(rocketType).type().maxPayloadKg());
            return ItemInteractionResult.SUCCESS;
        }
        if (standing == null) {
            say(player, "no_rocket");
        } else if (!standing.addPayload(stack)) {
            say(player, "too_heavy", AerospaceApi.massKg(stack), standing.loadedKg(), standing.type().type().maxPayloadKg());
        } else {
            standing.setOwner(player);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 1.2f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            say(player, "payload_loaded", stack.getHoverName(), standing.loadedKg(), standing.type().type().maxPayloadKg());
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        RocketEntity r = standing(level, pos);
        if (r != null && player.isShiftKeyDown()) {
            // take the rocket down with everything on it
            r.contents().forEach(s -> player.getInventory().placeItemBackInInventory(s));
            r.discard();
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        }
        Component rocket = r == null ? Component.translatable("message.create_aerospace_engineering.empty")
                : Component.translatable("message.create_aerospace_engineering.rocket_status", r.type().type().name(), r.payloadCount(), r.loadedKg(), r.type().type().maxPayloadKg());
        int fuel = (r == null ? RocketApi.rocketOrStandard(RocketApi.STANDARD) : r.type()).type().fuelMb();
        say(player, "pad_status", pad.tank.getFluidAmount(), pad.tank.getCapacity(), fuel, rocket);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), 2);
        if (powered && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad && !pad.tryLaunch())
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1f, 1f);
    }
}
