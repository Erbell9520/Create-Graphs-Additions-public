package com.createstats.block;

import com.createstats.blockentity.BlastShutterBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModSounds;
import com.createstats.security.ShutterBank;
import com.createstats.sound.DoorClip;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.createmod.catnip.placement.IPlacementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BlastShutterBlock extends HorizontalDirectionalBlock implements EntityBlock,
        com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final MapCodec<BlastShutterBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("tier").forGetter(block -> block.tier.name()),
                    propertiesCodec()
            ).apply(instance, (key, properties) ->
                    new BlastShutterBlock(ShutterTier.byName(key), properties)));

    public static final EnumProperty<ShutterSegment> HALF =
            EnumProperty.create("half", ShutterSegment.class);

    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public static final EnumProperty<ShutterMount> MOUNT =
            EnumProperty.create("mount", ShutterMount.class);

    public static final float LEAF_Z0 = 6.0f;
    public static final float LEAF_Z1 = 10.0f;

    public static final float CASE_Z0 = 5.0f;
    public static final float CASE_Z1 = 11.0f;
    public static final float CASE_H = 4.0f;

    public static final int MIN_HEIGHT = 2;

    public static final int MAX_HEIGHT = 8;

    private final ShutterTier tier;

    private static final Map<ShutterMount, Map<Direction, VoxelShape>> CASE_UPPER =
            new EnumMap<>(ShutterMount.class);
    private static final Map<ShutterMount, Map<Direction, VoxelShape>> CASE_LOWER =
            new EnumMap<>(ShutterMount.class);
    private static final Map<ShutterMount, Map<Direction, VoxelShape>> SEALED_UPPER =
            new EnumMap<>(ShutterMount.class);
    private static final Map<ShutterMount, Map<Direction, VoxelShape>> SEALED_LOWER =
            new EnumMap<>(ShutterMount.class);
    private static final Map<ShutterMount, Map<Direction, VoxelShape>> SEALED_MIDDLE =
            new EnumMap<>(ShutterMount.class);

    static {
        for (ShutterMount mount : ShutterMount.values()) {
            final double dz = mount.depth();
            final double caseZ0 = CASE_Z0 + dz;
            final double caseZ1 = CASE_Z1 + dz;
            final double leafZ0 = LEAF_Z0 + dz;
            final double leafZ1 = LEAF_Z1 + dz;
            final Map<Direction, VoxelShape> upperCases = new EnumMap<>(Direction.class);
            final Map<Direction, VoxelShape> lowerCases = new EnumMap<>(Direction.class);
            final Map<Direction, VoxelShape> sealedUppers = new EnumMap<>(Direction.class);
            final Map<Direction, VoxelShape> sealedLowers = new EnumMap<>(Direction.class);
            final Map<Direction, VoxelShape> sealedMiddles = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                final VoxelShape upperCase = rotate(new double[]{
                        0, 16 - CASE_H, caseZ0, 16, 16, caseZ1}, facing);
                final VoxelShape lowerCase = rotate(new double[]{
                        0, 0, caseZ0, 16, CASE_H, caseZ1}, facing);
                upperCases.put(facing, upperCase);
                lowerCases.put(facing, lowerCase);

                sealedUppers.put(facing, Shapes.or(upperCase,
                        rotate(new double[]{0, 0, leafZ0, 16, 16 - CASE_H, leafZ1}, facing)));
                sealedLowers.put(facing, Shapes.or(lowerCase,
                        rotate(new double[]{0, CASE_H, leafZ0, 16, 16, leafZ1}, facing)));
                sealedMiddles.put(facing,
                        rotate(new double[]{0, 0, leafZ0, 16, 16, leafZ1}, facing));
            }
            CASE_UPPER.put(mount, upperCases);
            CASE_LOWER.put(mount, lowerCases);
            SEALED_UPPER.put(mount, sealedUppers);
            SEALED_LOWER.put(mount, sealedLowers);
            SEALED_MIDDLE.put(mount, sealedMiddles);
        }
    }

    private static VoxelShape rotate(double[] b, Direction facing) {
        final double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    private static final int PLACEMENT_HELPER_ID =
            net.createmod.catnip.placement.PlacementHelpers.register(new BlastShutterPlacementHelper());

    public BlastShutterBlock(ShutterTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, ShutterSegment.LOWER)
                .setValue(MOUNT, ShutterMount.CENTER)
                .setValue(SEALED, false));
    }

    public ShutterTier tier() {
        return this.tier;
    }

    @Nullable
    public static ShutterTier tierOf(BlockState state) {
        return state.getBlock() instanceof BlastShutterBlock shutter ? shutter.tier() : null;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, MOUNT, SEALED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext ctx) {
        final Direction facing = state.getValue(FACING);
        final ShutterSegment segment = state.getValue(HALF);
        final ShutterMount mount = state.getValue(MOUNT);
        if (state.getValue(SEALED)) {
            return switch (segment) {
                case LOWER -> SEALED_LOWER.get(mount).get(facing);
                case MIDDLE -> SEALED_MIDDLE.get(mount).get(facing);
                case UPPER -> SEALED_UPPER.get(mount).get(facing);
            };
        }
        return switch (segment) {
            case LOWER -> CASE_LOWER.get(mount).get(facing);
            case UPPER -> CASE_UPPER.get(mount).get(facing);

            case MIDDLE -> Shapes.empty();
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return !state.getValue(SEALED);
    }

    @Nullable
    public static BlockPos bottomOf(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BlastShutterBlock block) || !state.hasProperty(HALF)) {
            return null;
        }
        BlockPos at = pos;
        BlockState here = state;
        for (int step = 0; step < MAX_HEIGHT; step++) {
            if (here.getValue(HALF) == ShutterSegment.LOWER) {
                return at.immutable();
            }
            final BlockPos below = at.below();
            final BlockState next = level.getBlockState(below);
            if (!next.is(block)) {
                return null;
            }
            at = below;
            here = next;
        }
        return null;
    }

    public static int heightOf(BlockGetter level, BlockPos bottom) {
        final BlockState state = level.getBlockState(bottom);
        if (!(state.getBlock() instanceof BlastShutterBlock block)
                || state.getValue(HALF) != ShutterSegment.LOWER) {
            return 0;
        }
        int height = 1;
        for (int step = 1; step < MAX_HEIGHT; step++) {
            final BlockState above = level.getBlockState(bottom.above(step));
            if (!above.is(block) || above.getValue(HALF) == ShutterSegment.LOWER) {
                break;
            }
            height = step + 1;
            if (above.getValue(HALF) == ShutterSegment.UPPER) {
                break;
            }
        }
        return height;
    }

    public static int heightAt(BlockGetter level, BlockPos pos, BlockState state) {
        final BlockPos bottom = bottomOf(level, pos, state);
        if (bottom == null) {
            return MIN_HEIGHT;
        }
        return Math.max(MIN_HEIGHT, heightOf(level, bottom));
    }

    public static List<BlockPos> columnFrom(BlockGetter level, BlockPos bottom) {
        final int height = heightOf(level, bottom);
        final List<BlockPos> out = new ArrayList<>(Math.max(1, height));
        for (int step = 0; step < height; step++) {
            out.add(bottom.above(step));
        }
        return out;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockPos pos = context.getClickedPos();
        final BlockState below = context.getLevel().getBlockState(pos.below());
        if (below.is(this) && below.getValue(HALF) == ShutterSegment.UPPER) {
            return below;
        }
        final BlockState above = context.getLevel().getBlockState(pos.above());
        if (above.is(this) && above.getValue(HALF) == ShutterSegment.LOWER) {
            return above;
        }
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !above.canBeReplaced(context)) {
            return null;
        }
        final Direction face = context.getClickedFace();
        final BlockState behind = context.getLevel().getBlockState(pos.relative(face.getOpposite()));

        if (behind.getBlock() instanceof BlastShutterBlock && behind.hasProperty(MOUNT)) {
            return defaultBlockState()
                    .setValue(FACING, behind.getValue(FACING))
                    .setValue(HALF, ShutterSegment.LOWER)
                    .setValue(MOUNT, behind.getValue(MOUNT))
                    .setValue(SEALED, false);
        }

        if (face.getAxis().isHorizontal() && !behind.isAir() && !behind.canBeReplaced(context)) {
            return defaultBlockState()
                    .setValue(FACING, face)
                    .setValue(HALF, ShutterSegment.LOWER)
                    .setValue(MOUNT, ShutterMount.forClickedFace(face))
                    .setValue(SEALED, false);
        }

        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, ShutterSegment.LOWER)
                .setValue(MOUNT, ShutterMount.CENTER)
                .setValue(SEALED, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, ShutterSegment.UPPER), Block.UPDATE_ALL);
        if (level.isClientSide()) {
            return;
        }

        final BlastShutterBE placed = BlastShutterBE.at(level, pos);
        if (placed == null) {
            return;
        }
        placed.setColumnHeight(MIN_HEIGHT);
        for (BlockPos member : ShutterBank.membersOf(level, pos, level.getBlockState(pos))) {
            if (member.equals(pos)) {
                continue;
            }
            final BlastShutterBE other = BlastShutterBE.at(level, member);
            if (other != null && other.isSealed()) {
                seal(level, pos, true);
                return;
            }
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) != ShutterSegment.LOWER) {
            final BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) != ShutterSegment.UPPER;
        }

        final BlockState above = level.getBlockState(pos.above());
        return !(above.is(this) && above.getValue(HALF) == ShutterSegment.LOWER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        final ShutterSegment segment = state.getValue(HALF);
        if (direction == Direction.UP && segment != ShutterSegment.UPPER) {
            return neighborState.is(this) && neighborState.getValue(HALF) != ShutterSegment.LOWER
                    ? state : Blocks.AIR.defaultBlockState();
        }
        if (direction == Direction.DOWN && segment != ShutterSegment.LOWER) {
            return neighborState.is(this) && neighborState.getValue(HALF) != ShutterSegment.UPPER
                    ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()
                && state.getValue(HALF) != ShutterSegment.LOWER) {
            final BlockPos bottom = bottomOf(level, pos, state);
            if (bottom != null) {
                final BlockState lower = level.getBlockState(bottom);
                level.setBlock(bottom, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
                level.levelEvent(player, 2001, bottom, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state,
                                    net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        if (state.getValue(HALF) != ShutterSegment.LOWER) {
            return List.of();
        }
        final List<ItemStack> base = super.getDrops(state, params);
        final int paid = paidFor(params);
        if (paid <= 1 || base.isEmpty()) {
            return base;
        }
        final List<ItemStack> out = new ArrayList<>(base.size());
        for (ItemStack stack : base) {
            final ItemStack copy = stack.copy();
            copy.setCount(copy.getCount() * paid);
            out.add(copy);
        }
        return out;
    }

    private static int paidFor(net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        final BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        final int height = be instanceof BlastShutterBE unit ? unit.columnHeight() : MIN_HEIGHT;
        return Math.max(1, Math.min(MAX_HEIGHT, height) - 1);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && state.getValue(HALF) == ShutterSegment.LOWER
                && level.getBlockEntity(pos) instanceof BlastShutterBE shutter) {
            com.createstats.power.BoreLinks.severAll(level, shutter);
            shutter.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    public static Direction aimedEnd(BlockPos pos, BlockHitResult hit) {
        final List<Direction> ordered = IPlacementHelper.orderedByDistanceOnlyAxis(pos,
                hit.getLocation(), Direction.Axis.Y);
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    @Nullable
    public static BlockPos growthTarget(BlockGetter level, BlockPos pos, BlockState state,
                                        Direction dir) {
        if (dir.getAxis() != Direction.Axis.Y) {
            return null;
        }
        final BlockPos bottom = bottomOf(level, pos, state);
        if (bottom == null) {
            return null;
        }
        final int height = heightOf(level, bottom);
        if (height < MIN_HEIGHT || height >= MAX_HEIGHT) {
            return null;
        }
        final BlockPos target = dir == Direction.UP ? bottom.above(height) : bottom.below();
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
            return null;
        }
        return level.getBlockState(target).canBeReplaced() ? target : null;
    }

    public static boolean grow(Level level, BlockPos pos, boolean upward) {
        if (level.isClientSide()) {
            return false;
        }
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlastShutterBlock block)) {
            return false;
        }
        final BlockPos target = growthTarget(level, pos, state, upward ? Direction.UP : Direction.DOWN);
        if (target == null) {
            return false;
        }
        final BlockPos bottom = bottomOf(level, pos, state);
        if (bottom == null) {
            return false;
        }
        final int height = heightOf(level, bottom);
        final BlockState bottomState = level.getBlockState(bottom);
        final Direction facing = bottomState.getValue(FACING);
        final ShutterMount mount = bottomState.getValue(MOUNT);
        final boolean sealed = bottomState.getValue(SEALED);
        final int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

        if (upward) {
            final BlockPos oldTop = bottom.above(height - 1);
            level.setBlock(oldTop,
                    block.segmentState(facing, ShutterSegment.MIDDLE, mount, sealed), flags);
            level.setBlock(target,
                    block.segmentState(facing, ShutterSegment.UPPER, mount, sealed), flags);
            final BlastShutterBE unit = BlastShutterBE.at(level, bottom);
            if (unit != null) {
                unit.setColumnHeight(height + 1);
            }
        } else {
            final BlastShutterBE old = BlastShutterBE.at(level, bottom);
            level.setBlock(bottom,
                    block.segmentState(facing, ShutterSegment.MIDDLE, mount, sealed), flags);

            level.removeBlockEntity(bottom);
            level.setBlock(target,
                    block.segmentState(facing, ShutterSegment.LOWER, mount, sealed), flags);
            final BlastShutterBE moved = BlastShutterBE.at(level, target);
            if (moved != null) {
                if (old != null) {
                    moved.adoptFrom(old);
                }
                moved.setColumnHeight(height + 1);
            }
        }

        level.playSound(null, target, ModSounds.doorClip(DoorClip.SEAT), SoundSource.BLOCKS,
                block.tier.gain() * 0.7f, block.tier.pitch() * 1.05f);
        return true;
    }

    private BlockState segmentState(Direction facing, ShutterSegment segment, ShutterMount mount,
                                    boolean sealed) {
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HALF, segment)
                .setValue(MOUNT, mount)
                .setValue(SEALED, sealed);
    }

    public static void driveBank(Level level, BlockPos at, boolean seal) {
        if (level.isClientSide()) {
            return;
        }
        final BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof BlastShutterBlock block)) {
            return;
        }
        final List<BlockPos> members = ShutterBank.membersOf(level, at, state);
        if (members.isEmpty()) {
            return;
        }
        boolean anyMoving = false;
        for (BlockPos member : members) {
            final BlastShutterBE unit = BlastShutterBE.at(level, member);
            if (unit == null) {
                continue;
            }
            if (unit.isSealed() == seal) {
                continue;
            }
            anyMoving = true;
            if (!unit.canAffordCycle()) {
                return;
            }
        }
        if (!anyMoving) {
            return;
        }
        for (BlockPos member : members) {
            final BlastShutterBE unit = BlastShutterBE.at(level, member);
            if (unit != null && unit.isSealed() != seal) {
                unit.spendCycle();
            }
        }
        for (BlockPos member : members) {
            block.move(level, member, seal);
        }
        final BlockPos anchor = members.get(0);
        final ShutterTier tier = block.tier();
        level.playSound(null, anchor, ModSounds.doorClip(seal ? tier.depart() : DoorClip.LATCH),
                SoundSource.BLOCKS, tier.gain() * (members.size() > 1 ? 1.15f : 1.0f), tier.pitch());
        level.gameEvent(null, seal ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE
                : net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN, anchor);
    }

    private void move(Level level, BlockPos lower, boolean seal) {
        final BlastShutterBE unit = BlastShutterBE.at(level, lower);
        if (unit == null || unit.isSealed() == seal) {
            return;
        }
        unit.setSealed(seal);
        if (!seal) {
            setSealedColumn(level, lower, false);
        } else {
            level.scheduleTick(lower, this, Math.max(1, unit.remainingTicks()));
        }
    }

    public static void settleCollision(Level level, BlockPos lower, boolean sealed) {
        if (level.getBlockState(lower).getBlock() instanceof BlastShutterBlock block) {
            block.setSealedColumn(level, lower, sealed);
        }
    }

    private void setSealedColumn(Level level, BlockPos lower, boolean sealed) {
        for (BlockPos at : columnFrom(level, lower)) {
            final BlockState state = level.getBlockState(at);
            if (state.is(this) && state.getValue(SEALED) != sealed) {
                level.setBlock(at, state.setValue(SEALED, sealed), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        final BlastShutterBE unit = BlastShutterBE.at(level, pos);
        if (unit != null && unit.isSealed() && unit.settled()) {
            setSealedColumn(level, pos, true);

            if (ShutterBank.isAnchor(level, pos, level.getBlockState(pos))) {
                level.playSound(null, pos, ModSounds.doorClip(DoorClip.SEAT), SoundSource.BLOCKS,
                        this.tier.gain() * 0.8f, this.tier.pitch());
            }
        }
    }

    public static void seal(Level level, BlockPos at, boolean sealed) {
        driveBank(level, at, sealed);
    }

    @Nullable
    public static BlastShutterBE unitAt(BlockGetter level, BlockPos pos, BlockState state) {
        final BlockPos bottom = bottomOf(level, pos, state);
        if (bottom == null) {
            return null;
        }
        return level.getBlockEntity(bottom) instanceof BlastShutterBE unit ? unit : null;
    }

    @Nullable
    public static com.createstats.power.BoreLinks.Host boreHostAt(LevelReader level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlastShutterBlock)
                || state.getValue(HALF) == ShutterSegment.LOWER) {
            return null;
        }

        final BlockPos bottom = bottomOf(level, pos, state);
        if (bottom == null) {
            return null;
        }
        return level.getBlockEntity(bottom) instanceof BlastShutterBE unit ? unit : null;
    }

    // shares growthTarget with the ghost so they can't disagree
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (com.simibubi.create.AllItems.WRENCH.isIn(stack) || isWiringTool(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() != this) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player == null || player.isShiftKeyDown()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        final Direction dir = aimedEnd(pos, hit);
        if (dir != null && growthTarget(level, pos, state, dir) != null) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (grow(level, pos, dir == Direction.UP)) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        if (!level.isClientSide()) {
            final boolean atCap = heightAt(level, pos, state) >= MAX_HEIGHT;
            player.displayClientMessage(Component.translatable(atCap
                    ? "message.create_stats.blast_shutter.too_tall"
                    : "message.create_stats.blast_shutter.no_room", MAX_HEIGHT), true);
        }
        return ItemInteractionResult.SUCCESS;
    }

    private static boolean isWiringTool(ItemStack stack) {
        return stack.getItem() instanceof com.createstats.item.ApplianceCordItem
                || stack.getItem() instanceof com.createstats.item.CableBoreItem;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("message.create_stats.blast_shutter.no_hand_release"), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos bottom = bottomOf(level, context.getClickedPos(), state);
        if (bottom == null) {
            return InteractionResult.PASS;
        }
        final ShutterMount picked = state.getValue(MOUNT).next();
        setMountColumn(level, bottom, picked);
        say(context.getPlayer(), "message.create_stats.blast_shutter.mount", picked);
        com.simibubi.create.content.equipment.wrench.IWrenchable
                .playRotateSound(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos bottom = bottomOf(level, context.getClickedPos(), state);
        if (bottom == null) {
            return InteractionResult.PASS;
        }
        final ShutterMount mount = state.getValue(MOUNT);
        int changed = 0;
        for (BlockPos member : ShutterBank.membersOf(level, bottom, level.getBlockState(bottom))) {
            final BlockState memberState = level.getBlockState(member);
            if (memberState.is(this) && memberState.getValue(MOUNT) != mount) {
                setMountColumn(level, member, mount);
                changed++;
            }
        }
        final Player player = context.getPlayer();
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.blast_shutter.mount_bank",
                    Component.translatable("shuttermount.create_stats." + mount.getSerializedName()),
                    changed), true);
        }
        com.simibubi.create.content.equipment.wrench.IWrenchable
                .playRotateSound(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    private void setMountColumn(Level level, BlockPos bottom, ShutterMount mount) {
        for (BlockPos at : columnFrom(level, bottom)) {
            final BlockState state = level.getBlockState(at);
            if (state.is(this) && state.getValue(MOUNT) != mount) {
                level.setBlock(at, state.setValue(MOUNT, mount), Block.UPDATE_ALL);
            }
        }
    }

    private static void say(@Nullable Player player, String key, ShutterMount mount) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key,
                    Component.translatable("shuttermount.create_stats." + mount.getSerializedName())),
                    true);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == ShutterSegment.LOWER
                ? ModBlockEntities.BLAST_SHUTTER.create(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.BLAST_SHUTTER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((BlastShutterBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats." + this.tier.blockId(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.shutter_draw",
                                com.createstats.power.FeRates.perTick(BlastShutterBE.drawPerTick()),
                                com.createstats.power.FeRates.amount(BlastShutterBE.cycleCost())),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.shutter_travel",
                                Component.literal(String.format("%.1f", this.tier.travelTicks() / 20.0f))),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.shutter_height",
                                Component.literal(MIN_HEIGHT + "–" + MAX_HEIGHT))));
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }
}
