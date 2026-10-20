package com.createstats.block;

import com.createstats.antenna.AntennaAttachmentHelper;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.item.FreightManifestClipboardItem;
import com.createstats.network.OpenDispatchBoardPacket;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightStationLink;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

public class DispatchBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape WALL_NORTH = Block.box(0, 0, 11, 16, 16, 16);
    private static final VoxelShape WALL_SOUTH = Block.box(0, 0, 0, 16, 16, 5);
    private static final VoxelShape WALL_EAST  = Block.box(0, 0, 0, 5, 16, 16);
    private static final VoxelShape WALL_WEST  = Block.box(11, 0, 0, 16, 16, 16);
    private final Supplier<BlockEntityType<DispatchBoardBE>> blockEntityType;

    public DispatchBoardBlock(BlockBehaviour.Properties properties) {
        this(properties, null);
    }

    public DispatchBoardBlock(BlockBehaviour.Properties properties, @Nullable Supplier<BlockEntityType<DispatchBoardBE>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(DispatchBoardBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();

        if (clickedFace.getAxis().isHorizontal()) {
            BlockState wallState = this.defaultBlockState().setValue(FACING, clickedFace);
            if (wallState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return wallState;
            }
        }

        Direction playerFacing = context.getHorizontalDirection().getOpposite();
        BlockState fallback = this.defaultBlockState().setValue(FACING, playerFacing);
        return fallback.canSurvive(context.getLevel(), context.getClickedPos()) ? fallback : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> WALL_NORTH;
            case SOUTH -> WALL_SOUTH;
            case EAST -> WALL_EAST;
            case WEST -> WALL_WEST;
            default -> WALL_NORTH;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos supportPos = pos.relative(state.getValue(FACING).getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, state.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        Direction supportDirection = state.getValue(FACING).getOpposite();
        if (direction == supportDirection && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DispatchBoardBE clickedBoard)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        DispatchBoardBE controller = clickedBoard.getControllerBE();
        ItemInteractionResult antennaResult = AntennaAttachmentHelper.handleUse(stack, level, player, hand, controller);
        if (antennaResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return antennaResult;
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && heldStack.getItem() instanceof FreightManifestClipboardItem) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return ItemInteractionResult.CONSUME;
            }
            FreightStationLink heldLink = FreightManifestClipboardItem.getStationLink(heldStack);
            FreightStationLink boardLink = controller.getStationLink();
            if (heldLink.isLinked()) {
                if (!canEdit(serverPlayer, heldLink)) {
                    serverPlayer.displayClientMessage(Component.translatable("message.create_stats.dispatch_board.no_relink_permission").withStyle(ChatFormatting.RED), false);
                    return ItemInteractionResult.CONSUME;
                }
                controller.setStationLink(heldLink);
                serverPlayer.displayClientMessage(Component.translatable("message.create_stats.dispatch_board.linked", heldLink.getStationName()).withStyle(ChatFormatting.GOLD), false);
            } else if (boardLink.isLinked()) {
                FreightManifestClipboardItem.setStationLink(heldStack, boardLink);
                serverPlayer.displayClientMessage(Component.translatable("message.create_stats.dispatch_board.link_copied").withStyle(ChatFormatting.GOLD), false);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.create_stats.dispatch_board.neither_linked").withStyle(ChatFormatting.RED), false);
            }
            return ItemInteractionResult.CONSUME;
        }

        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.CONSUME;
        }

        FreightStationLink link = controller.getStationLink();
        if (!link.isLinked()) {
            serverPlayer.displayClientMessage(Component.translatable("message.create_stats.dispatch_board.link_first").withStyle(ChatFormatting.RED), false);
            return ItemInteractionResult.CONSUME;
        }

        controller.refreshSummary();
        controller.refreshRosterForScreenIfStale();
        boolean canEdit = canEdit(serverPlayer, link);

        PacketDistributor.sendToPlayer(serverPlayer,
            new OpenDispatchBoardPacket(controller.getBlockPos(), link, controller.getManifest(),
                controller.getCargoView(), controller.getDeliveryBoard(), controller.getStationRoster(), canEdit));
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DispatchBoardBE board) {
            DispatchBoardBE controller = board.getControllerBE();
            FreightStationLink link = controller.getStationLink();
            if (link.isLinked()) {
                FreightManifestClipboardItem.setStationLink(stack, link);
            }
        }
        return stack;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DispatchBoardBE dispatchBoard) {
            FreightStationLink placedLink = FreightManifestClipboardItem.getStationLink(stack);
            if (placedLink.isLinked()) {
                dispatchBoard.setStationLink(placedLink);
            }
        }

        if (!(placer instanceof Player player)) return;
        if (player.isShiftKeyDown()) return;

        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        Set<BlockPos> connected = new HashSet<>();
        Queue<BlockPos> toCheck = new LinkedList<>();
        toCheck.add(pos);
        connected.add(pos);
        while (!toCheck.isEmpty()) {
            BlockPos cur = toCheck.poll();
            for (Direction dir : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
                BlockPos nb = cur.relative(dir);
                if (!connected.contains(nb)) {
                    BlockEntity nbe = level.getBlockEntity(nb);
                    if (nbe instanceof DispatchBoardBE) {
                        BlockState nState = nbe.getBlockState();
                        if (nState.hasProperty(FACING) && nState.getValue(FACING) == facing) {
                            connected.add(nb);
                            toCheck.add(nb);
                        }
                    }
                }
            }
        }
        if (connected.size() < 2) {
            if (be instanceof DispatchBoardBE db) db.detectAndFormMultiblock();
            return;
        }

        BlockPos ref = connected.iterator().next();
        int refH = dotAlongAxis(ref, right);
        int refV = ref.getY();
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (BlockPos p : connected) {
            int h = dotAlongAxis(p, right) - refH;
            int v = p.getY() - refV;
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        if (minH != maxH && minV != maxV) {
            if (be instanceof DispatchBoardBE db) db.detectAndFormMultiblock();
            return;
        }
        List<BlockPos> toFill = new ArrayList<>();
        if (minV == maxV) {
            for (int h = minH; h <= maxH; h++) {
                BlockPos candidate = ref.relative(right, h).above(minV);
                if (!connected.contains(candidate) && level.getBlockState(candidate).canBeReplaced()) {
                    toFill.add(candidate);
                }
            }
        } else {
            for (int v = minV; v <= maxV; v++) {
                BlockPos candidate = ref.relative(right, minH).above(v);
                if (!connected.contains(candidate) && level.getBlockState(candidate).canBeReplaced()) {
                    toFill.add(candidate);
                }
            }
        }
        if (!toFill.isEmpty()) {
            ItemStack panelItem = new ItemStack(this);
            boolean isCreative = player.getAbilities().instabuild;
            int available = isCreative ? toFill.size() : 0;
            if (!isCreative) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack invStack = player.getInventory().getItem(i);
                    if (invStack.getItem() == panelItem.getItem()) {
                        available += invStack.getCount();
                    }
                }
            }
            int placed = 0;
            for (BlockPos fillPos : toFill) {
                if (placed >= available) break;
                if (!level.getBlockState(fillPos).canBeReplaced()) continue;

                BlockState newState = this.defaultBlockState().setValue(FACING, facing);
                if (!newState.canSurvive(level, fillPos)) continue;
                level.setBlock(fillPos, newState, 3);
                if (!isCreative) {
                    for (int j = 0; j < player.getInventory().getContainerSize(); j++) {
                        ItemStack invStack = player.getInventory().getItem(j);
                        if (invStack.getItem() == panelItem.getItem()) {
                            invStack.shrink(1);
                            if (invStack.isEmpty()) player.getInventory().setItem(j, ItemStack.EMPTY);
                            break;
                        }
                    }
                }
                placed++;
            }
            if (placed > 0) {
                player.displayClientMessage(Component.translatable("message.create_stats.dispatch_board.auto_placed", placed), true);
            }
        }

        if (be instanceof DispatchBoardBE db) {
            db.detectAndFormMultiblock();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DispatchBoardBE db) {
                db.detectAndFormMultiblock();
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DispatchBoardBE db) {
                db.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (this.blockEntityType == null) {
            throw new IllegalStateException("Dispatch board block entity type has not been wired yet.");
        }
        return new DispatchBoardBE(this.blockEntityType.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((DispatchBoardBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.dispatch_board");
    }

    private static boolean canEdit(ServerPlayer player, FreightStationLink link) {
        if (!link.isLinked() || link.getStationId() == null) {
            return player.hasPermissions(2);
        }
        ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(player.server, link);
        if (stationLevel == null) {
            return player.hasPermissions(2);
        }
        FreightManifest manifest = FreightManifestSavedData.get(stationLevel).getManifest(link.getStationId());
        return manifest == null || manifest.getOwnerId() == null || manifest.getOwnerId().equals(player.getUUID()) || player.hasPermissions(2);
    }

    private static int dotAlongAxis(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getZ() * axis.getStepZ();
    }
}
