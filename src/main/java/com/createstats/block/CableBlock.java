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

/** [1] */
public class CableBlock extends Block implements EntityBlock {

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

    /** The grade of this cable: what it carries, and how thick it is drawn. */
    private final com.createstats.power.CableTier tier;

    private final Map<Direction, VoxelShape> faceShapes = new EnumMap<>(Direction.class);

    public CableBlock(Properties properties, com.createstats.power.CableTier tier) {
        super(properties);
        this.tier = tier == null ? com.createstats.power.CableTier.SURFACE : tier;
        // Copper by default, because that is what it is crafted from. White multiplied against a
        // neutral cord texture came out a flat lavender grey that looked like a placeholder.
        BlockState state = defaultBlockState().setValue(COLOR, DyeColor.ORANGE);
        for (BooleanProperty p : BY_FACE.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
        for (Direction face : Direction.values()) {
            faceShapes.put(face, slabAgainst(face, this.tier.thicknessPx()));
        }
    }

    /** The property recording whether this block carries cable on the given face. */
    public static BooleanProperty faceProperty(Direction face) {
        return BY_FACE.get(face);
    }

    /** The grade of this cable. Everything that differs between the three comes from here. */
    public com.createstats.power.CableTier tier() {
        return tier;
    }

    public int multiplier() {
        return tier.multiplier();
    }

    /** How far the cable stands off its mounting face, in pixels. Read by the renderer. */
    public double thicknessPx() {
        return tier.thicknessPx();
    }

    /** True when this state carries cable on the given face. */
    public static boolean hasFace(BlockState state, Direction face) {
        return state.getBlock() instanceof CableBlock && state.getValue(faceProperty(face));
    }

    /** True when this state carries no cable at all, so the block should be removed. */
    public static boolean isBare(BlockState state) {
        for (Direction face : Direction.values()) {
            if (hasFace(state, face)) {
                return false;
            }
        }
        return true;
    }

    /** A thin slab hugging one face of the block, which is the cable's footprint on that face. */
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

    /** Cable is walked through, never stood on. */
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
        // Placing into an existing cable adds a face rather than replacing it, which is what lets a
        // corner end up carrying two faces at one position.
        final BlockState existing = level.getBlockState(pos);
        if (existing.getBlock() == this) {
            return existing.setValue(faceProperty(face), true);
        }
        return defaultBlockState().setValue(faceProperty(face), true);
    }

    /** [2] */
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().getItem() == asItem()
                && !hasFace(state, context.getClickedFace().getOpposite());
    }

    /** True when the block behind this face can hold cable. */
    public static boolean canAttachTo(LevelReader level, BlockPos pos, Direction face) {
        final BlockPos support = pos.relative(face);
        return level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite());
    }

    /** [3] */
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

    /** [4] */
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

    /**
     * Floods the connected run of this same cable kind and recolours it.
     * capped - a run can hit hundreds of blocks, don't want one dye click scanning the whole world
     *
     * @return how many blocks were recoloured
     */
    private int paintRun(Level level, BlockPos origin, DyeColor colour) {
        final DyeColor was = level.getBlockState(origin).getValue(COLOR);
        if (was == colour) {
            return 0;
        }
        // [5]
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

    /** [6] */
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

    /** A cut or extended run has a different shape, so the grid has to be rediscovered. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof com.createstats.blockentity.CableBE cable) {
            cable.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** [7] */
    public static com.createstats.power.CableLinks.View linkViewOf(BlockGetter level) {
        return new com.createstats.power.CableLinks.View() {
            @Override
            public boolean hasCableFace(BlockPos pos, Direction face) {
                return CableBlock.hasFace(level.getBlockState(pos), face);
            }

            @Override
            public boolean isCable(BlockPos pos) {
                return level.getBlockState(pos).getBlock() instanceof CableBlock;
            }

            @Override
            public boolean isDevice(BlockPos pos, Direction fromSide) {
                return level instanceof Level lvl
                        && lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities
                                .EnergyStorage.BLOCK, pos, fromSide) != null;
            }

            /** [8] */
            @Override
            public int colourAt(BlockPos pos) {
                final BlockState state = level.getBlockState(pos);
                return state.getBlock() instanceof CableBlock
                        ? state.getValue(COLOR).ordinal()
                        : com.createstats.power.CableLinks.NO_COLOUR;
            }
        };
    }

    /** Adapts a level to what {@link CableRoute} needs, so routing and placement agree exactly. */
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
                return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
            }
        };
    }
}
