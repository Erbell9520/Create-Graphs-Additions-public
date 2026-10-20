package com.createstats.block;

import com.createstats.blockentity.IndoorCableBE;
import com.createstats.power.BoreLinks;
import com.createstats.power.IndoorCableAnchor;
import com.createstats.power.IndoorCableLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndoorCableBlock extends Block implements EntityBlock, GirderMounting.Fixture {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public static final EnumProperty<IndoorCableAnchor> ANCHOR =
            EnumProperty.create("anchor", IndoorCableAnchor.class);

    private static final int MAX_PAINT = 512;

    private static final double HALF_HITBOX = 2.0;

    private static final Map<IndoorCableAnchor, VoxelShape> CORES =
            new EnumMap<>(IndoorCableAnchor.class);
    private static final Map<IndoorCableAnchor, Map<Direction, VoxelShape>> ARMS =
            new EnumMap<>(IndoorCableAnchor.class);

    static {
        for (IndoorCableAnchor anchor : IndoorCableAnchor.values()) {
            CORES.put(anchor, boxAround(anchor, null));
            final Map<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);
            for (Direction dir : Direction.values()) {
                arms.put(dir, boxAround(anchor, dir));
            }
            ARMS.put(anchor, arms);
        }
    }

    private static VoxelShape boxAround(IndoorCableAnchor anchor, @Nullable Direction towards) {
        final double[] min = new double[3];
        final double[] max = new double[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            final double centre = anchor.centreOn(axis) * 16.0;
            min[axis.ordinal()] = centre - HALF_HITBOX;
            max[axis.ordinal()] = centre + HALF_HITBOX;
        }
        if (towards != null) {
            final int i = towards.getAxis().ordinal();
            if (towards.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                max[i] = 16.0;
            } else {
                min[i] = 0.0;
            }
        }

        for (int i = 0; i < 3; i++) {
            min[i] = Math.max(0.0, min[i]);
            max[i] = Math.min(16.0, max[i]);
        }
        return Block.box(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    public IndoorCableBlock(Properties properties) {
        super(properties);

        registerDefaultState(defaultBlockState().setValue(COLOR, DyeColor.LIGHT_GRAY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, ANCHOR);
    }

    public static IndoorCableAnchor anchorOf(BlockState state) {
        return state.getBlock() instanceof IndoorCableBlock ? state.getValue(ANCHOR)
                : IndoorCableAnchor.FREE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        final IndoorCableAnchor anchor = anchorOf(state);
        VoxelShape shape = CORES.get(anchor);
        final IndoorCableLinks.View view = linkViewOf(level);
        final Map<Direction, VoxelShape> arms = ARMS.get(anchor);
        for (Direction dir : Direction.values()) {
            if (IndoorCableLinks.of(view, pos, dir) != IndoorCableLinks.Link.NONE) {
                shape = Shapes.or(shape, arms.get(dir));
            }
        }
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ANCHOR, anchorFor(context));
    }

    private static IndoorCableAnchor anchorFor(BlockPlaceContext context) {
        final LevelReader level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Direction toSupport = context.getClickedFace().getOpposite();
        final BlockState clicked = level.getBlockState(pos.relative(toSupport));
        if (clicked.getBlock() instanceof IndoorCableBlock) {
            final IndoorCableAnchor theirs = clicked.getValue(ANCHOR);
            return supportedPartOf(level, pos, theirs)
                    .hug(toSupport.getAxis(), theirs != IndoorCableAnchor.FREE, supportedAt(level, pos));
        }
        return (supports(level, pos, toSupport) ? IndoorCableAnchor.of(toSupport) : IndoorCableAnchor.FREE)
                .hug(null, false, supportedAt(level, pos));
    }

    private static java.util.function.Predicate<Direction> supportedAt(LevelReader level, BlockPos pos) {
        return face -> supports(level, pos, face);
    }

    private static IndoorCableAnchor supportedPartOf(LevelReader level, BlockPos pos,
                                                     IndoorCableAnchor anchor) {
        IndoorCableAnchor kept = IndoorCableAnchor.FREE;
        for (Direction face : anchor.faces()) {
            if (supports(level, pos, face)) {
                kept = kept.with(face);
            }
        }
        return kept;
    }

    private static boolean supports(LevelReader level, BlockPos pos, @Nullable Direction face) {
        if (face == null) {
            return false;
        }
        return GirderMounting.holds(level, pos.relative(face), face.getOpposite());
    }

    @Override
    public boolean isBackedBy(BlockState state, Direction towardSupport) {
        return anchorOf(state).clipsTo(towardSupport);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            final IndoorCableAnchor anchor = state.getValue(ANCHOR);
            final int clipped = anchorRun(level, pos, anchor);
            player.displayClientMessage(Component.translatable(
                    anchor == IndoorCableAnchor.FREE
                            ? "message.create_stats.indoor_cable.run_centred"
                            : "message.create_stats.indoor_cable.run_clipped", clipped), true);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.COPPER_GRATE_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.2f);
            return InteractionResult.CONSUME;
        }
        final List<IndoorCableAnchor> options = anchorOptions(level, pos);
        final int next = (options.indexOf(state.getValue(ANCHOR)) + 1) % options.size();
        level.setBlock(pos, state.setValue(ANCHOR, options.get(next)), Block.UPDATE_ALL);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.COPPER_GRATE_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.6f);
        return InteractionResult.CONSUME;
    }

    private static List<IndoorCableAnchor> anchorOptions(LevelReader level, BlockPos pos) {
        final List<Direction> held = new ArrayList<>(6);
        for (Direction face : Direction.values()) {
            if (supports(level, pos, face)) {
                held.add(face);
            }
        }
        final List<IndoorCableAnchor> options = new ArrayList<>(held.size() + 2);
        for (Direction face : held) {
            options.add(IndoorCableAnchor.of(face));
        }
        for (int i = 0; i < held.size(); i++) {
            for (int j = i + 1; j < held.size(); j++) {
                final IndoorCableAnchor edge = IndoorCableAnchor.of(held.get(i), held.get(j));

                if (edge.faces().size() == 2) {
                    options.add(edge);
                }
            }
        }
        options.add(IndoorCableAnchor.FREE);
        return options;
    }

    private int anchorRun(Level level, BlockPos origin, IndoorCableAnchor anchor) {
        int clipped = 0;
        for (BlockPos at : runFrom(level, origin)) {
            final BlockState here = level.getBlockState(at);
            if (!(here.getBlock() instanceof IndoorCableBlock)) {
                continue;
            }
            IndoorCableAnchor want = IndoorCableAnchor.FREE;
            if (anchor != IndoorCableAnchor.FREE) {
                want = supportedPartOf(level, at, anchor);
                if (want == IndoorCableAnchor.FREE) {
                    continue;
                }

                for (Direction face : here.getValue(ANCHOR).faces()) {
                    if (supports(level, at, face)) {
                        want = want.with(face);
                    }
                }
            }
            if (want != here.getValue(ANCHOR)) {
                level.setBlock(at, here.setValue(ANCHOR, want), Block.UPDATE_ALL);
            }
            clipped++;
        }
        return clipped;
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
        final BlockState from = level.getBlockState(origin);
        if (!(from.getBlock() instanceof IndoorCableBlock) || from.getValue(COLOR) == colour) {
            return 0;
        }
        int painted = 0;

        for (BlockPos at : runFrom(level, origin)) {
            final BlockState here = level.getBlockState(at);
            if (here.getBlock() instanceof IndoorCableBlock) {
                level.setBlock(at, here.setValue(COLOR, colour), Block.UPDATE_ALL);
                painted++;
            }
        }
        return painted;
    }

    private static List<BlockPos> runFrom(Level level, BlockPos origin) {
        final IndoorCableLinks.View view = linkViewOf(level);
        final List<BlockPos> run = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        seen.add(origin.immutable());
        while (!queue.isEmpty() && run.size() < MAX_PAINT) {
            final BlockPos at = queue.poll();
            run.add(at);
            for (BlockPos next : IndoorCableLinks.continuations(view, at)) {
                if (seen.add(next.immutable())) {
                    queue.add(next.immutable());
                }
            }
        }
        return run;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.INDOOR_CABLE.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != com.createstats.registry.ModBlockEntities.INDOOR_CABLE.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((IndoorCableBE) be).tick();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   BlockPos neighbourPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof IndoorCableBE cable)) {
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
        return com.createstats.power.IndoorCable.energyAt(
                level, pos.relative(towards), towards.getOpposite()) != null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof IndoorCableBE cable) {
            BoreLinks.severAll(level, cable);
            cable.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    public static IndoorCableLinks.View linkViewOf(BlockGetter level) {
        return new IndoorCableLinks.View() {
            @Nullable
            private BlockState stateAt(BlockPos pos) {
                if (level instanceof net.minecraft.world.level.LevelReader reader && !reader.hasChunkAt(pos)) {
                    return null;
                }
                return level.getBlockState(pos);
            }

            @Override
            public boolean isIndoorCable(BlockPos pos) {
                final BlockState state = stateAt(pos);
                return state != null && state.getBlock() instanceof IndoorCableBlock;
            }

            @Override
            public int colourAt(BlockPos pos) {
                final BlockState state = stateAt(pos);
                return state != null && state.getBlock() instanceof IndoorCableBlock
                        ? state.getValue(COLOR).ordinal()
                        : IndoorCableLinks.NO_COLOUR;
            }

            @Override
            public boolean isDevice(BlockPos pos, Direction fromSide) {
                return level instanceof Level lvl
                        && com.createstats.power.IndoorCable.energyAt(lvl, pos, fromSide) != null;
            }

            @Override
            @Nullable
            public BlockPos boreTowards(BlockPos pos, Direction towards) {
                if (level instanceof net.minecraft.world.level.LevelReader reader && !reader.hasChunkAt(pos)) {
                    return null;
                }
                if (!(level.getBlockEntity(pos) instanceof IndoorCableBE cable)) {
                    return null;
                }
                for (BlockPos far : cable.bores()) {
                    if (BoreLinks.axisBetween(pos, far) == towards) {
                        return far;
                    }
                }
                return null;
            }
        };
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.indoor_cable",
                java.util.List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "tooltip.create_stats.indoor_cable_rate",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.indoorCable()))));
    }
}
