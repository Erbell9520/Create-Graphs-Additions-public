package com.createstats.block;

import com.createstats.blockentity.BreadSlicerBE;
import com.createstats.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

public class BreadSlicerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static final int LIT_LEVEL = 3;

    private static final double[][] SHAPE_BOXES = {
            {1.90, 0.00, 2.20, 13.10, 0.90, 13.20},
            {1.70, 0.90, 2.10, 14.30, 2.00, 13.90},
            {1.00, 2.00, 1.40, 15.00, 7.60, 14.60},
            {2.30, 2.40, 0.96, 13.70, 3.95, 1.40},
            {1.70, 4.30, 1.06, 14.30, 7.20, 1.40},
            {0.85, 7.30, 1.25, 15.15, 8.15, 14.75},
            {0.85, 8.15, 11.82, 15.15, 14.30, 14.87},
            {0.85, 8.15, 1.25, 15.15, 14.30, 12.10},
    };

    private static final int HOOD_BOX = SHAPE_BOXES.length - 1;

    private static final Map<Direction, VoxelShape> SHUT = buildShapes(true);
    private static final Map<Direction, VoxelShape> OPEN = buildShapes(false);

    public BreadSlicerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    private static Map<Direction, VoxelShape> buildShapes(boolean withHood) {
        final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (final Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int i = 0; i < SHAPE_BOXES.length; i++) {
                if (i == HOOD_BOX && !withHood) {
                    continue;
                }
                shape = Shapes.or(shape, rotateBox(SHAPE_BOXES[i], facing));
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
        return simpleCodec(BreadSlicerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        final boolean open = level.getBlockEntity(pos) instanceof BreadSlicerBE slicer
                && slicer.isHoodOpen();
        return (open ? OPEN : SHUT).get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHUT.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        final InteractionResult result = operate(state, level, pos, player, stack, hit);
        if (result == InteractionResult.PASS) {
            return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return operate(state, level, pos, player, ItemStack.EMPTY, hit);
    }

    private InteractionResult operate(BlockState state, Level level, BlockPos pos, Player player,
                                      ItemStack stack, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BreadSlicerBE slicer)) {
            return InteractionResult.PASS;
        }
        final BreadSlicerControls.Zone zone = BreadSlicerControls.zoneAt(
                state.getValue(FACING), pos, hit.getLocation(), slicer.isHoodOpen());
        if (zone == null) {
            return InteractionResult.PASS;
        }

        if (zone == BreadSlicerControls.Zone.DECK && declinesDeck(slicer, stack)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return switch (zone) {
            case START -> pressStart(slicer, level, pos, player);
            case STOP -> pressStop(slicer, level, pos, player);
            case LEVER -> nudgeLever(slicer, player);
            case DRAWER -> pullDrawer(level, pos, player);
            case DECK -> useDeck(slicer, level, pos, player, stack);
            case HOOD -> toggleHood(slicer, player);
        };
    }

    private InteractionResult pressStart(BreadSlicerBE slicer, Level level, BlockPos pos,
                                         Player player) {
        final BreadSlicerBE.Refusal why = slicer.start();
        if (why == BreadSlicerBE.Refusal.NONE) {
            return InteractionResult.CONSUME;
        }
        play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
        say(player, switch (why) {
            case HOOD -> "hood_up";
            case EMPTY -> "empty";
            case FULL -> "full";
            case SHED -> "shed";
            case GATED -> "gated";
            case NO_POWER -> slicer.isWired() ? "charging" : "not_wired";
            default -> "empty";
        });
        return InteractionResult.CONSUME;
    }

    private InteractionResult pressStop(BreadSlicerBE slicer, Level level, BlockPos pos,
                                        Player player) {
        final boolean was = slicer.isRunning();
        slicer.stop();
        if (!was) {
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            say(player, "not_running");
        }
        return InteractionResult.CONSUME;
    }

    private InteractionResult nudgeLever(BreadSlicerBE slicer, Player player) {
        if (!slicer.nudgeLever()) {
            say(player, "running");
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable(
                "message.create_stats.bread_slicer.thickness",
                Component.translatable("create_stats.bread_slicer.notch." + slicer.notch()),
                slicer.strokes(), slicer.draw()), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult pullDrawer(Level level, BlockPos pos, Player player) {
        play(level, pos, SoundEvents.BARREL_OPEN, 0.35f, 1.7f);
        play(level, pos, SoundEvents.BARREL_CLOSE, 0.30f, 1.8f);
        say(player, "crumbs");
        return InteractionResult.CONSUME;
    }

    private InteractionResult useDeck(BreadSlicerBE slicer, Level level, BlockPos pos, Player player,
                                      ItemStack stack) {
        if (!stack.isEmpty() && slicer.sliceable(stack) && slicer.loaf().isEmpty()) {
            final ItemStack left = slicer.loadFromHand(stack);
            if (left.getCount() != stack.getCount()) {
                if (!player.getAbilities().instabuild) {
                    stack.setCount(left.getCount());
                }
                play(level, pos, SoundEvents.COMPOSTER_FILL, 0.5f, 1.35f);
                return InteractionResult.CONSUME;
            }
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            say(player, slicer.isRunning() ? "running" : "hood_up");
            return InteractionResult.CONSUME;
        }
        final ItemStack slices = slicer.collect();
        if (!slices.isEmpty()) {
            player.getInventory().placeItemBackInInventory(slices);
            play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.3f);
            return InteractionResult.CONSUME;
        }
        if (slicer.isRunning()) {
            play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
            say(player, "running");
            return InteractionResult.CONSUME;
        }
        final ItemStack loaf = slicer.unloadDeck();
        if (!loaf.isEmpty()) {
            player.getInventory().placeItemBackInInventory(loaf);
            play(level, pos, SoundEvents.ITEM_PICKUP, 0.5f, 1.2f);
            return InteractionResult.CONSUME;
        }
        play(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f);
        say(player, "deck_empty");
        return InteractionResult.CONSUME;
    }

    private static boolean declinesDeck(BreadSlicerBE slicer, ItemStack stack) {
        return !stack.isEmpty() && !slicer.sliceable(stack)
                && slicer.output().isEmpty() && slicer.loaf().isEmpty();
    }

    private InteractionResult toggleHood(BreadSlicerBE slicer, Player player) {
        final boolean open = !slicer.isHoodOpen();
        slicer.setHoodOpen(open);
        if (open && slicer.isRunning()) {
            say(player, "held_hood");
        }
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(
                Component.translatable("message.create_stats.bread_slicer." + key), true);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            net.minecraft.util.RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(3) != 0) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof BreadSlicerBE slicer) || !slicer.isCutting()) {
            return;
        }
        final Vec3 slot = fromAuthoredFrame(state, pos, 7.40, 8.10,
                5.00 + random.nextDouble() * 3.85);
        level.addParticle(ParticleTypes.CRIT, slot.x, slot.y, slot.z,
                (random.nextDouble() - 0.5) * 0.02, 0.01, 0.0);
    }

    public static Vec3 fromAuthoredFrame(BlockState state, BlockPos pos, double px, double py,
                                         double pz) {
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BreadSlicerBE slicer) {
            slicer.onRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BreadSlicerBE slicer) {
            slicer.adoptRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BreadSlicerBE slicer
                ? slicer.comparatorSignal() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.bread_slicer",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.bread_slicer_draw",
                                BreadSlicerBE.DRAW_BASE,
                                BreadSlicerBE.DRAW_BASE << (BreadSlicerBE.STROKES.length - 1),
                                BreadSlicerBE.DRAW_IDLE),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.bread_slicer_notches",
                                BreadSlicerBE.STROKES[0],
                                BreadSlicerBE.STROKES[BreadSlicerBE.STROKES.length - 1]),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.bread_slicer_pass",
                                BreadSlicerBE.PASS_TICKS / 20)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof BreadSlicerBE slicer) {
            slicer.dropWiresAndUnlinkAll();
            for (final ItemStack dropped : slicer.dropContents()) {
                Block.popResource(level, pos, dropped);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.BREAD_SLICER.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.BREAD_SLICER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((BreadSlicerBE) be).serverTick();
    }
}
