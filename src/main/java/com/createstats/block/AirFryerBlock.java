package com.createstats.block;

import com.createstats.blockentity.AirFryerBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class AirFryerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final double[][] SHAPE_BOXES = {
            {1.6, 0.0, 2.4, 14.4, 2.4, 14.4},
            {1.4, 2.4, 2.2, 3.0, 14.0, 14.5},
            {13.0, 2.4, 2.2, 14.6, 14.0, 14.5},
            {3.0, 2.4, 9.6, 13.0, 14.0, 14.5},
            {3.0, 8.2, 2.2, 13.0, 14.0, 9.6},
            {3.0, 2.4, 1.9, 13.0, 8.2, 9.6},
            {4.8, 6.75, 0.95, 11.2, 7.85, 2.05},
            {1.25, 8.05, 2.0, 14.75, 8.55, 14.75},
            {1.4, 14.0, 2.2, 14.6, 15.4, 14.5},
            {3.6, 2.9, 14.4, 7.2, 5.7, 15.7},
    };

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public AirFryerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static Map<Direction, VoxelShape> buildShapes() {
        final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (final Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (final double[] b : SHAPE_BOXES) {
                shape = Shapes.or(shape, rotateBox(b, facing));
            }
            map.put(facing, shape);
        }
        return map;
    }

    private static VoxelShape rotateBox(double[] b, Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
            default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        };
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(AirFryerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    private static AirFryerControls.Zone zoneOf(BlockState state, BlockPos pos, BlockHitResult hit) {
        return AirFryerControls.zoneAt(state.getValue(FACING), pos, hit.getLocation());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        final InteractionResult result = operate(state, level, pos, player, stack, hit);
        if (result == InteractionResult.PASS) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return operate(state, level, pos, player, ItemStack.EMPTY, hit);
    }

    private InteractionResult operate(BlockState state, Level level, BlockPos pos, Player player,
                                      ItemStack stack, BlockHitResult hit) {
        final AirFryerControls.Zone zone = zoneOf(state, pos, hit);
        if (zone == null) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof AirFryerBE fryer)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return switch (zone) {
            case POWER -> {
                fryer.setOn(!fryer.isOn());
                play(level, pos, SoundEvents.LEVER_CLICK, 0.4f, fryer.isOn() ? 0.9f : 0.7f);
                if (fryer.isOn()) {
                    announceIfReady(fryer, player);
                }
                yield InteractionResult.CONSUME;
            }
            case START -> pressStart(fryer, level, pos, player);
            case DRAWER -> useDrawer(fryer, level, pos, state, player, stack);
        };
    }

    private void announceIfReady(AirFryerBE fryer, Player player) {
        if (!fryer.isRunning() && fryer.checkStartable() == AirFryerBE.Refusal.NONE) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.air_fryer.ready"), true);
        }
    }

    private InteractionResult pressStart(AirFryerBE fryer, Level level, BlockPos pos, Player player) {
        fryer.markPressed();
        if (fryer.isRunning()) {
            fryer.stop();
            play(level, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, 0.5f, 0.8f);
            player.displayClientMessage(
                    Component.translatable("message.create_stats.air_fryer.cancelled"), true);
            return InteractionResult.CONSUME;
        }
        final AirFryerBE.Refusal refusal = fryer.start();
        if (refusal == AirFryerBE.Refusal.NONE) {
            play(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.5f, 1.4f);
            player.displayClientMessage(
                    Component.translatable("message.create_stats.air_fryer.started"), true);
            return InteractionResult.CONSUME;
        }
        play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
        final String key = switch (refusal) {
            case OFF -> "off";
            case EMPTY -> "empty";
            case NO_RECIPE -> "no_recipe";
            case NO_POWER -> "no_power";
            default -> null;
        };
        if (key != null) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.air_fryer." + key), true);
        }
        return InteractionResult.CONSUME;
    }

    private InteractionResult useDrawer(AirFryerBE fryer, Level level, BlockPos pos, BlockState state,
                                        Player player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            final List<ItemStack> out = fryer.unload();
            if (!out.isEmpty()) {
                giveToPlayer(player, out);
                play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.2f);
                return InteractionResult.CONSUME;
            }
        } else if (!fryer.cookedStack().isEmpty()) {
            final List<ItemStack> out = fryer.unloadCooked();
            if (!out.isEmpty()) {
                giveToPlayer(player, out);
                play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.2f);
                return InteractionResult.CONSUME;
            }
        }
        if (!fryer.isDrawerOpen()) {
            fryer.setDrawerOpen(true);
            play(level, pos, SoundEvents.BARREL_OPEN, 0.5f, 1.35f);
            return InteractionResult.CONSUME;
        }
        if (!stack.isEmpty()) {
            final AirFryerBE.Refusal why = fryer.checkLoad(stack);
            if (why == AirFryerBE.Refusal.NONE) {
                final int taken = fryer.load(stack, stack.getCount());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(taken);
                }
                play(level, pos, SoundEvents.COMPOSTER_FILL, 0.6f, 1.2f);
                announceIfReady(fryer, player);
                return InteractionResult.CONSUME;
            }

            if (why != AirFryerBE.Refusal.NO_RECIPE || fryer.basketEmpty()) {
                play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
                final String key = switch (why) {
                    case BASKET_BUSY -> "basket_busy";
                    case WRONG_ITEM -> "wrong_item";
                    case FULL -> "full";
                    default -> "no_recipe";
                };
                player.displayClientMessage(
                        Component.translatable("message.create_stats.air_fryer." + key), true);
                return InteractionResult.CONSUME;
            }
        }
        shut(fryer, level, pos);
        announceIfReady(fryer, player);
        return InteractionResult.CONSUME;
    }

    private static void giveToPlayer(Player player, List<ItemStack> stacks) {
        for (final ItemStack dropped : stacks) {
            player.getInventory().placeItemBackInInventory(dropped);
        }
    }

    private void shut(AirFryerBE fryer, Level level, BlockPos pos) {
        fryer.setDrawerOpen(false);
        play(level, pos, SoundEvents.BARREL_CLOSE, 0.5f, 1.35f);
    }

    public static void playDing(Level level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.AIR_FRYER_DING.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AirFryerBlock)) {
            return;
        }
        final Vec3 vent = fromAuthoredFrame(state, pos, 8.0, 15.5, 9.0);
        server.sendParticles(ParticleTypes.CLOUD, vent.x, vent.y, vent.z, 8, 0.10, 0.02, 0.10, 0.02);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static Vec3 fromAuthoredFrame(BlockState state, BlockPos pos, double px, double py, double pz) {
        final Vec3 local = fromAuthoredFrameLocal(state, px, py, pz);
        return new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z);
    }

    public static Vec3 fromAuthoredFrameLocal(BlockState state, double px, double py, double pz) {
        final double x = px / 16.0;
        final double y = py / 16.0;
        final double z = pz / 16.0;
        return switch (state.getValue(FACING)) {
            case EAST -> new Vec3(1.0 - z, y, x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> new Vec3(x, y, z);
        };
    }

    public static Direction wireFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AirFryerBE fryer) {
            fryer.onRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AirFryerBE fryer) {
            fryer.adoptRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AirFryerBE fryer)) {
            return 0;
        }
        final int count = fryer.cookedStack().getCount();
        if (count <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(15,
                Math.round(count * 15.0f / AirFryerBE.BASKET_CAPACITY)));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.air_fryer",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.air_fryer_draw",
                                AirFryerBE.DRAW_COOK, AirFryerBE.DRAW_IDLE),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.air_fryer_basket",
                                AirFryerBE.BASKET_CAPACITY)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof AirFryerBE fryer) {
            fryer.dropWiresAndUnlinkAll();
            fryer.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.AIR_FRYER.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.AIR_FRYER.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> ((AirFryerBE) be).clientTick();
        }
        return (lvl, pos, st, be) -> ((AirFryerBE) be).serverTick();
    }
}
