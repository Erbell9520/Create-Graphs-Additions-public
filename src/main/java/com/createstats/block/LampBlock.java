package com.createstats.block;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LampBlock extends Block implements net.minecraft.world.level.block.EntityBlock,
        com.simibubi.create.content.equipment.wrench.IWrenchable, GirderMounting.Fixture {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty SPIN =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("spin", 0, 3);

    // ten notches, light is an int 0..15 anyway, a slider would be lying
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty DIM =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("level", 0, 9);

    public static final float[] DIM_FRACTION = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};

    public static class Spun extends LampBlock {
        public Spun(Properties properties, VoxelShape upShape, int litLevel) {
            super(properties, upShape, litLevel);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(SPIN);
        }
    }

    private final VoxelShape upShape;

    public LampBlock(Properties properties, VoxelShape upShape, int litLevel) {
        super(properties);
        this.upShape = upShape;
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(LIT, false)
                .setValue(COLOR, DyeColor.WHITE)
                .setValue(DIM, DIM_FRACTION.length - 1));
    }

    public static java.util.function.ToIntFunction<BlockState> emission(int full) {
        return state -> {
            if (!state.getValue(LIT)) {
                return 0;
            }
            return Math.max(1, Math.round(full * DIM_FRACTION[state.getValue(DIM)]));
        };
    }

    public static float dimFraction(BlockState state) {
        return state.hasProperty(DIM) ? DIM_FRACTION[state.getValue(DIM)] : 1.0f;
    }

    public static int spin(BlockState state) {
        return state.hasProperty(SPIN) ? state.getValue(SPIN) : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, COLOR, DIM);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction facing = context.getClickedFace();

        final BlockState placed = defaultBlockState().setValue(FACING, facing).setValue(LIT, false);
        if (!placed.hasProperty(SPIN)) {
            return placed;
        }

        return placed.setValue(SPIN,
                MountedPart.spinTowards(facing, context.getHorizontalDirection().getOpposite()));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        if (!originalState.hasProperty(SPIN)) {
            return originalState;
        }
        return originalState.setValue(SPIN, (originalState.getValue(SPIN) + 1) % 4);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LampShapes.rotate(upShape, state.getValue(FACING), spin(state));
    }

    @Override
    public boolean isBackedBy(BlockState state, Direction towardSupport) {
        return state.getValue(FACING) == towardSupport.getOpposite();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(COLOR) == dye.getDyeColor()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(COLOR, dye.getDyeColor()), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @Nullable
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.createstats.registry.ModBlockEntities.LAMP.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                    Level level, BlockState state,
                    net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || type != com.createstats.registry.ModBlockEntities.LAMP.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((com.createstats.blockentity.LampBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                stack.getItem().getDescriptionId(),
                java.util.List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "tooltip.create_stats.lamp_draw",
                        com.createstats.power.FeRates.perTick(com.createstats.blockentity.LampBE.drawPerTick()))));
    }
}
