package com.createstats.block;

import java.util.List;

import com.createstats.blockentity.StageLightBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** [1] */
public class StageLightBlock extends Block implements IBE<StageLightBE> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape BASE_DOWN = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);
    private static final VoxelShape BASE_UP = Block.box(2.0, 2.0, 2.0, 14.0, 16.0, 14.0);
    private static final VoxelShape BASE_NORTH = Block.box(2.0, 2.0, 2.0, 14.0, 14.0, 16.0);
    private static final VoxelShape BASE_SOUTH = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 14.0);
    private static final VoxelShape BASE_WEST = Block.box(2.0, 2.0, 2.0, 16.0, 14.0, 14.0);
    private static final VoxelShape BASE_EAST = Block.box(0.0, 2.0, 2.0, 14.0, 14.0, 14.0);

    public StageLightBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction mountNormal = context.getClickedFace();
        return this.defaultBlockState()
                .setValue(FACING, mountNormal)
                .setValue(LIT, false);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> BASE_UP;
            case NORTH -> BASE_NORTH;
            case SOUTH -> BASE_SOUTH;
            case WEST -> BASE_WEST;
            case EAST -> BASE_EAST;
            default -> BASE_DOWN;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Wrench removes the colour filter (returns the glass); with no filter, let Create rotate / pick up.
        if (AllItems.WRENCH.isIn(stack)) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StageLightBE stageLight && stageLight.hasTint()) {
                if (!level.isClientSide()) {
                    final net.minecraft.world.item.DyeColor dye =
                            com.createstats.util.StageLightColors.fromRgb(stageLight.getTint());
                    stageLight.setTint(StageLightBE.NO_TINT);
                    if (dye != null && !player.getAbilities().instabuild) {
                        final Item glass = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation.withDefaultNamespace(dye.getName() + "_stained_glass"));
                        if (glass != null) {
                            Block.popResource(level, pos, new ItemStack(glass));
                        }
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Right-click with stained glass (or a stained glass pane) to apply that colour filter; consumes one.
        final int tint = tintFromStack(stack);
        if (tint >= 0) {
            if (!level.isClientSide()) {
                final BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof StageLightBE stageLight) {
                    stageLight.setTint(tint);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Maps a stained-glass (block or pane) stack to its 0xRRGGBB tint, or -1 if it is not stained glass. */
    private static int tintFromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        final String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        final String colorName;
        if (path.endsWith("_stained_glass_pane")) {
            colorName = path.substring(0, path.length() - "_stained_glass_pane".length());
        } else if (path.endsWith("_stained_glass")) {
            colorName = path.substring(0, path.length() - "_stained_glass".length());
        } else {
            return -1;
        }
        final net.minecraft.world.item.DyeColor dye = net.minecraft.world.item.DyeColor.byName(colorName, null);
        return dye == null ? -1 : com.createstats.util.StageLightColors.rgb(dye);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // Right-click opens the control screen (works on the ground and on Aeronautics ships). On/off,
        // pan, tilt, range and brightness are all set there.
        if (level.isClientSide()) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StageLightBE stageLight) {
                com.createstats.util.ClientScreenAccess.openStageLight(stageLight);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            refreshGimbal(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            refreshGimbal(level, pos, state);
        }
    }

    private static void refreshGimbal(Level level, BlockPos pos, BlockState state) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StageLightBE stageLight) {
            stageLight.applyRedstoneGimbal(level, pos, state.getValue(FACING));
        }
    }

    /** Light emission: the fixture glows while lit (also lights adjacent blocks, including on ships). */
    public static int getLightEmission(BlockState state) {
        return state.hasProperty(LIT) && state.getValue(LIT) ? StageLightBE.LIT_LIGHT_LEVEL : 0;
    }

    @Override
    public Class<StageLightBE> getBlockEntityClass() {
        return StageLightBE.class;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<? extends StageLightBE> getBlockEntityType() {
        return ModBlockEntities.STAGE_LIGHT.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addStageLightTooltip(tooltip,
                "block.create_stats.stage_light");
    }
}
