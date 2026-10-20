package com.createstats.block;

import com.createstats.power.CableRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class CableBlock extends Block implements EntityBlock, GirderMounting.Fixture {
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    private static final Map<Direction, BooleanProperty> BY_FACE = new EnumMap<>(Direction.class);

    static {
        BY_FACE.put(Direction.UP, UP);
        BY_FACE.put(Direction.DOWN, DOWN);
        BY_FACE.put(Direction.NORTH, NORTH);
        BY_FACE.put(Direction.SOUTH, SOUTH);
        BY_FACE.put(Direction.EAST, EAST);
        BY_FACE.put(Direction.WEST, WEST);
    }

    private final com.createstats.power.CableTier tier;

    private final Map<Direction, VoxelShape> faceShapes = new EnumMap<>(Direction.class);

    public CableBlock(Properties properties, com.createstats.power.CableTier tier) {
        super(properties);
        this.tier = tier == null ? com.createstats.power.CableTier.SURFACE : tier;

        BlockState state = defaultBlockState().setValue(COLOR, DyeColor.ORANGE);
        for (BooleanProperty p : BY_FACE.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
        for (Direction face : Direction.values()) {
            faceShapes.put(face, slabAgainst(face, this.tier.thicknessPx() + this.tier.reliefPx()));
        }
    }

    public static BooleanProperty faceProperty(Direction face) {
        return BY_FACE.get(face);
    }

    public com.createstats.power.CableTier tier() {
        return tier;
    }

    public float multiplier() {
        return tier.multiplier();
    }

    public double thicknessPx() {
        return tier.thicknessPx();
    }

    public static boolean hasFace(BlockState state, Direction face) {
        return state.getBlock() instanceof CableBlock && state.getValue(faceProperty(face));
    }

    public static boolean isBare(BlockState state) {
        for (Direction face : Direction.values()) {
            if (hasFace(state, face)) {
                return false;
            }
        }
        return true;
    }

    private static VoxelShape slabAgainst(Direction face, double t) {
        return switch (face) {
            case DOWN -> Block.box(0, 0, 0, 16, t, 16);
            case UP -> Block.box(0, 16 - t, 0, 16, 16, 16);
            case NORTH -> Block.box(0, 0, 0, 16, 16, t);
            case SOUTH -> Block.box(0, 0, 16 - t, 16, 16, 16);
            case WEST -> Block.box(0, 0, 0, t, 16, 16);
            case EAST -> Block.box(16 - t, 0, 0, 16, 16, 16);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST, COLOR);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        for (Direction face : Direction.values()) {
            if (hasFace(state, face)) {
                shape = Shapes.or(shape, faceShapes.get(face));
            }
        }
        return shape.isEmpty() ? Shapes.empty() : shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction face = context.getClickedFace().getOpposite();
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (!canAttachTo(level, pos, face)) {
            return null;
        }

        final BlockState existing = level.getBlockState(pos);
        if (existing.getBlock() == this) {
            return existing.setValue(faceProperty(face), true);
        }
        return defaultBlockState().setValue(faceProperty(face), true);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().getItem() == asItem()
                && !hasFace(state, context.getClickedFace().getOpposite());
    }

    public static boolean canAttachTo(LevelReader level, BlockPos pos, Direction face) {
        return GirderMounting.holds(level, pos.relative(face), face.getOpposite());
    }

    @Override
    public boolean isBackedBy(BlockState state, Direction towardSupport) {
        return hasFace(state, towardSupport);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        BlockState result = state;
        for (Direction face : Direction.values()) {
            if (hasFace(result, face) && !canAttachTo(level, pos, face)) {
                result = result.setValue(faceProperty(face), false);
            }
        }
        return isBare(result) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : result;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            final int painted = paintRun(level, pos, dye.getDyeColor());
            if (painted > 0 && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private int paintRun(Level level, BlockPos origin, DyeColor colour) {
        final DyeColor was = level.getBlockState(origin).getValue(COLOR);
        if (was == colour) {
            return 0;
        }

        final java.util.List<BlockPos> run = com.createstats.power.CableRun.collect(
                linkViewOf(level), origin, com.createstats.power.CableRun.MAX_RUN_BLOCKS);
        int painted = 0;
        for (BlockPos at : run) {
            final BlockState here = level.getBlockState(at);
            if (here.getBlock() instanceof CableBlock && here.getValue(COLOR) == was) {
                level.setBlock(at, here.setValue(COLOR, colour), Block.UPDATE_ALL);
                painted++;
            }
        }
        return painted;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.CABLE.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                    Level level, BlockState state,
                    net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || type != com.createstats.registry.ModBlockEntities.CABLE.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((com.createstats.blockentity.CableBE) be).tick();
    }

    // nothing re-floods when a neighbour appears, so placing a generator next to a run did nothing. yine mi
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   BlockPos neighbourPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        if (level.isClientSide()
                || !(level.getBlockEntity(pos) instanceof com.createstats.blockentity.CableBE cable)) {
            return;
        }
        final com.createstats.power.FeWireNetwork network = cable.network();
        if (network == null || network.isDirty()) {
            return;
        }
        final Direction towards = Direction.fromDelta(neighbourPos.getX() - pos.getX(),
                neighbourPos.getY() - pos.getY(), neighbourPos.getZ() - pos.getZ());
        if (towards == null) {
            return;
        }

        if (cable.joints().note(towards, face -> offersEnergy(level, pos, face))) {
            cable.invalidateNetwork();
        }
    }

    private static boolean offersEnergy(Level level, BlockPos pos, Direction towards) {
        return level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                pos.relative(towards), towards.getOpposite()) != null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof com.createstats.blockentity.CableBE cable) {
            com.createstats.power.BoreLinks.severAll(level, cable);
            cable.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    public static com.createstats.power.CableLinks.View linkViewOf(BlockGetter level) {
        return new com.createstats.power.CableLinks.View() {
            @Nullable
            private BlockState stateAt(BlockPos pos) {
                if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) {
                    return null;
                }
                return level.getBlockState(pos);
            }

            @Override
            public boolean hasCableFace(BlockPos pos, Direction face) {
                final BlockState state = stateAt(pos);
                return state != null && CableBlock.hasFace(state, face);
            }

            @Override
            public boolean isCable(BlockPos pos) {
                final BlockState state = stateAt(pos);
                return state != null && state.getBlock() instanceof CableBlock;
            }

            @Override
            public boolean isFree(BlockPos pos) {
                final BlockState state = stateAt(pos);

                return state != null && (state.isAir() || state.getBlock() instanceof CableBlock
                        || state.canBeReplaced());
            }

            @Override
            public boolean isDevice(BlockPos pos, Direction fromSide) {
                return level instanceof Level lvl
                        && lvl.hasChunkAt(pos)
                        && lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities
                                .EnergyStorage.BLOCK, pos, fromSide) != null;
            }

            @Override
            public int colourAt(BlockPos pos) {
                final BlockState state = stateAt(pos);
                return state != null && state.getBlock() instanceof CableBlock
                        ? state.getValue(COLOR).ordinal()
                        : com.createstats.power.CableLinks.NO_COLOUR;
            }
        };
    }

    public static CableRoute.SurfaceView viewOf(LevelReader level) {
        return new CableRoute.SurfaceView() {
            @Override
            public boolean isFree(BlockPos pos) {
                final BlockState state = level.getBlockState(pos);
                return state.isAir() || state.getBlock() instanceof CableBlock
                        || state.canBeReplaced();
            }

            @Override
            public boolean isSolid(BlockPos pos) {
                final BlockState state = level.getBlockState(pos);
                return state.isCollisionShapeFullBlock(level, pos)
                        || GirderMounting.kindOf(state) == GirderMounting.Kind.GIRDER;
            }
        };
    }
}
