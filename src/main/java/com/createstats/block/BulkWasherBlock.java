package com.createstats.block;

import com.createstats.blockentity.BulkWasherBE;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

public class BulkWasherBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final double[][] SHAPE_BOXES = {
            {1.2, 0.0, 1.6, 14.8, 1.2, 14.8},
            {0.4, 1.2, 1.0, 15.6, 14.9, 15.6},
            {0.25, 14.9, 0.85, 15.75, 16.0, 15.75},
            {11.6, 11.9, 1.15, 15.0, 14.5, 1.6},
            {3.0, 1.3, 0.0, 13.0, 11.3, 1.0},
            {0.1, 4.2, 4.2, 15.9, 10.8, 10.8},
            {3.4, 2.6, 15.55, 12.4, 5.8, 16.0},
    };

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_MODE = 1;
    public static final int BUTTON_START = 2;

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public BulkWasherBlock(Properties properties) {
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
        return simpleCodec(BulkWasherBlock::new);
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
    private static BulkWasherControls.Zone zoneOf(BlockState state, BlockPos pos, BlockHitResult hit) {
        return BulkWasherControls.zoneAt(state.getValue(FACING), pos, hit.getLocation());
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
        if (!(level.getBlockEntity(pos) instanceof BulkWasherBE washer)) {
            return InteractionResult.PASS;
        }

        if (stack.is(Items.WATER_BUCKET)) {
            return fillFromBucket(washer, level, pos, player, stack);
        }
        final BulkWasherControls.Zone zone = zoneOf(state, pos, hit);
        if (zone == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return switch (zone) {
            case POWER -> {
                washer.markPressed(BUTTON_POWER);
                washer.setOn(!washer.isOn());
                play(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.45f,
                        washer.isOn() ? 1.2f : 0.8f);
                announceState(washer, player);
                yield InteractionResult.CONSUME;
            }
            case MODE -> pressMode(washer, level, pos, player);
            case START -> pressStart(washer, level, pos, player);
            case DOOR -> useDoor(washer, level, pos, player, stack);
        };
    }

    private InteractionResult fillFromBucket(BulkWasherBE washer, Level level, BlockPos pos,
                                             Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (washer.addWater(1_000, false) < 1_000) {
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            player.displayClientMessage(
                    Component.translatable("message.create_stats.bulk_washer.tank_full"), true);
            return InteractionResult.CONSUME;
        }
        washer.addWater(1_000, true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        }
        play(level, pos, SoundEvents.BUCKET_EMPTY, 0.6f, 1.0f);
        return InteractionResult.CONSUME;
    }

    private InteractionResult pressMode(BulkWasherBE washer, Level level, BlockPos pos, Player player) {
        washer.markPressed(BUTTON_MODE);
        final BulkWasherBE.WashMode mode = washer.cycleMode();
        play(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.45f, 1.1f + 0.15f * mode.ordinal());
        player.displayClientMessage(Component.translatable(
                "message.create_stats.bulk_washer.mode",
                Component.translatable("mode.create_stats.bulk_washer." + mode.key()),
                String.format(java.util.Locale.ROOT, "%.1f", mode.itemsPerSecond()),
                mode.drawFe), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult pressStart(BulkWasherBE washer, Level level, BlockPos pos, Player player) {
        washer.markPressed(BUTTON_START);
        final boolean started = washer.toggleStart();
        play(level, pos, started ? SoundEvents.STONE_BUTTON_CLICK_ON
                : SoundEvents.STONE_BUTTON_CLICK_OFF, 0.5f, started ? 1.4f : 0.9f);
        if (!started) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.bulk_washer.held"), true);
        } else {
            announceState(washer, player);
        }
        return InteractionResult.CONSUME;
    }

    private InteractionResult useDoor(BulkWasherBE washer, Level level, BlockPos pos, Player player,
                                      ItemStack stack) {
        if (player.isShiftKeyDown()) {
            final List<ItemStack> out = washer.unloadAll();
            if (!out.isEmpty()) {
                giveToPlayer(player, out);
                play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.1f);
                return InteractionResult.CONSUME;
            }
        } else if (!washer.washedStacks().isEmpty()) {
            final List<ItemStack> out = washer.unloadWashed();
            if (!out.isEmpty()) {
                giveToPlayer(player, out);
                play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.2f);
                return InteractionResult.CONSUME;
            }
        }
        if (!washer.isDoorOpen()) {
            if (!washer.canOpenDoor()) {
                play(level, pos, SoundEvents.IRON_DOOR_CLOSE, 0.35f, 1.8f);
                player.displayClientMessage(
                        Component.translatable("message.create_stats.bulk_washer.door_locked"), true);
                return InteractionResult.CONSUME;
            }
            washer.setDoorOpen(true);
            play(level, pos, SoundEvents.IRON_TRAPDOOR_OPEN, 0.5f, 1.2f);
            return InteractionResult.CONSUME;
        }
        if (!stack.isEmpty()) {
            final BulkWasherBE.Refusal why = washer.checkLoad(stack);
            if (why == BulkWasherBE.Refusal.NONE) {
                final int taken = washer.load(stack, stack.getCount());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(taken);
                }
                play(level, pos, SoundEvents.COMPOSTER_FILL, 0.6f, 1.1f);
                return InteractionResult.CONSUME;
            }

            if (why != BulkWasherBE.Refusal.NO_RECIPE || washer.drumCount() == 0) {
                play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
                player.displayClientMessage(Component.translatable(
                        "message.create_stats.bulk_washer." + why.key()), true);
                return InteractionResult.CONSUME;
            }
        }
        washer.setDoorOpen(false);
        play(level, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5f, 1.2f);
        announceState(washer, player);
        return InteractionResult.CONSUME;
    }

    private void announceState(BulkWasherBE washer, Player player) {
        final BulkWasherBE.Refusal why = washer.checkRun();
        if (why == BulkWasherBE.Refusal.NONE) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.bulk_washer.running"), true);
        } else if (why == BulkWasherBE.Refusal.NOT_STARTED && washer.isOn()) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.bulk_washer.not_started"), true);
        } else if (why == BulkWasherBE.Refusal.NO_WATER || why == BulkWasherBE.Refusal.NO_POWER) {
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.bulk_washer." + why.key()), true);
        }
    }

    private static void giveToPlayer(Player player, List<ItemStack> stacks) {
        for (final ItemStack dropped : stacks) {
            player.getInventory().placeItemBackInInventory(dropped);
        }
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
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

    public static Vec3 fromAuthoredFrame(BlockState state, BlockPos pos, double px, double py, double pz) {
        final Vec3 local = fromAuthoredFrameLocal(state, px, py, pz);
        return new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z);
    }

    public static Direction wireFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BulkWasherBE washer)) {
            return 0;
        }
        final int count = washer.drumCount();
        if (count <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(15, Math.round(count * 15.0f / BulkWasherBE.DRUM_CAPACITY)));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.bulk_washer",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.bulk_washer_draw",
                                BulkWasherBE.WashMode.ECO.drawFe, BulkWasherBE.WashMode.FULL.drawFe,
                                BulkWasherBE.DRAW_IDLE),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.bulk_washer_drum",
                                BulkWasherBE.DRUM_CAPACITY),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.bulk_washer_water",
                                BulkWasherBE.WATER_PER_ITEM, BulkWasherBE.TANK_CAPACITY)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof BulkWasherBE washer) {
            washer.dropWiresAndUnlinkAll();
            washer.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.BULK_WASHER.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.BULK_WASHER.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> ((BulkWasherBE) be).clientTick();
        }
        return (lvl, pos, st, be) -> ((BulkWasherBE) be).serverTick();
    }
}
