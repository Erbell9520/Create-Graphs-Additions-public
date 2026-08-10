package com.createstats.block;

import com.createstats.antenna.AntennaAttachmentHelper;
import com.createstats.display.StatsDisplaySettings;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import net.minecraft.world.level.ItemLike;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.createstats.util.ClientScreenAccess;
import net.minecraft.network.chat.Component;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.item.StatsConfiguratorItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public class StatsDisplayPanelBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    public static final DirectionProperty FACING;
    private static final VoxelShape SHAPE_NORTH;
    private static final VoxelShape SHAPE_SOUTH;
    private static final VoxelShape SHAPE_EAST;
    private static final VoxelShape SHAPE_WEST;
    
    public StatsDisplayPanelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((this.stateDefinition.any()).setValue(StatsDisplayPanelBlock.FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(StatsDisplayPanelBlock::new);
    }
    
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { StatsDisplayPanelBlock.FACING });
    }
    
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(StatsDisplayPanelBlock.FACING, context.getHorizontalDirection());
    }
    
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch ((Direction)state.getValue(StatsDisplayPanelBlock.FACING)) {
            case SOUTH -> StatsDisplayPanelBlock.SHAPE_SOUTH;
            case EAST -> StatsDisplayPanelBlock.SHAPE_EAST;
            case WEST -> StatsDisplayPanelBlock.SHAPE_WEST;
            default -> StatsDisplayPanelBlock.SHAPE_NORTH;
        };
    }
    
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StatsConfiguratorItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        final BlockEntity clickedBE = level.getBlockEntity(pos);
        final StatsDisplayPanelBE antennaTarget = clickedBE instanceof StatsDisplayPanelBE clickedPanel
                ? (clickedPanel.isController() ? clickedPanel : this.getController(level, clickedPanel))
                : null;
        if (antennaTarget != null) {
            ItemInteractionResult antennaResult = AntennaAttachmentHelper.handleUse(stack, level, player, hand, antennaTarget);
            if (antennaResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return antennaResult;
            }
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                final BlockEntity be = clickedBE;
                if (be instanceof StatsDisplayPanelBE panel) {
                    final StatsDisplayPanelBE controller = panel.isController() ? panel : this.getController(level, panel);
                    if (controller != null) {
                        final StatsDisplaySettings settings = controller.getSettings();
                        final StatsDisplaySettings.GraphType nextGraphType = StatsDisplaySettings.nextGraphType(settings.getGraphType(), controller.hasTrainConnection());
                        settings.setGraphType(nextGraphType);
                        settings.setDisplayMode(StatsDisplaySettings.normalizeDisplayMode(settings.getDisplayMode(), nextGraphType, controller.getMultiblockWidth(), controller.getMultiblockHeight()));
                        controller.setSettings(settings);
                        level.sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
                        player.displayClientMessage(Component.translatable("message.create_stats.stats_panel.view", controller.getSettings().getGraphType().getDisplayName()), true);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StatsDisplayPanelBE panel) {
                final StatsDisplayPanelBE controller = panel.isController() ? panel : this.getController(level, panel);
                if (controller != null) {
                    controller.markTrainMapActive();
                }
            }
        }
        if (level.isClientSide()) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StatsDisplayPanelBE p) {
                this.openConfigScreen(p);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** [1] */
    protected void openConfigScreen(StatsDisplayPanelBE panel) {
        ClientScreenAccess.openStatsDisplay(panel);
    }
    
    @Nullable
    private StatsDisplayPanelBE getController(Level level, StatsDisplayPanelBE member) {
        final BlockPos controllerPos = member.getControllerPos();
        if (controllerPos != null) {
            final BlockEntity be = level.getBlockEntity(controllerPos);
            if (be instanceof StatsDisplayPanelBE controller) {
                if (controller.isController()) {
                    return controller;
                }
            }
        }
        return null;
    }
    
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.stats_display_panel");
    }
    
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatsDisplayPanelBE((BlockEntityType<?>)ModBlockEntities.STATS_DISPLAY_PANEL.get(), pos, state);
    }
    
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type != ModBlockEntities.STATS_DISPLAY_PANEL.get()) return null;
        return (lvl, pos, st, be) -> ((StatsDisplayPanelBE) be).tick();
    }
    
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsDisplayPanelBE panel) {
            panel.refreshPlacementMultiblock();
        }
    }
    
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player)) {
            return;
        }
        final Player player = (Player)placer;
        final BlockEntity placedBE = level.getBlockEntity(pos);
        if (placedBE instanceof StatsDisplayPanelBE panel) {
            panel.setMultiblockMergeDisabled(player.isShiftKeyDown());
        }
        if (player.isShiftKeyDown()) {
            return;
        }
        final Direction facing = (Direction)state.getValue(StatsDisplayPanelBlock.FACING);
        final Direction right = facing.getClockWise();
        final Set<BlockPos> connected = new HashSet<BlockPos>();
        final Queue<BlockPos> toCheck = new LinkedList<BlockPos>();
        toCheck.add(pos);
        connected.add(pos);
        while (!toCheck.isEmpty()) {
            final BlockPos cur = toCheck.poll();
            for (Direction dir : new Direction[] { right, right.getOpposite(), Direction.UP, Direction.DOWN }) {
                final BlockPos nb = cur.relative(dir);
                if (!connected.contains(nb)) {
                    final BlockEntity nbe = level.getBlockEntity(nb);
                    if (nbe instanceof StatsDisplayPanelBE) {
                        final BlockState nState = nbe.getBlockState();
                        if (nState.hasProperty(StatsDisplayPanelBlock.FACING) && nState.getValue(StatsDisplayPanelBlock.FACING) == facing && nState.getBlock() == state.getBlock()) {
                            connected.add(nb);
                            toCheck.add(nb);
                        }
                    }
                }
            }
        }
        if (connected.size() < 2) {
            return;
        }
        final BlockPos ref = connected.iterator().next();
        final int refH = dotAlongAxis(ref, right);
        final int refV = ref.getY();
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        for (BlockPos p : connected) {
            final int h = dotAlongAxis(p, right) - refH;
            final int v = p.getY() - refV;
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        if (minH != maxH && minV != maxV) {
            return;
        }
        final List<BlockPos> toFill = new ArrayList<BlockPos>();
        if (minV == maxV) {
            for (int h = minH; h <= maxH; ++h) {
                final BlockPos candidate = ref.relative(right, h).above(minV);
                if (!connected.contains(candidate) && level.getBlockState(candidate).canBeReplaced()) {
                    toFill.add(candidate);
                }
            }
        }
        else {
            for (int v = minV; v <= maxV; ++v) {
                final BlockPos candidate2 = ref.relative(right, minH).above(v);
                if (!connected.contains(candidate2) && level.getBlockState(candidate2).canBeReplaced()) {
                    toFill.add(candidate2);
                }
            }
        }
        if (toFill.isEmpty()) {
            return;
        }
        final ItemStack panelItem = new ItemStack((ItemLike)this);
        final boolean isCreative = player.getAbilities().instabuild;
        int available = isCreative ? toFill.size() : 0;
        if (!isCreative) {
            for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
                final ItemStack invStack = player.getInventory().getItem(i);
                if (invStack.getItem() == panelItem.getItem()) {
                    available += invStack.getCount();
                }
            }
        }
        int placed = 0;
        for (BlockPos fillPos : toFill) {
            if (placed >= available) {
                break;
            }
            if (!level.getBlockState(fillPos).canBeReplaced()) {
                continue;
            }
            final BlockState newState = this.defaultBlockState().setValue(StatsDisplayPanelBlock.FACING, facing);
            level.setBlock(fillPos, newState, 3);
            if (!isCreative) {
                int j = 0;
                while (j < player.getInventory().getContainerSize()) {
                    final ItemStack invStack2 = player.getInventory().getItem(j);
                    if (invStack2.getItem() == panelItem.getItem()) {
                        invStack2.shrink(1);
                        if (invStack2.isEmpty()) {
                            player.getInventory().setItem(j, ItemStack.EMPTY);
                            break;
                        }
                        break;
                    }
                    else {
                        ++j;
                    }
                }
            }
            ++placed;
        }
        if (placed > 0) {
            player.displayClientMessage(Component.literal("§a+" + placed + " panels auto-placed §7(shift to disable)"), true);
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StatsDisplayPanelBE panel) {
                panel.detectAndFormMultiblock();
            }
        }
    }
    
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StatsDisplayPanelBE panel) {
                panel.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
    
    public boolean isSignalSource(BlockState state) {
        return true;
    }
    
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsDisplayPanelBE panel) {
            return panel.getRedstonePower();
        }
        return 0;
    }
    
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsDisplayPanelBE panel) {
            return panel.getRedstonePower();
        }
        return 0;
    }
    
    static {
        FACING = HorizontalDirectionalBlock.FACING;
        SHAPE_NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0);
        SHAPE_SOUTH = Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0);
        SHAPE_EAST = Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0);
        SHAPE_WEST = Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0);
    }

    private static int dotAlongAxis(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getZ() * axis.getStepZ();
    }
}
