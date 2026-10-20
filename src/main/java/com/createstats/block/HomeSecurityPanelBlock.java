package com.createstats.block;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HomeSecurityPanelBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Face> FACE = EnumProperty.create("face", Face.class);

    public enum Face implements StringRepresentable {
        DARK("dark"),

        LIVE("live"),

        ALERT("alert");

        private final String name;

        Face(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private static final VoxelShape SHAPE_NORTH = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 1.9);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2.0, 2.0, 14.1, 14.0, 14.0, 16.0);
    private static final VoxelShape SHAPE_EAST = Block.box(14.1, 2.0, 2.0, 16.0, 14.0, 14.0);
    private static final VoxelShape SHAPE_WEST = Block.box(0.0, 2.0, 2.0, 1.9, 14.0, 14.0);

    public HomeSecurityPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, Face.DARK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction clicked = context.getClickedFace();
        if (!clicked.getAxis().isHorizontal()) {
            return null;
        }
        final BlockState state = defaultBlockState()
                .setValue(FACING, clicked.getOpposite())
                .setValue(FACE, Face.DARK);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final Direction facing = state.getValue(FACING);
        final BlockPos support = pos.relative(facing);
        return level.getBlockState(support).isFaceSturdy(level, support, facing.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HomeSecurityPanelBE panel)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sender) {
            panel.claim(player);
            panel.openFor(sender);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos,
                                        net.minecraft.world.level.Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof HomeSecurityPanelBE panel && panel.isClaimed()) {
            return 3600000.0f;
        }
        return super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof HomeSecurityPanelBE panel) {
            panel.claim(player);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HomeSecurityPanelBE(ModBlockEntities.HOME_SECURITY_PANEL.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.HOME_SECURITY_PANEL.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((HomeSecurityPanelBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.home_security_panel",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.home_security_panel_draw",
                                com.createstats.power.FeRates.perTick(HomeSecurityPanelBE.drawPerTick())),

                        com.createstats.util.CreateStatsTooltipHelper.spec(
                                "tooltip.create_stats.home_security_panel_owned")));
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }
}
