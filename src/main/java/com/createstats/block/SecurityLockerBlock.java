package com.createstats.block;

import com.createstats.blockentity.SecurityLockerBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.security.LockerSeal;
import com.createstats.security.LockerSeals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 60s for every tool, hardness alone can't do it so getDestroyProgress
public class SecurityLockerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<SecurityLockerBlock> CODEC =
            simpleCodec(SecurityLockerBlock::new);

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;

    private static final double[] SHELL = {1.5, 1.0, 0.0, 14.5, 15.0, 8.5};

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, rotate(SHELL, facing));
        }
    }

    private static VoxelShape rotate(double[] b, Direction facing) {
        final double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public SecurityLockerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction clicked = context.getClickedFace();

        final Direction support = clicked.getAxis().isHorizontal()
                ? clicked.getOpposite() : context.getHorizontalDirection();
        final BlockState state = defaultBlockState().setValue(FACING, support);
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
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SecurityLockerBE locker)) {
            return;
        }
        if (placer instanceof Player player) {
            locker.claim(player);
        }
        final LockerSeal seal = stack.get(com.createstats.registry.ModDataComponents.LOCKER_SEAL.get());
        final LockerSeals seals = LockerSeals.of(level);
        if (seal != null && seals != null) {
            final CompoundTag payload = seals.redeem(seal.id());
            if (payload != null) {
                locker.loadSealPayload(payload, level.registryAccess());
            }
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!(level instanceof Level world)) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        final SecurityLockerBE locker = SecurityLockerBE.at(world, pos);

        if (locker == null || locker.isOpen() || locker.warm()) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        locker.noteMining(player);
        return 1.0f / 1200.0f;
    }

    private static boolean sealsOnBreak(SecurityLockerBE locker) {
        return !locker.isOpen() && !locker.warm()
                && (!locker.isEmptyInside() || locker.hasCode());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        final BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(be instanceof SecurityLockerBE locker) || !sealsOnBreak(locker)) {
            return super.getDrops(state, params);
        }
        final ServerLevel level = params.getLevel();
        final LockerSeals seals = LockerSeals.of(level);
        final ItemStack stack = new ItemStack(this);
        if (seals == null) {
            return List.of(stack);
        }
        final java.util.UUID id = seals.seal(locker.sealPayload(level.registryAccess()));
        stack.set(com.createstats.registry.ModDataComponents.LOCKER_SEAL.get(),
                new LockerSeal(id, locker.sealOwnerName()));
        return List.of(stack);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof SecurityLockerBE locker) {
            com.createstats.power.BoreLinks.severAll(level, locker);
            locker.invalidateNetwork();
            if (!sealsOnBreak(locker)) {
                com.createstats.upgrade.DeepItems.dropSplit(level, pos, locker.contents());
                Containers.dropContents(level, pos, locker.upgradeRack().contents());
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final SecurityLockerBE locker = SecurityLockerBE.at(level, pos);
        if (locker == null) {
            return InteractionResult.PASS;
        }

        if (locker.needsModeChoice(player)
                || (player.isShiftKeyDown() && locker.mayChooseMode(player))) {
            askMode(player, locker, pos);
            return InteractionResult.CONSUME;
        }

        return interact(level, pos, player, locker);
    }

    public static InteractionResult interact(Level level, BlockPos pos, Player player,
                                             SecurityLockerBE locker) {
        if (locker.mayOpen(player)) {
            if (locker.governed()) {
                locker.noteAccess(player, true);
            }
            final boolean wasOpen = locker.isOpen();
            locker.setOpen(true);
            if (!wasOpen) {
                thunk(level, pos, true);
            }
            openScreen(player, locker, pos);
            return InteractionResult.CONSUME;
        }

        if (locker.governed()) {
            if (locker.demandsCode()) {
                locker.noteRefusal(player, "code required, reader was lost");
            } else if (!locker.hasPower()) {
                locker.noteRefusal(player, "no power, reader dark");
            } else if (!locker.linkedHere()) {
                locker.noteRefusal(player, "no panel, reader dark");
            } else {
                locker.noteAccess(player, false);
            }
        }
        if (!locker.hasCode()) {
            if (locker.mayClaimCode() && player instanceof net.minecraft.server.level.ServerPlayer c) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(c,
                        com.createstats.network.OpenLockerKeypadPacket.claim(pos));
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(
                    Component.translatable("message.create_stats.security_locker.no_code"), true);
            deny(level, pos);
            return InteractionResult.CONSUME;
        }
        if (locker.keypadLocked()) {
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.security_locker.keypad_locked",
                    Math.max(1, locker.keypadLockoutRemaining() / 20)), true);
            deny(level, pos);
            return InteractionResult.CONSUME;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sender) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                    com.createstats.network.OpenLockerKeypadPacket.fresh(pos));
        }
        return InteractionResult.CONSUME;
    }

    private static void askMode(Player player, SecurityLockerBE locker, BlockPos pos) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sender) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sender,
                    new com.createstats.network.OpenLockerModePacket(pos, locker.mode().ordinal()));
        }
    }

    private static void openScreen(Player player, SecurityLockerBE locker, BlockPos pos) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sender) {
            sender.openMenu(locker, buf ->
                    com.createstats.menu.SecurityLockerMenu.writeOpenData(locker, buf));
        }
    }

    public static void thunk(Level level, BlockPos pos, boolean opening) {
        level.playSound(null, pos, com.createstats.registry.ModSounds.doorClip(
                        opening ? com.createstats.sound.DoorClip.LATCH
                                : com.createstats.sound.DoorClip.SEAL_HISS),
                SoundSource.BLOCKS, 0.5f, opening ? 1.15f : 0.95f);
    }

    public static void deny(Level level, BlockPos pos) {
        level.playSound(null, pos,
                com.createstats.registry.ModSounds.doorClip(com.createstats.sound.DoorClip.REFUSE),
                SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SECURITY_LOCKER.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SECURITY_LOCKER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((SecurityLockerBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats.security_locker",
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "tooltip.create_stats.security_locker_draw",
                        com.createstats.power.FeRates.perTick(SecurityLockerBE.drawPerTick()))));
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }
}
