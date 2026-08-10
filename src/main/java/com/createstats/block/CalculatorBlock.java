package com.createstats.block;

import com.createstats.blockentity.CalculatorBE;
import com.createstats.item.StatsConfiguratorItem;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.createstats.util.CalculatorLayout;
import com.createstats.util.CalculatorLayout.FaceTarget;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

public class CalculatorBlock extends HorizontalDirectionalBlock implements EntityBlock
{
    public static final DirectionProperty FACING;
    private static final VoxelShape SHAPE_NORTH;
    private static final VoxelShape SHAPE_SOUTH;
    private static final VoxelShape SHAPE_EAST;
    private static final VoxelShape SHAPE_WEST;
    
    public CalculatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((this.stateDefinition.any()).setValue(CalculatorBlock.FACING, Direction.NORTH));
    }
    
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(CalculatorBlock::new);
    }
    
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { CalculatorBlock.FACING });
    }
    
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(CalculatorBlock.FACING, context.getHorizontalDirection());
    }
    
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch ((Direction)state.getValue(CalculatorBlock.FACING)) {
            case SOUTH -> CalculatorBlock.SHAPE_SOUTH;
            case EAST -> CalculatorBlock.SHAPE_EAST;
            case WEST -> CalculatorBlock.SHAPE_WEST;
            default -> CalculatorBlock.SHAPE_NORTH;
        };
    }
    
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StatsConfiguratorItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (hit.getDirection() != ((Direction)state.getValue(CalculatorBlock.FACING)).getOpposite()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CalculatorBE calculator)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        final CalculatorBE controller = calculator.getControllerBE();
        final FaceTarget target = resolveFaceTarget(controller, pos, hit.getLocation(), (Direction)state.getValue(CalculatorBlock.FACING));
        if (target.isDisplay()) {
            if (!level.isClientSide()) {
                level.playSound(null, controller.getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.25f, 1.35f);
            }
            if (level.isClientSide()) {
                final CalculatorBE localCalculator = calculator;
                ClientScreenAccess.openCalculator(localCalculator);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!target.isButton()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            final boolean handled = controller.pressFaceButton(target.getToken());
            if (!handled) {
                player.displayClientMessage(
                    Component.translatable("message.create_stats.calculator.controller_unavailable").withStyle(ChatFormatting.RED),
                    true);
                return ItemInteractionResult.FAIL;
            }
            level.playSound(null, controller.getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 1.7f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    
    private static FaceTarget resolveFaceTarget(CalculatorBE controller, BlockPos clickedPos, Vec3 hitLocation, Direction facing) {
        final Direction right = facing.getClockWise();
        final BlockPos controllerPos = controller.getControllerPos();
        final int originH = dotAlongAxis(controllerPos, right);
        final int clickedH = dotAlongAxis(clickedPos, right);
        final double withinBlock = fractionAlongAxis(hitLocation, clickedPos, right);
        final double localX = clickedH - originH + withinBlock;
        final double localY = clickedPos.getY() - controllerPos.getY() + hitLocation.y - clickedPos.getY();
        final double u = Mth.clamp(localX / controller.getMultiblockWidth(), 0.0, 0.999999);
        final double vTop = Mth.clamp(1.0 - localY / controller.getMultiblockHeight(), 0.0, 0.999999);
        return CalculatorLayout.resolve(u, vTop);
    }
    
    private static double fractionAlongAxis(Vec3 hitLocation, BlockPos pos, Direction axis) {
        final double localX = hitLocation.x - pos.getX();
        final double localZ = hitLocation.z - pos.getZ();
        return switch (axis) {
            case EAST -> localX;
            case WEST -> 1.0 - localX;
            case SOUTH -> localZ;
            case NORTH -> 1.0 - localZ;
            default -> 0.0;
        };
    }
    
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.calculator");
    }
    
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CalculatorBE((BlockEntityType<?>)ModBlockEntities.CALCULATOR.get(), pos, state);
    }
    
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CalculatorBE calculator) {
                calculator.detectAndFormMultiblock();
            }
        }
    }
    
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player)) {
            return;
        }
        final Player player = (Player)placer;
        if (!player.isShiftKeyDown()) {
            return;
        }
        final Direction facing = (Direction)state.getValue(CalculatorBlock.FACING);
        final Direction right = facing.getClockWise();
        final Set<BlockPos> connected = new HashSet<BlockPos>();
        final Queue<BlockPos> toCheck = new LinkedList<BlockPos>();
        connected.add(pos);
        toCheck.add(pos);
        while (!toCheck.isEmpty()) {
            final BlockPos current = toCheck.poll();
            for (Direction dir : new Direction[] { right, right.getOpposite(), Direction.UP, Direction.DOWN }) {
                final BlockPos neighbor = current.relative(dir);
                if (connected.contains(neighbor)) {
                    continue;
                }
                final BlockEntity be = level.getBlockEntity(neighbor);
                if (!(be instanceof CalculatorBE)) {
                    continue;
                }
                final BlockState neighborState = be.getBlockState();
                if (!neighborState.hasProperty(CalculatorBlock.FACING) || neighborState.getValue(CalculatorBlock.FACING) != facing) {
                    continue;
                }
                connected.add(neighbor);
                toCheck.add(neighbor);
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
        for (BlockPos connectedPos : connected) {
            final int h = dotAlongAxis(connectedPos, right) - refH;
            final int v = connectedPos.getY() - refV;
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
            for (int h2 = minH; h2 <= maxH; ++h2) {
                final BlockPos candidate = ref.relative(right, h2).above(minV);
                if (!connected.contains(candidate) && level.getBlockState(candidate).canBeReplaced()) {
                    toFill.add(candidate);
                }
            }
        }
        else {
            for (int v2 = minV; v2 <= maxV; ++v2) {
                final BlockPos candidate2 = ref.relative(right, minH).above(v2);
                if (!connected.contains(candidate2) && level.getBlockState(candidate2).canBeReplaced()) {
                    toFill.add(candidate2);
                }
            }
        }
        if (toFill.isEmpty()) {
            return;
        }
        final ItemStack calculatorItem = new ItemStack((ItemLike)this);
        final boolean creative = player.getAbilities().instabuild;
        int available = creative ? toFill.size() : 0;
        if (!creative) {
            for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
                final ItemStack invStack = player.getInventory().getItem(i);
                if (invStack.getItem() == calculatorItem.getItem()) {
                    available += invStack.getCount();
                }
            }
        }
        int placed = 0;
        for (BlockPos fillPos : toFill) {
            if (placed >= available || !level.getBlockState(fillPos).canBeReplaced()) {
                break;
            }
            level.setBlock(fillPos, this.defaultBlockState().setValue(CalculatorBlock.FACING, facing), 3);
            if (!creative) {
                for (int j = 0; j < player.getInventory().getContainerSize(); ++j) {
                    final ItemStack invStack2 = player.getInventory().getItem(j);
                    if (invStack2.getItem() == calculatorItem.getItem()) {
                        invStack2.shrink(1);
                        if (invStack2.isEmpty()) {
                            player.getInventory().setItem(j, ItemStack.EMPTY);
                        }
                        break;
                    }
                }
            }
            ++placed;
        }
        if (placed > 0) {
            player.displayClientMessage(
                Component.translatable("message.create_stats.calculator.auto_filled", placed).withStyle(ChatFormatting.GREEN)
                    .append(Component.translatable("message.create_stats.calculator.auto_fill_hint").withStyle(ChatFormatting.GRAY)),
                true);
            final BlockEntity be2 = level.getBlockEntity(pos);
            if (be2 instanceof CalculatorBE calculator2) {
                calculator2.detectAndFormMultiblock();
            }
        }
    }
    
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CalculatorBE calculator) {
                calculator.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
    
    private static int dotAlongAxis(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getZ() * axis.getStepZ();
    }
    
    static {
        FACING = HorizontalDirectionalBlock.FACING;
        SHAPE_NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0);
        SHAPE_SOUTH = Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0);
        SHAPE_EAST = Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0);
        SHAPE_WEST = Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0);
    }
}
