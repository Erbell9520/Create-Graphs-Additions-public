package com.createstats.block;

import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import com.createstats.util.ClientScreenAccess;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

public class StatusTowerLightBlock extends Block implements EntityBlock {
    public static final EnumProperty<Segment> SEGMENT = EnumProperty.create("segment", Segment.class);

    public static final EnumProperty<Glow> GLOW = EnumProperty.create("glow", Glow.class);

    public static final int MAX_STACK = 4;

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            Block.box(3.2, 0.0, 3.2, 12.8, 0.9, 12.8),
            Block.box(4.4, 0.9, 4.4, 11.6, 16.0, 11.6));
    private static final VoxelShape COLUMN_SHAPE = Block.box(4.4, 0.0, 4.4, 11.6, 16.0, 11.6);

    public StatusTowerLightBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SEGMENT, Segment.SINGLE)
                .setValue(GLOW, Glow.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT, GLOW);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(SEGMENT, segmentAt(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SEGMENT).hasBase() ? BASE_SHAPE : COLUMN_SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (isTower(level.getBlockState(pos.below()))) {
            return towerHeightBelow(level, pos) < MAX_STACK;
        }
        return !level.getBlockState(pos.below()).isAir();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction.getAxis().isVertical()) {
            return state.setValue(SEGMENT, segmentAt(level, currentPos));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    private static Segment segmentAt(LevelReader level, BlockPos pos) {
        final boolean below = isTower(level.getBlockState(pos.below()));
        final boolean above = isTower(level.getBlockState(pos.above()));
        if (below && above) {
            return Segment.MIDDLE;
        }
        if (below) {
            return Segment.TOP;
        }
        if (above) {
            return Segment.BASE;
        }
        return Segment.SINGLE;
    }

    public static boolean isTower(BlockState state) {
        return state.getBlock() instanceof StatusTowerLightBlock;
    }

    private static int towerHeightBelow(LevelReader level, BlockPos pos) {
        int height = 0;
        BlockPos cursor = pos.below();
        while (height < MAX_STACK + 1 && isTower(level.getBlockState(cursor))) {
            height++;
            cursor = cursor.below();
        }
        return height;
    }

    public static BlockPos towerBase(BlockGetter level, BlockPos pos) {
        BlockPos cursor = pos;
        for (int guard = 0; guard < MAX_STACK && isTower(level.getBlockState(cursor.below())); guard++) {
            cursor = cursor.below();
        }
        return cursor;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof StatusTowerLightBE towerLight)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            ClientScreenAccess.openStatusTowerLight(towerLight);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        final BlockEntity be = level.getBlockEntity(pos);
        return be instanceof StatusTowerLightBE tower && tower.isCriticalPowered() ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StatusTowerLightBE tower)) {
            return 0;
        }
        int worst = tower.comparatorLevel();
        if (state.getValue(SEGMENT).hasBase()) {
            BlockPos cursor = pos.above();
            for (int i = 0; i < MAX_STACK && isTower(level.getBlockState(cursor)); i++) {
                if (level.getBlockEntity(cursor) instanceof StatusTowerLightBE above) {
                    worst = Math.max(worst, above.comparatorLevel());
                }
                cursor = cursor.above();
            }
        }
        return worst;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatusTowerLightBE(ModBlockEntities.STATUS_TOWER_LIGHT.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.STATUS_TOWER_LIGHT.get()) {
            return null;
        }

        return level.isClientSide() ? null : (lvl, pos, st, be) -> ((StatusTowerLightBE) be).tick();
    }

    public static int getLightLevel(BlockState state) {
        return state.hasProperty(GLOW) ? state.getValue(GLOW).lightLevel() : 0;
    }

    public enum Glow implements StringRepresentable {
        OFF("off", 0),
        DIM("dim", 5),
        MID("mid", 9),
        HOT("hot", 13);

        private final String serializedName;
        private final int lightLevel;

        Glow(String serializedName, int lightLevel) {
            this.serializedName = serializedName;
            this.lightLevel = lightLevel;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }

        public int lightLevel() {
            return this.lightLevel;
        }
    }

    public enum Segment implements StringRepresentable {
        SINGLE("single"),

        BASE("base"),

        MIDDLE("middle"),

        TOP("top");

        private final String serializedName;

        Segment(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }

        public boolean hasBase() {
            return this == SINGLE || this == BASE;
        }

        public boolean hasCap() {
            return this == SINGLE || this == TOP;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.status_tower_light");
    }
}
