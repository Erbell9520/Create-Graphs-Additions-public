package com.createstats.block;

import com.createstats.antenna.AntennaAttachmentHelper;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.item.StatsConfiguratorItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class HolographicProjectorBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    public static final DirectionProperty FACING;
    public static final BooleanProperty ACTIVE;
    private static final VoxelShape SHAPE;
    
    public HolographicProjectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(((this.stateDefinition.any()).setValue(HolographicProjectorBlock.FACING, Direction.NORTH)).setValue(HolographicProjectorBlock.ACTIVE, false));
    }
    
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(HolographicProjectorBlock::new);
    }
    
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { HolographicProjectorBlock.FACING, HolographicProjectorBlock.ACTIVE });
    }
    
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (this.defaultBlockState().setValue(HolographicProjectorBlock.FACING, context.getHorizontalDirection().getOpposite())).setValue(HolographicProjectorBlock.ACTIVE, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HolographicProjectorBE projector) {
                projector.setOwnerUUID(player.getUUID());
            }
        }
    }
    
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return HolographicProjectorBlock.SHAPE;
    }
    
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy((BlockGetter)level, below, Direction.UP);
    }
    
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN && !this.canSurvive(state, (LevelReader)level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
    
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StatsConfiguratorItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        final BlockEntity clickedBE = level.getBlockEntity(pos);
        if (clickedBE instanceof HolographicProjectorBE projector) {
            ItemInteractionResult antennaResult = AntennaAttachmentHelper.handleUse(stack, level, player, hand, projector);
            if (antennaResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return antennaResult;
            }
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                final BlockEntity be = clickedBE;
                if (be instanceof HolographicProjectorBE projector) {
                    projector.getHologramSettings().cycleDisplayMode();
                    projector.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    player.displayClientMessage(Component.translatable("message.create_stats.holographic.style", projector.getHologramSettings().getDisplayMode().getDisplayName()), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HolographicProjectorBE projector) {
                final String mode = projector.getHologramSettings().getDisplayMode().getDisplayName();
                final int channels = projector.getAllChannels().size();
                final boolean hasData = projector.hasData();
                final String status = hasData ? ("§b\u2726 Hologram §7| §fStyle: " + mode + " §7| §f" + channels + " channel(s)") : "§b\u2726 Hologram §7| §cNo data §7, connect a Display Link";
                player.displayClientMessage(Component.literal(status), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.holographic_projector");
    }
    
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HolographicProjectorBE((BlockEntityType<?>)ModBlockEntities.HOLOGRAPHIC_PROJECTOR.get(), pos, state);
    }
    
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type != ModBlockEntities.HOLOGRAPHIC_PROJECTOR.get()) return null;
        return (lvl, pos, st, be) -> ((HolographicProjectorBE) be).tick();
    }
    
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HolographicProjectorBE projector) {
                projector.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
    
    public boolean isSignalSource(BlockState state) {
        return true;
    }
    
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HolographicProjectorBE p) {
            return p.getRedstonePower();
        }
        return 0;
    }
    
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        // [1]
        return 0;
    }
    
    static {
        FACING = HorizontalDirectionalBlock.FACING;
        ACTIVE = BooleanProperty.create("active");
        SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);
    }
}
