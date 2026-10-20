package com.createstats.block;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.createstats.antenna.AntennaAttachmentHelper;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.item.FreightManifestClipboardItem;
import com.createstats.network.OpenFreightManifestClipboardPacket;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.registry.ModBlockEntities;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

public class FreightManifestClipboardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<MountType> MOUNT = EnumProperty.create("mount", MountType.class);

    private static final double[][] STANDING_BOXES = {
            {3.0, 0.0, 3.0, 13.0, 2.0, 13.0},
            {6.0, 2.0, 8.0, 10.0, 4.0, 11.0},
            {2.0, 4.0, 6.0, 14.0, 5.0, 11.0},
            {3.0, 4.6, 5.7, 13.0, 8.0, 9.8},
            {3.0, 8.0, 8.0, 13.0, 11.6, 11.4},
            {3.0, 11.6, 9.4, 13.0, 15.3, 12.8},
    };

    private static final double[][] WALL_BOXES = {
            {3.0, 3.0, 13.5, 13.0, 13.0, 16.0},
            {6.0, 13.0, 14.0, 10.0, 14.0, 16.0},
            {3.0, 3.0, 12.5, 13.0, 4.0, 14.5},
            {4.0, 11.0, 12.5, 12.0, 13.0, 14.5},
    };

    private static final Map<Direction, VoxelShape> STANDING_SHAPES = buildShapes(STANDING_BOXES);
    private static final Map<Direction, VoxelShape> WALL_SHAPES = buildShapes(WALL_BOXES);

    public FreightManifestClipboardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, MountType.STANDING));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(FreightManifestClipboardBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState wallState = this.defaultBlockState().setValue(MOUNT, MountType.WALL);
        if (clickedFace.getAxis().isHorizontal()) {
            wallState = wallState.setValue(FACING, clickedFace);
            if (wallState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return wallState;
            }
        }

        BlockState standingState = this.defaultBlockState()
            .setValue(MOUNT, MountType.STANDING)
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
        return standingState.canSurvive(context.getLevel(), context.getClickedPos())
            ? standingState
            : null;
    }

    private static Map<Direction, VoxelShape> buildShapes(double[][] boxes) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, rotateBox(b, facing));
            }
            map.put(facing, shape);
        }
        return map;
    }

    private static VoxelShape rotateBox(double[] b, Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
            default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes =
                state.getValue(MOUNT) == MountType.WALL ? WALL_SHAPES : STANDING_SHAPES;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction supportFace = state.getValue(MOUNT) == MountType.WALL ? state.getValue(FACING) : Direction.UP;
        BlockPos supportPos = state.getValue(MOUNT) == MountType.WALL ? pos.relative(state.getValue(FACING).getOpposite()) : pos.below();
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, supportFace);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        Direction supportDirection = state.getValue(MOUNT) == MountType.WALL ? state.getValue(FACING).getOpposite() : Direction.DOWN;
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
        if (!(be instanceof FreightManifestClipboardBE clipboard)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemInteractionResult antennaResult = AntennaAttachmentHelper.handleUse(stack, level, player, hand, clipboard);
        if (antennaResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return antennaResult;
        }
        ItemStack heldStack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && heldStack.getItem() instanceof FreightManifestClipboardItem) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            FreightStationLink heldLink = FreightManifestClipboardItem.getStationLink(heldStack);
            FreightStationLink clipboardLink = clipboard.getStationLink();
            if (heldLink.isLinked()) {
                clipboard.setStationLink(heldLink);
                player.displayClientMessage(Component.translatable("message.create_stats.clipboard_block.relinked", heldLink.getStationName()).withStyle(ChatFormatting.GOLD), false);
            } else if (clipboardLink.isLinked()) {
                FreightManifestClipboardItem.setStationLink(heldStack, clipboardLink);
                player.displayClientMessage(Component.translatable("message.create_stats.clipboard_block.link_copied").withStyle(ChatFormatting.GOLD), false);
            } else {
                player.displayClientMessage(Component.translatable("message.create_stats.clipboard_block.neither_linked").withStyle(ChatFormatting.RED), false);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        FreightStationLink link = clipboard.getStationLink();
        if (!link.isLinked() || link.getStationDimension() == null || link.getStationId() == null) {
            player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.not_linked").withStyle(ChatFormatting.RED), false);
            return ItemInteractionResult.CONSUME;
        }

        ResourceKey<Level> stationDimension = ResourceKey.create(Registries.DIMENSION, link.getStationDimension());
        ServerLevel stationLevel = level.getServer() == null ? null : level.getServer().getLevel(stationDimension);
        if (stationLevel == null) {
            player.displayClientMessage(Component.translatable("message.create_stats.clipboard_block.dimension_unavailable").withStyle(ChatFormatting.RED), false);
            return ItemInteractionResult.CONSUME;
        }

        FreightManifestStationResolver.StationView view = FreightManifestStationResolver.captureStationView(stationLevel, link);
        FreightManifest manifest = view.manifest();
        FreightStationCargoView cargoView = view.cargoView();
        DeliveryOrderBoardView deliveryBoard = view.deliveryBoard();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.CONSUME;
        }
        boolean canEdit = manifest.getOwnerId() == null || manifest.getOwnerId().equals(serverPlayer.getUUID()) || serverPlayer.hasPermissions(2);
        PacketDistributor.sendToPlayer(serverPlayer,
            new OpenFreightManifestClipboardPacket(pos, link, manifest, cargoView, deliveryBoard, canEdit));
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FreightManifestClipboardBE clipboard) {
            FreightStationLink link = clipboard.getStationLink();
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
        if (be instanceof FreightManifestClipboardBE clipboard) {
            clipboard.setStationLink(FreightManifestClipboardItem.getStationLink(stack));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FreightManifestClipboardBE(ModBlockEntities.FREIGHT_MANIFEST_CLIPBOARD.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FREIGHT_MANIFEST_CLIPBOARD.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((FreightManifestClipboardBE) be).serverTick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.freight_manifest_clipboard");
    }

    public enum MountType implements StringRepresentable {
        STANDING("standing"),
        WALL("wall");

        private final String serializedName;

        MountType(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }
}
