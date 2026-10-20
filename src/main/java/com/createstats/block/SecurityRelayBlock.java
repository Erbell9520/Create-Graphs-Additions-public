package com.createstats.block;

import com.createstats.blockentity.SecurityRelayBE;
import com.createstats.network.SecurityRelaySyncPacket;
import com.createstats.power.FeRates;
import com.createstats.registry.ModBlockEntities;
import com.createstats.security.relay.RelayFace;
import com.createstats.security.relay.RelayPower;
import com.createstats.security.relay.RelayRules;
import com.createstats.util.CreateStatsTooltipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// output is the back face, like an observer. weak source, 0 or 15
public class SecurityRelayBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape NORTH = Block.box(1, 0, 1, 15, 13, 15);

    public SecurityRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof SecurityRelayBE relay) {
            relay.setPlacer(player);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return NORTH;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (direction != state.getValue(FACING)) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof SecurityRelayBE relay ? relay.signalTowards() : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos,
                                      @Nullable Direction direction) {
        return direction == state.getValue(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SecurityRelayBE relay)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (onButton(state, pos, hit)) {
            SecurityRelayBE.tell(sp, relay.pressReset(sp));
            return InteractionResult.SUCCESS;
        }
        if (relay.admits(sp)) {
            PacketDistributor.sendToPlayer(sp, SecurityRelaySyncPacket.open(relay, sp));
        } else {
            relay.askPassword(sp);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean onButton(BlockState state, BlockPos pos, BlockHitResult hit) {
        final Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) {
            return false;
        }
        final Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        final float u = RelayFace.u(facing.getStepX(), facing.getStepZ(), local.x, local.z);
        final float v = RelayFace.v(local.y);
        return RelayFace.onButton(u, v);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityRelayBE(ModBlockEntities.SECURITY_RELAY.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SECURITY_RELAY.get()) {
            return null;
        }
        return (lvl, p, st, be) -> ((SecurityRelayBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.security_relay",
                List.of(CreateStatsTooltipHelper.spec("tooltip.create_stats.security_relay_draw",
                        FeRates.perTick(RelayPower.STANDBY), FeRates.perTick(RelayPower.RATED))));
    }

    public static Component verdict(RelayRules.ResetVerdict verdict) {
        return Component.translatable("message.create_stats.security_relay.reset."
                + verdict.name().toLowerCase(java.util.Locale.ROOT));
    }
}
