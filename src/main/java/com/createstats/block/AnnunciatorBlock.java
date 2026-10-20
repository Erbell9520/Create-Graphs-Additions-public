package com.createstats.block;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.AnnunciatorVariant;
import com.createstats.antenna.AntennaAttachmentHelper;
import com.createstats.blockentity.AnnunciatorBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.createstats.util.CreateStatsTooltipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

// the head is just model geometry above the plinth, no second block. canSurvive keeps the space clear
public abstract class AnnunciatorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Glow> GLOW = EnumProperty.create("glow", Glow.class);

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    private static final float[] ACK_ZONE = {3.0f, 3.4f, 7.8f, 8.2f};
    private static final float[] RESET_ZONE = {7.8f, 4.0f, 10.4f, 7.2f};
    private static final float[] TOGGLE_ZONE = {10.5f, 3.6f, 13.0f, 7.6f};

    protected AnnunciatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(GLOW, Glow.OFF));
    }

    public abstract AnnunciatorVariant variant();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GLOW);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).canBeReplaced();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof AnnunciatorBE annunciator) {
            annunciator.setOwnerUUID(player.getUUID());
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AnnunciatorBE annunciator)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (this.variant().usesAntenna()) {
            final ItemInteractionResult antenna =
                    AntennaAttachmentHelper.handleUse(stack, level, player, hand, annunciator);
            if (antenna != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return antenna;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AnnunciatorBE annunciator)) {
            return InteractionResult.PASS;
        }
        if (hit.getDirection() == state.getValue(FACING)) {
            final float[] local = frontFaceHit(state, pos, hit);
            if (inZone(local, ACK_ZONE)) {
                if (!level.isClientSide()) {
                    annunciator.acknowledge();
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            if (inZone(local, RESET_ZONE)) {
                if (!level.isClientSide()) {
                    annunciator.reset();
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            if (inZone(local, TOGGLE_ZONE)) {
                if (!level.isClientSide()) {
                    annunciator.cycleToggle();
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        if (level.isClientSide()) {
            ClientScreenAccess.openAnnunciator(annunciator);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static float[] frontFaceHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        final Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        final double dx = local.x - 0.5;
        final double dz = local.z - 0.5;

        final double mx = switch (state.getValue(FACING)) {
            case NORTH -> dx;
            case EAST -> dz;
            case SOUTH -> -dx;
            case WEST -> -dz;
            default -> dx;
        };
        return new float[]{(float) ((0.5 - mx) * 16.0), (float) (local.y * 16.0)};
    }

    private static boolean inZone(float[] local, float[] zone) {
        return local[0] >= zone[0] && local[0] <= zone[2]
                && local[1] >= zone[1] && local[1] <= zone[3];
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof AnnunciatorBE annunciator
                ? annunciator.redstoneSignal() : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AnnunciatorBE annunciator
                ? annunciator.comparatorSignal() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        final boolean wasPowered = !level.isClientSide() && !state.is(newState.getBlock())
                && state.getValue(GLOW) != Glow.OFF;
        super.onRemove(state, level, pos, newState, isMoving);
        if (wasPowered) {
            level.updateNeighborsAt(pos, this);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnnunciatorBE(ModBlockEntities.ANNUNCIATOR.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.ANNUNCIATOR.get() || level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((AnnunciatorBE) be).tick();
    }

    public static int lightLevel(BlockState state) {
        return state.hasProperty(GLOW) ? state.getValue(GLOW).lightLevel() : 0;
    }

    public enum Glow implements StringRepresentable {
        OFF("off", 0),
        DIM("dim", 4),
        MID("mid", 8),
        HOT("hot", 12);

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

        public static Glow forSeverity(AlarmSeverity severity) {
            return switch (severity) {
                case NORMAL -> OFF;
                case NOTICE -> DIM;
                case WARNING -> MID;
                case CRITICAL -> HOT;
            };
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                this.variant().isWired()
                        ? "block.create_stats.klaxon_alarm"
                        : "block.create_stats.sentry_alarm");
    }
}
