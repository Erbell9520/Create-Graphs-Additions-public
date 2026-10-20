package com.createstats.block;

import com.createstats.blockentity.PoweredDoorBE;
import com.createstats.security.Clearance;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import com.createstats.sound.DoorSound;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class PoweredDoorBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable {
    public static final MapCodec<PoweredDoorBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("material")
                            .forGetter(block -> block.material.key()),
                    propertiesCodec()
            ).apply(instance, (key, properties) ->
                    new PoweredDoorBlock(DoorMaterial.byName(key), properties)));

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    // lives here not in the renderer, common reaching into client-only = dedicated server crash on class load
    public static final float LEAF_D = 4.0f;

    private final DoorMaterial material;

    public DoorMaterial material() {
        return this.material;
    }

    public Set<DoorOpening> openings() {
        return this.material.openings();
    }

    public DoorOpening offered(DoorOpening style) {
        return this.material.openings().contains(style) ? style : this.material.defaultOpening();
    }

    public static DoorOpening offeredBy(BlockState state, DoorOpening style) {
        return state.getBlock() instanceof PoweredDoorBlock door ? door.offered(style)
                : DoorOpening.SWING;
    }

    private static final double[][] SHUT_BOXES = {{0, 0, 0, 16, 16, LEAF_D}};

    private static final Map<Direction, VoxelShape> SHUT_SHAPES = buildShapes(SHUT_BOXES);
    private static final Map<DoorOpening, Map<Direction, VoxelShape>> OPEN_LEFT = buildOpenShapes(false);
    private static final Map<DoorOpening, Map<Direction, VoxelShape>> OPEN_RIGHT = buildOpenShapes(true);

    public PoweredDoorBlock(DoorMaterial material, Properties properties) {
        super(properties);
        this.material = material;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(HINGE, DoorHingeSide.LEFT)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, HINGE, OPEN);
    }

    public static Direction widthAxis(Direction facing) {
        return facing.getClockWise();
    }

    private static Direction towardSeam(Direction facing, DoorHingeSide hinge) {
        return hinge == DoorHingeSide.LEFT ? widthAxis(facing) : widthAxis(facing).getOpposite();
    }

    @Nullable
    public static BlockPos partnerOf(BlockGetter level, BlockPos lower, BlockState state) {
        if (!(state.getBlock() instanceof PoweredDoorBlock)) {
            return null;
        }
        final Direction facing = state.getValue(FACING);
        final DoorHingeSide hinge = state.getValue(HINGE);
        final BlockPos at = lower.relative(towardSeam(facing, hinge));
        final BlockState other = level.getBlockState(at);
        return other.is(state.getBlock())
                && other.getValue(HALF) == DoubleBlockHalf.LOWER
                && other.getValue(FACING) == facing
                && other.getValue(HINGE) != hinge
                ? at : null;
    }

    public static boolean isPaired(BlockGetter level, BlockPos lower, BlockState state) {
        return partnerOf(level, lower, state) != null;
    }

    private static Map<Direction, VoxelShape> buildShapes(double[][] boxes) {
        final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (final Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (final double[] b : boxes) {
                shape = Shapes.or(shape, rotateBox(b, facing));
            }
            map.put(facing, shape);
        }
        return map;
    }

    private static double[][] openBoxes(DoorOpening style) {
        final double t = LEAF_D;
        return switch (style) {
            case SWING -> new double[][]{{1, 0, 0, 1 + t, 16, 16}};
            case WINGS -> new double[][]{{1, 0, 0, 1 + t, 16, 8}, {15 - t, 0, 0, 15, 16, 8}};
            case FOLD_PARK, CONCERTINA -> new double[][]{{1, 0, 0, 2 + t, 16, 6 + t}};
            case SLIDE, FOLD_RETRACT, STACK_SLIDE, PLUG_RETRACT ->
                    new double[][]{{0, 0, 0, t, 16, 4 + t}};

            case ROLL_UP -> new double[][]{{0, 13, 0, 16, 16, 4 + t}};
        };
    }

    private static boolean mirrorsWithHinge(DoorOpening style) {
        return style != DoorOpening.STACK_SLIDE;
    }

    private static Map<DoorOpening, Map<Direction, VoxelShape>> buildOpenShapes(boolean hingeRight) {
        final Map<DoorOpening, Map<Direction, VoxelShape>> map = new EnumMap<>(DoorOpening.class);
        for (final DoorOpening style : DoorOpening.values()) {
            final double[][] boxes = hingeRight && mirrorsWithHinge(style)
                    ? mirrorX(openBoxes(style)) : openBoxes(style);
            map.put(style, buildShapes(boxes));
        }
        return map;
    }

    private static double[][] mirrorX(double[][] boxes) {
        final double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            final double[] b = boxes[i];
            out[i] = new double[]{16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5]};
        }
        return out;
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!state.getValue(OPEN)) {
            return SHUT_SHAPES.get(state.getValue(FACING));
        }
        final Map<DoorOpening, Map<Direction, VoxelShape>> byStyle =
                state.getValue(HINGE) == DoorHingeSide.RIGHT ? OPEN_RIGHT : OPEN_LEFT;
        return byStyle.get(styleAt(level, pos, state)).get(state.getValue(FACING));
    }

    private static DoorOpening styleAt(BlockGetter level, BlockPos pos, BlockState state) {
        final BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return level.getBlockEntity(lower) instanceof PoweredDoorBE door
                ? offeredBy(state, door.style()).effective(isPaired(level, lower, state))
                : DoorOpening.SWING;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT_SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OPEN);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        final Direction facing = context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(HINGE, hingeFor(context, facing))
                .setValue(OPEN, false);
    }

    private static DoorHingeSide hingeFor(BlockPlaceContext context, Direction facing) {
        final BlockPos pos = context.getClickedPos();
        final Direction plusX = widthAxis(facing);
        if (presentsFreeEdge(context.getLevel(), pos.relative(plusX), facing, DoorHingeSide.RIGHT)) {
            return DoorHingeSide.LEFT;
        }
        if (presentsFreeEdge(context.getLevel(), pos.relative(plusX.getOpposite()), facing,
                DoorHingeSide.LEFT)) {
            return DoorHingeSide.RIGHT;
        }
        final Vec3 hit = context.getClickLocation();
        final double x = hit.x - pos.getX();
        final double z = hit.z - pos.getZ();
        final double alongWidth = switch (facing) {
            case EAST -> z;
            case SOUTH -> 1.0 - x;
            case WEST -> 1.0 - z;
            default -> x;
        };
        return alongWidth < 0.5 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
    }

    private static boolean presentsFreeEdge(LevelReader level, BlockPos at, Direction facing,
                                            DoorHingeSide hinge) {
        final BlockState state = level.getBlockState(at);
        return state.getBlock() instanceof PoweredDoorBlock
                && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && state.getValue(FACING) == facing
                && state.getValue(HINGE) == hinge;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (level.isClientSide()) {
            return;
        }
        final PoweredDoorBE placed = beAt(level, pos);
        if (placed == null) {
            return;
        }

        placed.setStyle(this.material.defaultOpening());
        final BlockPos partner = partnerOf(level, pos, state);
        final PoweredDoorBE joined = partner == null ? null : beAt(level, partner);
        if (joined == null) {
            return;
        }
        placed.setStyle(joined.style());
        placed.setSeam(joined.hasSeam());
        if (joined.isOpen()) {
            toggle(level, pos, true, Drive.PLACEMENT, null);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        final BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        final DoubleBlockHalf half = state.getValue(HALF);
        final boolean towardPartner = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardPartner) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half
                    ? state : Blocks.AIR.defaultBlockState();
        }
        return half == DoubleBlockHalf.LOWER && direction == Direction.DOWN
                && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            final BlockPos below = pos.below();
            final BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS
                        | Block.UPDATE_ALL);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? java.util.List.of()
                : super.getDrops(state, params);
    }

    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Nullable
    public static com.createstats.power.BoreLinks.Host boreHostAt(LevelReader level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PoweredDoorBlock)
                || state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return null;
        }
        final BlockPos lower = pos.below();
        return level.getBlockEntity(lower) instanceof PoweredDoorBE door ? door : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && level.getBlockEntity(pos) instanceof PoweredDoorBE door) {
            com.createstats.power.BoreLinks.severAll(level, door);
            door.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    public static PoweredDoorBE leafAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PoweredDoorBlock) || !state.hasProperty(HALF)) {
            return null;
        }
        final BlockPos lower = lowerPos(state, pos);
        return level.getBlockEntity(lower) instanceof PoweredDoorBE door ? door : null;
    }

    @Nullable
    private static PoweredDoorBE beAt(Level level, BlockPos lower) {
        return level.getBlockEntity(lower) instanceof PoweredDoorBE door ? door : null;
    }

    @Nullable
    private static BlockPos partnerLower(Level level, BlockPos lower) {
        return partnerOf(level, lower, level.getBlockState(lower));
    }

    private enum Drive {
        HAND,

        REDSTONE,

        PROXIMITY,

        PLACEMENT
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        final BlockPos lower = lowerPos(state, pos);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            toggleSeam(level, lower, player);
            return InteractionResult.CONSUME;
        }
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null) {
            return InteractionResult.CONSUME;
        }

        if (!door.mayOperate(player)) {
            refuse(level, lower, door, player);
            return InteractionResult.CONSUME;
        }
        door.noteAccess(player, true);
        toggle(level, lower, !door.isOpen(), Drive.HAND, player);
        return InteractionResult.CONSUME;
    }

    private static boolean mayConfigure(Level level, BlockPos lower, PoweredDoorBE door,
                                        @Nullable Player player) {
        if (door.mayOperate(player)) {
            return true;
        }
        if (player != null) {
            refuse(level, lower, door, player);
        }
        return false;
    }

    private static void refuse(Level level, BlockPos lower, PoweredDoorBE door, Player player) {
        door.noteAccess(player, false);

        player.displayClientMessage(door.lockedDown()
                ? Component.translatable("message.create_stats.powered_door.lockdown")
                : Component.translatable(
                        "message.create_stats.powered_door.denied",
                        Clearance.badge(door.requiredLevel())), true);

        door.markRefused();
        level.playSound(null, lower, DoorSound.event(com.createstats.sound.DoorClip.REFUSE),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, 1.0f);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return AllItems.WRENCH.isIn(stack)
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos lower = lowerPos(state, context.getClickedPos());
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null) {
            return InteractionResult.PASS;
        }
        if (!mayConfigure(level, lower, door, context.getPlayer())) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos partner = partnerLower(level, lower);
        final DoorOpening picked = door.cycleStyle(partner != null, openings());
        final PoweredDoorBE other = partner == null ? null : beAt(level, partner);
        if (other != null) {
            other.setStyle(picked);
        }
        final Player player = context.getPlayer();
        if (player != null) {
            player.displayClientMessage(Component.literal("Opening style: " + picked.name()
                    + (partner != null ? "  (double door)" : "")), true);
        }
        configClick(level, lower, 1.10f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos lower = lowerPos(state, context.getClickedPos());
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null) {
            return InteractionResult.PASS;
        }
        if (!mayConfigure(level, lower, door, context.getPlayer())) {
            return InteractionResult.SUCCESS;
        }
        final Player player = context.getPlayer();
        if (door.isOpen() || !door.settled()) {
            if (player != null) {
                player.displayClientMessage(Component.literal("Shut the door before moving its hinge"),
                        true);
            }
            return InteractionResult.SUCCESS;
        }
        final DoorHingeSide flipped = state.getValue(HINGE) == DoorHingeSide.LEFT
                ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
        for (final BlockPos at : new BlockPos[]{lower, lower.above()}) {
            final BlockState half = level.getBlockState(at);
            if (half.is(this)) {
                level.setBlock(at, half.setValue(HINGE, flipped), Block.UPDATE_ALL);
            }
        }

        final BlockPos partner = partnerLower(level, lower);
        final PoweredDoorBE joined = partner == null ? null : beAt(level, partner);
        if (joined != null) {
            door.setStyle(joined.style());
            door.setSeam(joined.hasSeam());
        }
        if (player != null) {
            player.displayClientMessage(Component.literal("Hinge: " + flipped.getSerializedName()
                    + (partner != null ? "  (double door)" : "  (single)")), true);
        }
        configClick(level, lower, 0.90f);
        return InteractionResult.SUCCESS;
    }

    private void toggleSeam(Level level, BlockPos lower, Player player) {
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null || !mayConfigure(level, lower, door, player)) {
            return;
        }
        final BlockPos partner = partnerLower(level, lower);
        if (partner == null) {
            player.displayClientMessage(Component.literal(
                    "Interlocked seam needs a second leaf beside this one"), true);
            return;
        }
        final boolean seam = !door.hasSeam();
        door.setSeam(seam);
        final PoweredDoorBE other = beAt(level, partner);
        if (other != null) {
            other.setSeam(seam);
        }
        player.displayClientMessage(
                Component.literal("Interlocked seam: " + (seam ? "on" : "off")), true);
        configClick(level, lower, seam ? 1.35f : 1.15f);
    }

    private static void configClick(Level level, BlockPos lower, float pitch) {
        level.playSound(null, lower, DoorSound.event(com.createstats.sound.DoorClip.CONFIG),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, pitch);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        final BlockPos lower = lowerPos(state, pos);
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null) {
            return;
        }

        final boolean powered = level.hasNeighborSignal(lower) || level.hasNeighborSignal(lower.above());
        if (!door.updatePowered(powered)) {
            return;
        }

        if (door.governed()) {
            door.markRefused();
            return;
        }
        if (powered != door.isOpen()) {
            toggle(level, lower, powered, Drive.REDSTONE, null);
        }
    }

    private void toggle(Level level, BlockPos lower, boolean open, Drive drive,
                        @Nullable Player asker) {
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null || door.isOpen() == open) {
            return;
        }
        final BlockPos partner = partnerLower(level, lower);
        final PoweredDoorBE other = partner == null ? null : beAt(level, partner);

        final PoweredDoorBE mover = other != null && other.isOpen() != open ? other : null;
        if (!afford(door, drive, asker) || (mover != null && !afford(mover, drive, asker))) {
            if (asker != null) {
                asker.displayClientMessage(Component.translatable(
                        door.mayReleaseByHand(asker) ? "message.create_stats.powered_door.no_power"
                                : "message.create_stats.powered_door.locked_dark"), true);

                door.markRefused();
                level.playSound(null, lower, DoorSound.event(com.createstats.sound.DoorClip.STALL),
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
            }
            return;
        }
        spend(door, drive);
        if (mover != null) {
            spend(mover, drive);
        }
        move(level, lower, open);
        if (partner != null) {
            move(level, partner, open);
        }
        if (drive == Drive.PROXIMITY) {
            if (open) {
                door.markAutoOpen();
                if (other != null) {
                    other.markAutoOpen();
                }
            } else {
                door.clearAutoOpen();
                if (other != null) {
                    other.clearAutoOpen();
                }
            }
        } else {
            door.clearAutoOpen();
            if (other != null) {
                other.clearAutoOpen();
            }
        }
        depart(level, lower, partner != null);
        level.gameEvent(null, open ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN
                : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE, lower);
    }

    private void depart(Level level, BlockPos lower, boolean paired) {
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null) {
            return;
        }
        final DoorSound.Kit kit = DoorSound.kit(door.effectiveStyle());
        final float gain = DoorSound.volume(material) * (paired ? 1.15f : 1.0f);
        final float pitch = DoorSound.pitch(material);
        level.playSound(null, lower, DoorSound.event(kit.depart()),
                net.minecraft.sounds.SoundSource.BLOCKS, gain, pitch);
        if (kit.departLayer() != null) {
            level.playSound(null, lower, DoorSound.event(kit.departLayer()),
                    net.minecraft.sounds.SoundSource.BLOCKS, gain * 0.85f, pitch);
        }

        level.playSound(null, lower, DoorSound.substance(material),
                net.minecraft.sounds.SoundSource.BLOCKS,
                gain * DoorSound.SUBSTANCE_GAIN, DoorSound.substancePitch(material));
    }

    private static boolean afford(PoweredDoorBE door, Drive drive, @Nullable Player asker) {
        if (drive == Drive.PLACEMENT || door.hasPower()) {
            return true;
        }
        return drive == Drive.HAND && door.mayReleaseByHand(asker);
    }

    private static void spend(PoweredDoorBE door, Drive drive) {
        if (drive != Drive.PLACEMENT) {
            door.spendCycle();
        }
    }

    public static void driveProximity(Level level, BlockPos lower, boolean open) {
        if (level.getBlockState(lower).getBlock() instanceof PoweredDoorBlock door) {
            door.toggle(level, lower, open, Drive.PROXIMITY, null);
        }
    }

    private void move(Level level, BlockPos lower, boolean open) {
        final PoweredDoorBE door = beAt(level, lower);
        if (door == null || door.isOpen() == open) {
            return;
        }
        door.setOpen(open);
        if (open) {
            setOpenBoth(level, lower, true);
        } else {
            level.scheduleTick(lower, this, Math.max(1, door.remainingTicks()));
        }
    }

    private void setOpenBoth(Level level, BlockPos lower, boolean open) {
        for (final BlockPos at : new BlockPos[]{lower, lower.above()}) {
            final BlockState state = level.getBlockState(at);
            if (state.is(this) && state.getValue(OPEN) != open) {
                level.setBlock(at, state.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        final PoweredDoorBE door = beAt(level, pos);
        if (door != null && !door.isOpen() && door.settled()) {
            setOpenBoth(level, pos, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? ModBlockEntities.POWERED_DOOR.create(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
            getTicker(Level level, BlockState state,
                      net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type != ModBlockEntities.POWERED_DOOR.get()) {
            return null;
        }

        if (level.isClientSide()) {
            return (lvl, pos, blockState, be) -> ((PoweredDoorBE) be).clientTick();
        }
        return (lvl, pos, blockState, be) -> ((PoweredDoorBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.powered_door",
                java.util.List.of(
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.powered_door_draw",
                                com.createstats.power.FeRates.perTick(PoweredDoorBE.drawPerTick()),
                                com.createstats.power.FeRates.amount(PoweredDoorBE.cycleCost())),
                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.powered_door_openings", openingList())));
    }

    private String openingList() {
        final StringBuilder out = new StringBuilder();
        for (DoorOpening style : DoorOpening.values()) {
            if (!this.material.openings().contains(style)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            final String key = style.key().replace('_', ' ');
            out.append(Character.toUpperCase(key.charAt(0))).append(key.substring(1));
        }
        return out.toString();
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }
}
